package com.combats.be;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

import com.combats.exception.GameStoppedException;
import com.combats.pages.PageState;
import org.junit.jupiter.api.Test;

import static com.combats.pages.PageState.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Тесты восстановления после ошибки ({@link SiteRecovery}) на поддельном браузере: он по очереди
 * отдаёт заданные состояния страницы и записывает, что с ним делали.
 * Названия тестов описывают проверяемый случай.
 */
class SiteRecoveryTest {

    @Test
    void ordinaryErrorOnTheGamePageIsCounted() {
        FakeBrowser browser = new FakeBrowser(GAME);
        assertEquals(SiteRecovery.Result.NOT_EXTERNAL, new SiteRecovery(browser).recover());
        assertEquals(List.of("reload", "state"), browser.actions);
    }

    @Test
    void unknownPageIsCounted() {
        FakeBrowser browser = new FakeBrowser(UNKNOWN);
        assertEquals(SiteRecovery.Result.NOT_EXTERNAL, new SiteRecovery(browser).recover());
    }

    @Test
    void loginPageLeadsToLoginAgain() {
        FakeBrowser browser = new FakeBrowser(LOGIN);
        assertEquals(SiteRecovery.Result.RECOVERED, new SiteRecovery(browser).recover());
        assertEquals(List.of("reload", "state", "login"), browser.actions);
    }

    @Test
    void failedLoginAgainStopsTheGame() {
        FakeBrowser browser = new FakeBrowser(LOGIN);
        browser.loginSucceeds = false;
        assertEquals(SiteRecovery.Result.LOGIN_FAILED, new SiteRecovery(browser).recover());
    }

    @Test
    void unavailableSiteIsWaitedWithGrowingPauses() {
        FakeBrowser browser = new FakeBrowser(SITE_UNAVAILABLE, SITE_UNAVAILABLE, SITE_UNAVAILABLE,
                SITE_UNAVAILABLE, SITE_UNAVAILABLE, GAME);
        assertEquals(SiteRecovery.Result.RECOVERED, new SiteRecovery(browser).recover());
        assertEquals(List.of(Duration.ofMinutes(1), Duration.ofMinutes(2), Duration.ofMinutes(5),
                Duration.ofMinutes(10), Duration.ofMinutes(10)), browser.pauses);
    }

    @Test
    void afterUnavailableSiteTheBotLogsInIfTheSessionIsOver() {
        FakeBrowser browser = new FakeBrowser(SITE_UNAVAILABLE, LOGIN);
        assertEquals(SiteRecovery.Result.RECOVERED, new SiteRecovery(browser).recover());
        assertTrue(browser.actions.contains("login"));
        assertEquals(List.of(Duration.ofMinutes(1)), browser.pauses);
    }

    @Test
    void lostBrowserIsReopenedAndTheBotLogsIn() {
        FakeBrowser browser = new FakeBrowser(BROWSER_LOST, LOGIN);
        assertEquals(SiteRecovery.Result.RECOVERED, new SiteRecovery(browser).recover());
        assertEquals(List.of("reload", "state", "open", "state", "login"), browser.actions);
    }

    @Test
    void browserThatCantBeReopenedIsCounted() {
        FakeBrowser browser = new FakeBrowser(BROWSER_LOST, BROWSER_LOST);
        assertEquals(SiteRecovery.Result.NOT_EXTERNAL, new SiteRecovery(browser).recover());
    }

    @Test
    void stopDuringWaitingForTheSiteEndsTheRecovery() {
        FakeBrowser browser = new FakeBrowser(SITE_UNAVAILABLE, SITE_UNAVAILABLE, GAME);
        browser.stopOnPause = 2;
        assertThrows(GameStoppedException.class, () -> new SiteRecovery(browser).recover());
        assertEquals(1, browser.pauses.size());
    }

    @Test
    void availableSiteAtStartIsNotWaited() {
        FakeBrowser browser = new FakeBrowser(LOGIN);
        assertEquals(LOGIN, new SiteRecovery(browser).waitIfSiteUnavailable());
        assertTrue(browser.pauses.isEmpty());
    }

    @Test
    void unavailableSiteAtStartIsWaited() {
        FakeBrowser browser = new FakeBrowser(SITE_UNAVAILABLE, SITE_UNAVAILABLE, LOGIN);
        assertEquals(LOGIN, new SiteRecovery(browser).waitIfSiteUnavailable());
        assertEquals(2, browser.pauses.size());
    }

    @Test
    void pausesGrowUpToTenMinutes() {
        assertEquals(Duration.ofMinutes(1), SiteRecovery.delayBeforeAttempt(0));
        assertEquals(Duration.ofMinutes(5), SiteRecovery.delayBeforeAttempt(2));
        assertEquals(Duration.ofMinutes(10), SiteRecovery.delayBeforeAttempt(3));
        assertEquals(Duration.ofMinutes(10), SiteRecovery.delayBeforeAttempt(100));
    }

    /**
     * Браузер, который на каждую проверку страницы отдаёт следующее состояние из списка
     * (последнее повторяется), а паузы не ждёт, а записывает.
     */
    private static class FakeBrowser implements SiteRecovery.Browser {

        final Deque<PageState> states;
        final List<String> actions = new ArrayList<>();
        final List<Duration> pauses = new ArrayList<>();
        boolean loginSucceeds = true;
        /** Номер паузы (с 1), на которой пользователь нажимает «Stop»; 0 — не нажимает. */
        int stopOnPause;

        FakeBrowser(PageState... states) {
            this.states = new ArrayDeque<>(List.of(states));
        }

        @Override
        public void reload() {
            actions.add("reload");
        }

        @Override
        public void openBasePage() {
            actions.add("open");
        }

        @Override
        public PageState pageState() {
            actions.add("state");
            return states.size() > 1 ? states.poll() : states.peek();
        }

        @Override
        public boolean login() {
            actions.add("login");
            return loginSucceeds;
        }

        @Override
        public void pause(Duration duration) {
            if (pauses.size() + 1 == stopOnPause)
                throw new GameStoppedException();
            pauses.add(duration);
        }
    }

}
