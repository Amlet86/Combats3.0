package com.combats.be;

import com.combats.pages.ChatPage;
import com.combats.pages.CityPage;
import com.combats.exception.GameStoppedException;
import com.combats.exception.GameTimeIsOverException;
import com.combats.utils.LogContext;
import com.combats.utils.Notifications;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.codeborne.selenide.Selenide.refresh;
import static com.codeborne.selenide.Selenide.switchTo;
import static com.combats.utils.GameControl.checkStop;
import static com.combats.utils.GameControl.isStopRequested;
import static com.combats.utils.GameControl.status;
import static com.combats.utils.MapNavigator.prepareRoadFile;
import static com.combats.utils.Properties.getTypeOfGame;
import static com.combats.utils.Properties.isHeadless;
import static com.combats.utils.Utils.hasGameTime;
import static com.combats.utils.Utils.takeScreenshot;
import static com.combats.utils.Utils.waitAboutSomeSeconds;

/**
 * Верхний уровень логики бота: запуск браузера, вход в игру и игровой цикл
 * до наступления времени окончания игры.
 */
public class TopLevelLogic extends BaseLevelLogic {

    private static final Logger log = LoggerFactory.getLogger(TopLevelLogic.class);

    /**
     * Число ошибок подряд, после которого игра останавливается. Одна ошибка (лаг, всплывающее окно)
     * не должна прерывать игру, но повторяющаяся ошибка означает, что бот продолжать не может.
     */
    private static final int MAX_ERRORS_IN_A_ROW = 3;

    private static final String STOPPED_BY_USER = "The game was stopped by the user.";

    /**
     * Настраивает Selenide перед запуском браузера.
     */
    private void configurationBrowser() {
        new ConfigBrowser(isHeadless());
    }

    /**
     * Один круг игры: бой в выбранном режиме. После PvP-боя бот ждёт восстановления HP.
     * До и после боя отправляет итоги боёв из чата: после смерти итог появляется позже, когда
     * бой закончится у всех.
     */
    private void actionsInGame() {
        ChatPage.reportNewBattleResults();
        log.info("Battle {} begins", LogContext.nextBattle());
        if (getTypeOfGame()) {
            PvP();
            ChatPage.reportNewBattleResults();
            new CityPage().waitForHpRecovery();
        } else {
            PvE();
            ChatPage.reportNewBattleResults();
        }
    }

    /**
     * Закрывает браузер.
     */
    private void endBrowser() {
        quitWebDriver();
    }

    /**
     * Запускает игру целиком: браузер, вход, игровой цикл. Браузер закрывается при любом исходе,
     * в том числе после нажатия «Stop».
     *
     * @return сообщение о том, почему игра закончилась
     */
    public String game() {
        try {
            status("Starting the browser");
            configurationBrowser();
            startBrowser();
            status("Logging in");
            ChatPage.reset();
            if (!loginInGame())
                return "Login failed: check login and password.";
            prepareRoadFile();
            return playUntilEndTime();
        } catch (Error | RuntimeException e) {
            // «Stop» во время запуска браузера или входа прерывает операции WebDriver с ошибкой
            if (isStopRequested())
                return STOPPED_BY_USER;
            throw e;
        } finally {
            // после «Stop» поток прерван, а с флагом прерывания WebDriver может не закрыть браузер
            Thread.interrupted();
            endBrowser();
        }
    }

    /**
     * Игровой цикл до времени окончания игры. После ошибки перезагружает страницу и продолжает,
     * после {@link #MAX_ERRORS_IN_A_ROW} ошибок подряд останавливается.
     *
     * @return сообщение о том, почему игра закончилась
     */
    private String playUntilEndTime() {
        int errorsInARow = 0;
        while (hasGameTime()) {
            try {
                checkStop();
                actionsInGame();
                errorsInARow = 0;
            } catch (GameTimeIsOverException e) {
                break;
            } catch (GameStoppedException e) {
                return STOPPED_BY_USER;
            } catch (Error | RuntimeException e) {
                // после «Stop» операции браузера могут упасть с любой ошибкой, это не сбой игры
                if (isStopRequested())
                    return STOPPED_BY_USER;
                errorsInARow++;
                if (errorsInARow >= MAX_ERRORS_IN_A_ROW) {
                    log.error("The game was stopped after {} errors in a row", errorsInARow, e);
                    return "The game was stopped after " + errorsInARow + " errors in a row: " + e.getMessage();
                }
                log.warn("Error {} of {}, reload the page and continue", errorsInARow, MAX_ERRORS_IN_A_ROW, e);
                status("Error " + errorsInARow + " of " + MAX_ERRORS_IN_A_ROW + ", reloading the page");
                Notifications.photo("Error " + errorsInARow + " of " + MAX_ERRORS_IN_A_ROW + ": "
                        + firstLine(e.getMessage()), takeScreenshot());
                reloadPage();
            }
        }
        return "There isn't error, the game's end because timeout.";
    }

    /**
     * @return первая строка сообщения об ошибке: у Selenide оно многострочное, с деталями для отладки
     */
    private static String firstLine(String message) {
        if (message == null)
            return "no message";
        int end = message.indexOf('\n');
        return end < 0 ? message : message.substring(0, end);
    }

    /**
     * Перезагружает страницу игры после ошибки. Ошибку самой перезагрузки только пишет в лог:
     * если браузер не отвечает, это покажет следующий круг игры.
     */
    private void reloadPage() {
        try {
            switchTo().defaultContent();
            refresh();
            waitAboutSomeSeconds(5);
        } catch (RuntimeException e) {
            log.warn("Can't reload the page", e);
        }
    }

}
