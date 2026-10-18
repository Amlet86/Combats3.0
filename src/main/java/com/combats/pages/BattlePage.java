package com.combats.pages;

import java.time.Duration;
import java.time.Instant;

import com.codeborne.selenide.ElementsCollection;
import com.codeborne.selenide.SelenideElement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.codeborne.selenide.Condition.visible;
import static com.codeborne.selenide.Selenide.*;
import static com.combats.utils.GameControl.checkStop;
import static com.combats.utils.GameControl.status;
import static com.combats.utils.Properties.*;
import static com.combats.utils.Utils.getRandomInt;
import static com.combats.utils.Utils.waitAboutSomeSeconds;

/**
 * Экран боя: приёмы, зоны удара и блока, кнопки «Вперёд!!!» и «Вернуться».
 */
public class BattlePage extends BasePage {

    private static final Logger log = LoggerFactory.getLogger(BattlePage.class);

    private static final int MAX_SECONDS_WITHOUT_BATTLE_BUTTONS = 60;
    private static final Duration BATTLE_START_TIMEOUT = Duration.ofSeconds(60);
    /** Зоны: голова, грудь, живот, пояс, ноги. */
    private static final int ZONES = 5;
    /** Сообщение после смерти персонажа, пока остальные ещё дерутся. */
    private static final String DEAD_MESSAGE = "Для вас бой окончен";
    private static final Duration EXIT_CONFIRM_TIMEOUT = Duration.ofSeconds(10);
    /** Сколько ждать окна подтверждения после приёма: обычно его нет, поэтому недолго. */
    private static final Duration METHOD_CONFIRM_TIMEOUT = Duration.ofSeconds(2);
    private static final long CONFIRM_CHECK_INTERVAL_MS = 300;
    private static final Duration LEAVE_BATTLE_TIMEOUT = Duration.ofMinutes(3);
    private static final int MAX_CHOOSE_ATTEMPTS = 3;
    private static final String ATTACK_RADIOS = ".UserBattleAttack button.UserBattleRadio";
    private static final String DEFEND_RADIOS = ".UserBattleDefend button.UserBattleRadio";
    /** Сколько проверок подряд не должно быть ни «Обновить», ни «Вернуться», чтобы считать, что мы вышли. */
    private static final int CHECKS_WITHOUT_BUTTONS_TO_LEAVE = 2;
    /**
     * Действие с питомцем: {@code pet_unleash} — «Выпустить зверя» (зверь даёт бонус и получает опыт),
     * {@code pet_train} — «Обучить зверя».
     */
    static final String PET_ACTION = "pet_unleash";
    /** Доступная кнопка действия с питомцем. */
    static final String PET_METHOD = ".UserBattleResources button.UserBattleMethod:has(img[src*='/" + PET_ACTION + ".'])";
    /** Доступный приём персонажа, не питомца. */
    static final String OWN_METHOD = ".UserBattleResources button.UserBattleMethod:not(:has(img[src*='/pet_']))";

    private SelenideElement commitBtn = $("[action=commit]");
    private SelenideElement refreshBtn = $("[action=refresh]");
    private SelenideElement returnBtn = $("[action=gameover]");
    private SelenideElement exitBtn = $("[action=exit]");
    /**
     * «ОК» в окне подтверждения внутри страницы: «Выход из боя» и такое же окно, которое иногда
     * появляется после приёма. Закрытое окно остаётся в DOM скрытым, поэтому нужна видимая кнопка.
     */
    private ElementsCollection confirmOkBtns = $$("table.WindowNeitral button.WindowOk");
    private ElementsCollection attackRadios = $$(ATTACK_RADIOS);
    private ElementsCollection defendRadios = $$(DEFEND_RADIOS);
    private SelenideElement text = $("td.UserBattleError");

    public BattlePage() {
    }

