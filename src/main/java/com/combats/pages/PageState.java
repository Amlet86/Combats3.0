package com.combats.pages;

import com.codeborne.selenide.WebDriverRunner;
import org.openqa.selenium.NoSuchSessionException;
import org.openqa.selenium.WebDriverException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.codeborne.selenide.Selenide.executeJavaScript;
import static com.codeborne.selenide.Selenide.switchTo;

/**
 * Что сейчас открыто в браузере. Нужно после ошибки, чтобы понять её причину: игра выкинула на
 * страницу входа, сайт недоступен или браузер закрылся. Все три случая видели вживую 24.10.2026:
 * бот останавливался после трёх ошибок подряд, хотя ему надо было войти заново или переждать.
 */
public enum PageState {

    /** Игра загружена: есть основной фрейм игры. */
    GAME,
    /** Страница входа: игра закончила сессию. */
    LOGIN,
    /** Страница ошибки Chrome «Не удается получить доступ к сайту» (ERR_TIMED_OUT и подобные). */
    SITE_UNAVAILABLE,
    /** Браузер закрылся или не отвечает (invalid session id, disconnected). */
    BROWSER_LOST,
    /** Что-то другое: страница ещё грузится или игра показала незнакомый экран. */
    UNKNOWN;

    private static final Logger log = LoggerFactory.getLogger(PageState.class);

    /**
     * Смотрит верхний документ. У страницы ошибки Chrome {@code body.neterror} и блок
     * {@code #main-frame-error}, у страницы входа поля {@code login} и {@code psw}.
     */
    private static final String DETECT = "const d = top.document;"
            + "if (location.protocol === 'chrome-error:' || d.getElementById('main-frame-error')"
            + "    || (d.body && d.body.classList.contains('neterror'))) return 'SITE_UNAVAILABLE';"
            + "if (d.querySelector(arguments[0])) return 'GAME';"
            + "if (d.querySelector('[name=login]') && d.querySelector('[name=psw]')) return 'LOGIN';"
            + "return 'UNKNOWN';";

    /**
     * @return что открыто в браузере сейчас; ошибки WebDriver не бросает
     */
    public static PageState detect() {
        if (!WebDriverRunner.hasWebDriverStarted())
            return BROWSER_LOST;
        try {
            switchTo().defaultContent();
            return fromScriptResult(executeJavaScript(DETECT, BasePage.GAME_FRAME_LOCATOR));
        } catch (RuntimeException e) {
            PageState state = fromException(e);
            log.warn("Can't check the page, consider it {}", state, e);
            return state;
        }
    }

    /**
     * @param result ответ скрипта {@link #DETECT}
     * @return состояние по ответу; на непонятный ответ {@link #UNKNOWN}
     */
    static PageState fromScriptResult(Object result) {
        if (result == null)
            return UNKNOWN;
        try {
            return valueOf(result.toString());
        } catch (IllegalArgumentException e) {
            return UNKNOWN;
        }
    }

    /**
     * Ошибка при проверке страницы: если браузер закрыт или потерял связь с драйвером, это
     * {@link #BROWSER_LOST}, иначе {@link #UNKNOWN}.
     */
    static PageState fromException(RuntimeException e) {
        if (e instanceof NoSuchSessionException)
            return BROWSER_LOST;
        String message = e.getMessage();
        if (e instanceof WebDriverException && message != null
                && (message.contains("disconnected") || message.contains("chrome not reachable")))
            return BROWSER_LOST;
        return UNKNOWN;
    }

}
