package com.combats.pages;

import java.time.Duration;
import java.time.Instant;

import com.codeborne.selenide.ElementsCollection;
import com.codeborne.selenide.SelenideElement;
import com.codeborne.selenide.ex.UIAssertionError;
import com.combats.utils.Notifications;
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
    private static final Duration LEAVE_BATTLE_TIMEOUT = Duration.ofMinutes(3);
    /** Сколько проверок подряд не должно быть ни «Обновить», ни «Вернуться», чтобы считать, что мы вышли. */
    private static final int CHECKS_WITHOUT_BUTTONS_TO_LEAVE = 2;

    private SelenideElement commitBtn = $("[action=commit]");
    private SelenideElement refreshBtn = $("[action=refresh]");
    private SelenideElement returnBtn = $("[action=gameover]");
    private SelenideElement exitBtn = $("[action=exit]");
    /** «ОК» в окне «Выход из боя». */
    private ElementsCollection exitConfirmOkBtns = $$("table.WindowNeitral button.WindowOk");
    private ElementsCollection anyBattleMethods = $$(".UserBattleResources button");
    private ElementsCollection activeBattleMethods = $$(".UserBattleMethod");
    private ElementsCollection attackRadios = $$(".UserBattleAttack button.UserBattleRadio");
    private ElementsCollection defendRadios = $$(".UserBattleDefend button.UserBattleRadio");
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
            if (commitBtn.isDisplayed()) {
                clickBattleMethods();
                clickAttack();
                clickDefend();
            }
            if (exitAfterDeath())
                break;
            if (commitBtn.isDisplayed() || refreshBtn.isDisplayed())
                secondsWithoutBattleButtons = 0;
            else if (++secondsWithoutBattleButtons > MAX_SECONDS_WITHOUT_BATTLE_BUTTONS) {
                log.warn("The battle screen has disappeared, stop waiting for the end of the battle");
                break;
            }
            body.pressEnter();
            waitAboutSomeSeconds(4);
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
        try {
            // после закрытия окно остаётся в DOM скрытым, поэтому ищем видимую кнопку
            SelenideElement ok = exitConfirmOkBtns.findBy(visible).shouldBe(visible, EXIT_CONFIRM_TIMEOUT);
            humanClick(ok);
            return true;
        } catch (UIAssertionError e) {
            // окна не было: если «Выйти» ещё видна, на следующем шаге цикла попробуем снова
            log.warn("The exit confirmation window hasn't appeared");
            return false;
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
     * Использует первый доступный приём (с питомцем или без).
     */
    private void clickBattleMethods() {
        if ($(".UserBattleMethod").isDisplayed()) {
            if (getPet())
                activeBattleMethods.get(0).click();
            else {
                if (anyBattleMethods.size() != 12 && activeBattleMethods.get(0).isDisplayed())
                    activeBattleMethods.get(0).click();
                else if (!activeBattleMethods.get(0).equals(anyBattleMethods.get(11)) && activeBattleMethods.get(0).isDisplayed())
                    activeBattleMethods.get(0).click();
            }
        }
        waitAboutSomeSeconds(1);
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
     * Сообщает итог боя в окно статуса и в уведомления (Telegram).
     */
    private void getMessage() {
        if (text.isDisplayed()) {
            String message = text.getText();
            status("Battle is over: " + message);
            Notifications.message("Battle is over: " + message);
        }
    }

}