    /**
     * Проводит бой до конца: каждый ход выбирает приём, удар и блок. После боя сообщает итог
     * и нажимает «Вернуться».
     */
    public void fight() {
        switchTo().defaultContent();
        // при начале боя игра перезагружает всю страницу, это дольше общего таймаута
        commitBtn.shouldBe(visible, BATTLE_START_TIMEOUT);
        status("In battle");
        // когда бой закончен, игра оставляет на экране только кнопку «Вернуться»
        int secondsWithoutBattleButtons = 0;
        while (!returnBtn.isDisplayed()) {
            // время окончания игры бой не прерывает, а «Stop» прерывает: это явное решение пользователя
            checkStop();
            if (commitBtn.isDisplayed())
                makeMove();
            if (exitAfterDeath())
                break;
            if (commitBtn.isDisplayed() || refreshBtn.isDisplayed())
                secondsWithoutBattleButtons = 0;
            else if (++secondsWithoutBattleButtons > MAX_SECONDS_WITHOUT_BATTLE_BUTTONS) {
                log.warn("The battle screen has disappeared, stop waiting for the end of the battle");
                break;
            }
            // ход делаем кликом по «Вперёд», Enter нужен только чтобы подтолкнуть обновление, пока ждём противника
            if (!commitBtn.isDisplayed())
                body.pressEnter();
            waitAboutSomeSeconds(commitBtn.isDisplayed() ? 1 : 3);
        }
        getMessage();
        leaveBattle();
    }

    /**
     * Если персонаж погиб, а остальные ещё дерутся, нажимает «Выйти», чтобы не ждать конца боя
     * (опыт за бой при этом сохраняется). Игра спрашивает «Уверены, что хотите выйти из боя?»
     * в окне внутри страницы, его подтверждаем.
     *
     * @return true, если выход подтверждён и дальше надо выходить из боя через {@link #leaveBattle()}
     */
    private boolean exitAfterDeath() {
        if (!exitBtn.isDisplayed() || !text.getText().contains(DEAD_MESSAGE))
            return false;
        log.info("The character is dead, leave the battle without waiting for the others");
        status("Dead, leaving the battle");
        humanClick(exitBtn);
        if (confirmWindowIfShown(EXIT_CONFIRM_TIMEOUT))
            return true;
        // окна не было: если «Выйти» ещё видна, на следующем шаге цикла попробуем снова
        log.warn("The exit confirmation window hasn't appeared");
        return false;
    }

    /**
     * Если на странице появилось окно подтверждения, нажимает в нём «ОК». Ждёт окно не дольше
     * timeout и не бросает исключение, если его нет: проверка через shouldBe при неудаче
     * сохраняла бы скриншот в fails/.
     *
     * @return true, если окно было и «ОК» нажата
     */
    private boolean confirmWindowIfShown(Duration timeout) {
        Instant deadline = Instant.now().plus(timeout);
        while (true) {
            SelenideElement ok = confirmOkBtns.findBy(visible);
            if (ok.isDisplayed()) {
                humanClick(ok);
                return true;
            }
            if (Instant.now().isAfter(deadline))
                return false;
            sleep(CONFIRM_CHECK_INTERVAL_MS);
        }
    }

    /**
     * Выходит с экрана боя. После «Выйти» игра не сразу отпускает персонажа: какое-то время
     * видна только «Обновить», потом «Вернуться», и иногда после «Вернуться» снова «Обновить».
     * Поэтому нажимаем ту из них, что видна, пока обе не пропадут.
     */
    private void leaveBattle() {
        Instant deadline = Instant.now().plus(LEAVE_BATTLE_TIMEOUT);
        int checksWithoutButtons = 0;
        while (checksWithoutButtons < CHECKS_WITHOUT_BUTTONS_TO_LEAVE) {
            checkStop();
            if (Instant.now().isAfter(deadline)) {
                log.warn("Haven't left the battle in {} minutes", LEAVE_BATTLE_TIMEOUT.toMinutes());
                return;
            }
            if (returnBtn.isDisplayed()) {
                checksWithoutButtons = 0;
                humanClick(returnBtn);
            } else if (refreshBtn.isDisplayed()) {
                checksWithoutButtons = 0;
                humanClick(refreshBtn);
            } else
                checksWithoutButtons++;
            waitAboutSomeSeconds(2);
        }
        log.info("Left the battle");
    }

