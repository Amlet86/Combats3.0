package com.combats.be;

import java.time.Duration;
import java.util.List;

import com.combats.pages.PageState;
import com.combats.utils.Notifications;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.combats.utils.GameControl.status;

/**
 * Восстановление после ошибки в игре, если её причина снаружи: игра выкинула на страницу входа,
 * сайт недоступен или закрылся браузер. Такие ошибки не считаются в лимит ошибок подряд: бот входит
 * заново или пережидает недоступность сайта с растущей паузой и играет дальше.
 * <p>
 * С браузером класс работает через {@link Browser}, чтобы логику можно было проверить тестами.
 */
class SiteRecovery {

    private static final Logger log = LoggerFactory.getLogger(SiteRecovery.class);

    /** Паузы между попытками открыть сайт; после последней ждём столько же, пока сайт не ответит. */
    static final List<Duration> SITE_WAITING_DELAYS =
            List.of(Duration.ofMinutes(1), Duration.ofMinutes(2), Duration.ofMinutes(5), Duration.ofMinutes(10));

    /** Итог восстановления. */
    enum Result {
        /** Причина была снаружи и устранена: можно играть дальше, ошибку не считаем. */
        RECOVERED,
        /** Игра выкинула на страницу входа, а войти заново не получилось. */
        LOGIN_FAILED,
        /** Внешней причины не нашли: это обычная ошибка бота, её надо посчитать. */
        NOT_EXTERNAL
    }

    /** Действия с браузером, которые нужны для восстановления. */
    interface Browser {

        /** Перезагружает текущую страницу, ошибки только пишет в лог. */
        void reload();

        /** Открывает стартовую страницу игры. Selenide при этом сам запускает новый браузер вместо закрытого. */
        void openBasePage();

        /** @return что сейчас открыто в браузере */
        PageState pageState();

        /** Входит в игру со страницы входа. @return true, если игра загрузилась */
        boolean login();

        /** Пауза; бросает исключение, если пользователь нажал «Stop» или кончилось время игры. */
        void pause(Duration duration);
    }

    private final Browser browser;

    SiteRecovery(Browser browser) {
        this.browser = browser;
    }

    /**
     * Перезагружает страницу и устраняет внешнюю причину ошибки, если она есть.
     */
    Result recover() {
        browser.reload();
        PageState state = browser.pageState();
        boolean external = false;
        if (state == PageState.BROWSER_LOST) {
            log.warn("The browser is lost, open the game in a new one");
            status("The browser is lost, restarting it");
            browser.openBasePage();
            state = browser.pageState();
            external = true;
        }
        if (state == PageState.SITE_UNAVAILABLE) {
            state = waitForSite();
            external = true;
        }
        if (state == PageState.LOGIN)
            return loginAgain();
        return external && state == PageState.GAME ? Result.RECOVERED : Result.NOT_EXTERNAL;
    }

    /**
     * Если сайт сейчас недоступен, ждёт, пока он ответит. Нужно и при запуске игры.
     *
     * @return что открыто в браузере после ожидания
     */
    PageState waitIfSiteUnavailable() {
        PageState state = browser.pageState();
        return state == PageState.SITE_UNAVAILABLE ? waitForSite() : state;
    }

    /**
     * Пережидает недоступность сайта: перезагружает страницу с паузами из {@link #SITE_WAITING_DELAYS},
     * пока вместо страницы ошибки Chrome не откроется что-то другое. Ждёт без ограничения:
     * остановит ожидание «Stop» или время окончания игры (их проверяет {@link Browser#pause}).
     *
     * @return что открылось вместо страницы ошибки
     */
    private PageState waitForSite() {
        log.warn("The game site is unavailable, wait until it answers");
        Notifications.message("The game site is unavailable, waiting until it answers.");
        Duration waited = Duration.ZERO;
        for (int attempt = 0; ; attempt++) {
            Duration delay = delayBeforeAttempt(attempt);
            status("The site is unavailable, next try in " + delay.toMinutes() + " min");
            browser.pause(delay);
            waited = waited.plus(delay);
            browser.reload();
            PageState state = browser.pageState();
            if (state == PageState.BROWSER_LOST) {
                browser.openBasePage();
                state = browser.pageState();
            }
            if (state != PageState.SITE_UNAVAILABLE) {
                log.info("The game site answers again after {} min: {}", waited.toMinutes(), state);
                Notifications.message("The game site is available again after " + waited.toMinutes() + " min.");
                return state;
            }
        }
    }

    /**
     * @param attempt номер попытки, с 0
     * @return пауза перед попыткой: 1, 2, 5, 10, 10… минут
     */
    static Duration delayBeforeAttempt(int attempt) {
        return SITE_WAITING_DELAYS.get(Math.min(attempt, SITE_WAITING_DELAYS.size() - 1));
    }

    private Result loginAgain() {
        log.warn("The game has logged the character out, log in again");
        status("Logged out by the game, logging in again");
        if (!browser.login()) {
            log.error("Can't log in again after the game has logged the character out");
            return Result.LOGIN_FAILED;
        }
        Notifications.message("The game logged the character out, the bot has logged in again.");
        return Result.RECOVERED;
    }

}
