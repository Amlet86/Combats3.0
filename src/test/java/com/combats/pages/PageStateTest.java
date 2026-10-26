package com.combats.pages;

import org.openqa.selenium.NoSuchSessionException;
import org.openqa.selenium.TimeoutException;
import org.openqa.selenium.WebDriverException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Тесты разбора состояния страницы ({@link PageState}) по ответу скрипта и по ошибке WebDriver.
 * Названия тестов описывают проверяемый случай.
 */
class PageStateTest {

    @Test
    void scriptResultIsParsed() {
        assertEquals(PageState.SITE_UNAVAILABLE, PageState.fromScriptResult("SITE_UNAVAILABLE"));
        assertEquals(PageState.LOGIN, PageState.fromScriptResult("LOGIN"));
        assertEquals(PageState.GAME, PageState.fromScriptResult("GAME"));
    }

    @Test
    void strangeScriptResultIsUnknown() {
        assertEquals(PageState.UNKNOWN, PageState.fromScriptResult(null));
        assertEquals(PageState.UNKNOWN, PageState.fromScriptResult("something"));
    }

    @Test
    void closedBrowserIsLost() {
        assertEquals(PageState.BROWSER_LOST, PageState.fromException(
                new NoSuchSessionException("invalid session id: session deleted as the browser has closed the connection")));
        assertEquals(PageState.BROWSER_LOST, PageState.fromException(
                new WebDriverException("disconnected: not connected to DevTools")));
    }

    @Test
    void otherWebDriverErrorIsUnknown() {
        assertEquals(PageState.UNKNOWN, PageState.fromException(
                new TimeoutException("timeout: Timed out receiving message from renderer: 30.000")));
    }

}
