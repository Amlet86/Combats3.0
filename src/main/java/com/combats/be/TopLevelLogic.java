package com.combats.be;

import java.time.Duration;
import java.time.Instant;

import com.combats.pages.ChatPage;
import com.combats.pages.CityPage;
import com.combats.pages.PageState;
import com.combats.utils.GameStats;
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
import static com.combats.utils.Utils.checkGameCanContinue;
import static com.combats.utils.Utils.hasGameTime;
import static com.combats.utils.Utils.takeScreenshot;
import static com.combats.utils.Utils.waitAboutSomeSeconds;
import static com.combats.utils.Utils.waiting;

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
    private static final String GAME_TIME_IS_OVER = "There isn't error, the game's end because timeout.";
    private static final Duration PAUSE_STEP = Duration.ofSeconds(10);

    private final SiteRecovery recovery = new SiteRecovery(new RecoveryBrowser());

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
            openBasePageSafely();
            // сайт мог быть недоступен уже при запуске: ждём его, а не падаем на входе
            recovery.waitIfSiteUnavailable();
            status("Logging in");
            ChatPage.reset();
            if (!loginInGame())
                return "Login failed: check login and password.";
            prepareRoadFile();
            return playUntilEndTime();
        } catch (GameTimeIsOverException e) {
            return GAME_TIME_IS_OVER;
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
     * после {@link #MAX_ERRORS_IN_A_ROW} ошибок подряд останавливается. Ошибки с внешней причиной
     * (выкинуло на страницу входа, сайт недоступен, закрылся браузер) не считаются: их устраняет
     * {@link SiteRecovery}.
     *
     * @return сообщение о том, почему игра закончилась
     */
    private String playUntilEndTime() {
        int errorsInARow = 0;
        while (hasGameTime()) {
            Throwable error;
            try {
                checkStop();
                actionsInGame();
                errorsInARow = 0;
                continue;
            } catch (GameTimeIsOverException e) {
                break;
            } catch (GameStoppedException e) {
                return STOPPED_BY_USER;
            } catch (Error | RuntimeException e) {
                // после «Stop» операции браузера могут упасть с любой ошибкой, это не сбой игры
                if (isStopRequested())
                    return STOPPED_BY_USER;
                error = e;
            }
            // скриншот до перезагрузки, чтобы в Telegram пришёл экран, на котором случилась ошибка
            byte[] screenshot = takeScreenshot();
            SiteRecovery.Result recovered;
            try {
                recovered = recovery.recover();
            } catch (GameTimeIsOverException e) {
                break;
            } catch (GameStoppedException e) {
                return STOPPED_BY_USER;
            } catch (RuntimeException e) {
                if (isStopRequested())
                    return STOPPED_BY_USER;
                log.warn("Can't recover after the error", e);
                recovered = SiteRecovery.Result.NOT_EXTERNAL;
            }
            if (recovered == SiteRecovery.Result.RECOVERED) {
                GameStats.recovery();
                log.info("The error had an external cause, it's fixed, continue: {}", firstLine(error.getMessage()));
                continue;
            }
            if (recovered == SiteRecovery.Result.LOGIN_FAILED)
                return "The game logged the character out, and the bot can't log in again: check login and password.";
            errorsInARow++;
            GameStats.error();
            if (errorsInARow >= MAX_ERRORS_IN_A_ROW) {
                log.error("The game was stopped after {} errors in a row", errorsInARow, error);
                return "The game was stopped after " + errorsInARow + " errors in a row: " + error.getMessage();
            }
            log.warn("Error {} of {}, the page is reloaded, continue", errorsInARow, MAX_ERRORS_IN_A_ROW, error);
            status("Error " + errorsInARow + " of " + MAX_ERRORS_IN_A_ROW + ", the page is reloaded");
            Notifications.photo("Error " + errorsInARow + " of " + MAX_ERRORS_IN_A_ROW + ": "
                    + firstLine(error.getMessage()), screenshot);
        }
        return GAME_TIME_IS_OVER;
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
    private static void reloadPage() {
        try {
            switchTo().defaultContent();
            refresh();
            waitAboutSomeSeconds(5);
        } catch (RuntimeException e) {
            log.warn("Can't reload the page", e);
        }
    }

    /**
     * Открывает стартовую страницу игры. Ошибку только пишет в лог: если сайт не ответил,
     * это покажет проверка {@link PageState}.
     */
    private void openBasePageSafely() {
        try {
            startBrowser();
            waitAboutSomeSeconds(2);
        } catch (RuntimeException e) {
            log.warn("Can't open the game site", e);
        }
    }

    /**
     * Пауза, которую прерывают «Stop» и время окончания игры: ждём шагами по 10 секунд
     * и между шагами проверяем, можно ли продолжать.
     */
    private static void pause(Duration duration) {
        Instant end = Instant.now().plus(duration);
        while (true) {
            checkGameCanContinue();
            long millisLeft = Duration.between(Instant.now(), end).toMillis();
            if (millisLeft <= 0)
                return;
            waiting(Math.min(millisLeft, PAUSE_STEP.toMillis()) / 1000.0);
        }
    }

    /**
     * Браузер для {@link SiteRecovery}: те же действия, что и в игре.
     */
    private class RecoveryBrowser implements SiteRecovery.Browser {

        @Override
        public void reload() {
            reloadPage();
        }

        @Override
        public void openBasePage() {
            openBasePageSafely();
        }

        @Override
        public PageState pageState() {
            return PageState.detect();
        }

        @Override
        public boolean login() {
            return loginInGame();
        }

        @Override
        public void pause(Duration duration) {
            TopLevelLogic.pause(duration);
        }
    }

}
