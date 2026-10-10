package com.combats.pages;

import java.time.Duration;

import com.codeborne.selenide.ElementsCollection;
import com.codeborne.selenide.SelenideElement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.codeborne.selenide.Condition.visible;
import static com.codeborne.selenide.Selenide.*;
import static com.combats.fe.LoginForm.bot;
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

    private SelenideElement commitBtn = $("[action=commit]");
    private SelenideElement refreshBtn = $("[action=refresh]");
    private SelenideElement returnBtn = $("[action=gameover]");
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
        humanClick(returnBtn);
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
     * Выбирает случайную зону удара из пяти.
     */
    private void clickAttack() {
        if (attackRadios.get(1).isDisplayed())
            attackRadios.get(getRandomInt(0, 5)).click();
        if (attackRadios.size() > 6)
            body.sendKeys(String.valueOf(getRandomInt(1, 6)));
    }

    /**
     * Выбирает случайную зону блока из пяти.
     */
    private void clickDefend() {
        if (defendRadios.get(1).isDisplayed())
            defendRadios.get(getRandomInt(0, 5)).click();
    }

    /**
     * Сообщает итог боя: в Telegram, если бот запущен и чат известен, иначе в лог.
     */
    private void getMessage() {
        String message;
        if (text.isDisplayed()) {
            message = text.getText();
            status("Battle is over: " + message);
            if (bot == null || getTelegramChatId() == null)
                log.info("Battle result: {}", message);
            else
                bot.sendMsg(getTelegramChatId(), message);
        }

    }

}