    /**
     * Если включён питомец, сначала применяет действие с ним, затем первый доступный обычный приём.
     * Приёмы питомца без галочки «Pet» не трогаем. Некоторые приёмы игра просит подтвердить
     * в окне, как при выходе из боя: такое окно подтверждаем.
     */
    private void clickBattleMethods() {
        if (getPet())
            usePet();
        SelenideElement method = $(OWN_METHOD);
        if (method.isDisplayed()) {
            method.click();
            if (confirmWindowIfShown(METHOD_CONFIRM_TIMEOUT))
                log.info("The method was confirmed in the window");
        }
        waitAboutSomeSeconds(1);
    }

    /**
     * Действие с питомцем: кнопка с иконкой {@link #PET_ACTION} рядом с восемью слотами приёмов.
     * Окна подтверждения нет. После клика обе кнопки питомца («Выпустить» и «Обучить») становятся
     * недоступными до конца боя, поэтому за бой оно срабатывает один раз (снято 14.10.2026).
     */
    private void usePet() {
        SelenideElement petBtn = $(PET_METHOD);
        if (!petBtn.isDisplayed())
            return;
        humanClick(petBtn);
        confirmWindowIfShown(METHOD_CONFIRM_TIMEOUT);
        log.info("Pet action «{}» is used", PET_ACTION);
        waitAboutSomeSeconds(1);
    }

    /**
     * Один ход: приём, удары и блок, затем клик по «Вперёд». Приём может перерисовать зоны
     * и сбросить выбор, поэтому выбор проверяем и при необходимости повторяем. Что «Вперёд» пропала
     * после клика, не проверяем: в бою с несколькими противниками игра сразу даёт ход против
     * следующего, и кнопка остаётся. Если она видна, цикл боя просто сделает следующий ход.
     */
    private void makeMove() {
        clickBattleMethods();
        // выбор с прошлого хода может остаться, поэтому первый раз выбираем всегда
        for (int attempt = 1; attempt <= MAX_CHOOSE_ATTEMPTS; attempt++) {
            clickAttack();
            clickDefend();
            if (zonesChosen())
                break;
            log.warn("Zones aren't chosen after attempt {}", attempt);
        }
        if (!zonesChosen())
            log.warn("Zones still aren't chosen, press «Вперёд» anyway");
        if (!commitBtn.isDisplayed())
            return;
        commitBtn.click();
    }

    /**
     * Выбраны ли все удары и блок. У выбранной кнопки картинка внутри сдвинута: {@code img.Absolute}
     * со {@code style.left = -18px}.
     */
    private boolean zonesChosen() {
        int strikes = attackRadios.size() / ZONES;
        return strikes > 0
            && countChosen(ATTACK_RADIOS) == strikes
            && countChosen(DEFEND_RADIOS) == 1;
    }

    private static long countChosen(String radiosSelector) {
        Long chosen = executeJavaScript(
            "return [...document.querySelectorAll(arguments[0])]"
                + ".filter(b => { const i = b.querySelector('img.Absolute'); return i && i.style.left === '-18px'; })"
                + ".length;", radiosSelector);
        return chosen == null ? 0 : chosen;
    }

    /**
     * Для каждого удара выбирает случайную зону из пяти. Удары могут попасть в одну зону.
     */
    private void clickAttack() {
        if (!attackRadios.get(1).isDisplayed())
            return;
        int strikes = attackRadios.size() / ZONES;
        for (int strike = 0; strike < strikes; strike++)
            attackRadios.get(attackRadioIndex(getRandomInt(0, ZONES), strike, strikes)).click();
    }

    /**
     * Кнопки удара идут по строкам-зонам, в строке по кнопке на каждый удар:
     * [голова: удар 1, удар 2], [грудь: удар 1, удар 2], …
     */
    static int attackRadioIndex(int zone, int strike, int strikes) {
        return zone * strikes + strike;
    }

    /**
     * Выбирает случайный блок из пяти (каждый закрывает две соседние зоны).
     */
    private void clickDefend() {
        if (defendRadios.get(1).isDisplayed())
            defendRadios.get(getRandomInt(0, defendRadios.size())).click();
    }

    /**
     * Показывает в окне статуса сообщение с экрана боя. В уведомления его не отправляем: после
     * смерти там «Для вас бой окончен…», а не итог. Итог бой пишет в чат, его отправляет
     * {@link ChatPage#reportNewBattleResults()}.
     */
    private void getMessage() {
        if (text.isDisplayed())
            status("Battle is over: " + text.getText());
    }

}
