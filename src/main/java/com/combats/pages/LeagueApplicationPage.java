package com.combats.pages;

import java.time.Duration;
import java.time.Instant;

import com.codeborne.selenide.ElementsCollection;
import com.codeborne.selenide.SelenideElement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.codeborne.selenide.Condition.visible;
import static com.codeborne.selenide.Selenide.$;
import static com.codeborne.selenide.Selenide.$$;
import static com.codeborne.selenide.Selenide.page;
import static com.codeborne.selenide.Selenide.sleep;
import static com.combats.utils.GameControl.status;
import static com.combats.utils.Utils.checkGameCanContinue;
import static com.combats.utils.Utils.waitAboutSomeSeconds;

/**
 * Заявка на бой Лиги, вкладка «1 на 1» в Таверне города East (снято 06.11.2026): «Подать заявку», игра ищет
 * соперника по рейтингу, потом появляется «Готов». Страница сама не обновляется, поэтому заново открываем вкладку.
 */
public class LeagueApplicationPage extends BasePage {

    private static final Logger log = LoggerFactory.getLogger(LeagueApplicationPage.class);

    private static final int CHECK_SECONDS = 10;
    /** Столько проверок подряд бот может не видеть вкладку «1 на 1», прежде чем считать это ошибкой. */
    private static final int MAX_CHECKS_WITHOUT_TAB = 6;
    /** В заявке бывает «Бой начнется через 1.4 мин.», ждём с запасом. */
    private static final Duration MAX_WAIT_AFTER_READY = Duration.ofMinutes(3);
    private static final long BATTLE_CHECK_INTERVAL_MS = 500;
    /** Так игра отвечает вне Таверны, например в Комнате Знахаря. */
    private static final String WRONG_ROOM = "невозможно подавать заявки";

    private final SelenideElement battlesBtn = $("input[value='Поединки']");
    private final SelenideElement duelTab = $("a[href*='level=fiz']");
    /** Имя кнопки похоже на случайное ({@code open555}), поэтому ищем по надписи, а ловушки с {@code fb} обходим. */
    private final ElementsCollection submitBtns = $$("input[type=submit][value='Подать заявку']:not([onclick*='fb'])");
    private final SelenideElement readyBtn = $("input[name=ready]");

    /**
     * Подаёт заявку и ждёт начала боя. Активен должен быть фрейм игры.
     *
     * @throws IllegalStateException если бот не в той комнате или не может попасть на вкладку заявок
     */
    public BattlePage enterToBattle() {
        status("Looking for a league battle");
        humanClick(battlesBtn);
        humanClick(duelTab);
        int checksWithoutTab = 0;
        while (!GoToBattlePage.battleStarted()) {
            checkGameCanContinue();
            if (duelTab.isDisplayed()) {
                checksWithoutTab = 0;
                checkApplicationsPage();
            } else if (++checksWithoutTab > MAX_CHECKS_WITHOUT_TAB) {
                throw new IllegalStateException("Can't get to the «1 на 1» applications");
            } else {
                humanClick(battlesBtn);
            }
            waitAboutSomeSeconds(CHECK_SECONDS);
        }
        return page(BattlePage.class);
    }

    /**
     * Один шаг на вкладке «1 на 1»: подтверждает бой, подаёт заявку или обновляет страницу.
     */
    private void checkApplicationsPage() {
        if ($("body").getText().contains(WRONG_ROOM))
            throw new IllegalStateException("The game doesn't accept applications in this room");
        SelenideElement submit = submitBtns.findBy(visible);
        if (readyBtn.isDisplayed()) {
            log.info("An opponent is found, confirm the battle");
            status("Confirming the league battle");
            humanClick(readyBtn);
            waitForBattleStart();
        } else if (submit.isDisplayed()) {
            log.info("Submit a league application");
            humanClick(submit);
        } else {
            status("Waiting for a league opponent");
            humanClick(duelTab);
        }
    }

    /**
     * После «Готов» бой начинается сам. Проверяем часто: таймер первого хода идёт с начала боя, а обычная
     * проверка раз в 20 с съедала его (06.11.2026). Страницу не трогаем.
     */
    private static void waitForBattleStart() {
        Instant deadline = Instant.now().plus(MAX_WAIT_AFTER_READY);
        while (!GoToBattlePage.battleStarted() && Instant.now().isBefore(deadline)) {
            checkGameCanContinue();
            sleep(BATTLE_CHECK_INTERVAL_MS);
        }
    }

}
