package com.combats.utils;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;

import com.combats.exception.GameResumedException;
import com.combats.exception.GameStoppedException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Тесты остановки игры кнопкой «Stop» ({@link GameControl}).
 * Названия тестов описывают проверяемый случай.
 */
class GameControlTest {

    @AfterEach
    void resetStopFlag() {
        GameControl.prepare(null);
    }

    @Test
    void newGameIsNotStopped() {
        GameControl.prepare(Thread.currentThread());
        assertFalse(GameControl.isStopRequested());
        assertDoesNotThrow(GameControl::checkStop);
    }

    @Test
    void checkStopThrowsAfterStopRequest() {
        GameControl.prepare(new Thread(() -> { }));
        GameControl.requestStop();
        assertTrue(GameControl.isStopRequested());
        assertThrows(GameStoppedException.class, GameControl::checkStop);
    }

    @Test
    void stopWakesUpGameThreadFromLongWaitingLoop() throws InterruptedException {
        AtomicReference<Throwable> stoppedBy = new AtomicReference<>();
        Thread gameThread = new Thread(() -> {
            try {
                while (true) {
                    Utils.checkGameCanContinue();
                    Utils.waitAboutSomeSeconds(10);
                }
            } catch (Throwable e) {
                stoppedBy.set(e);
            }
        });
        GameControl.prepare(gameThread);
        gameThread.start();
        Thread.sleep(200);

        long stopTime = System.currentTimeMillis();
        GameControl.requestStop();
        gameThread.join(2000);

        assertFalse(gameThread.isAlive(), "the game thread must stop instead of waiting 10 seconds");
        assertTrue(System.currentTimeMillis() - stopTime < 2000);
        assertInstanceOf(GameStoppedException.class, stoppedBy.get());
    }

    @Test
    void statusIsPassedToListener() {
        AtomicReference<String> received = new AtomicReference<>();
        GameControl.setStatusListener(received::set);
        try {
            GameControl.status("In battle");
            assertEquals("In battle", received.get());
        } finally {
            GameControl.setStatusListener(status -> { });
        }
        assertEquals("In battle", GameControl.getLastStatus());
    }

    @Test
    void gameIsRunningFromPrepareUntilFinish() {
        assertFalse(GameControl.isRunning());
        GameControl.prepare(new Thread(() -> { }));
        assertTrue(GameControl.isRunning());
        GameControl.finish();
        assertFalse(GameControl.isRunning());
    }

    @Test
    void taskFromAnotherThreadRunsInGameThreadOnCheckStop() throws InterruptedException {
        AtomicReference<String> ranIn = new AtomicReference<>();
        Thread other = new Thread(() -> GameControl.runInGameThread(() -> ranIn.set(Thread.currentThread().getName())));
        other.start();
        other.join();
        assertNull(ranIn.get(), "the task must wait for the game thread");

        GameControl.checkStop();

        assertEquals(Thread.currentThread().getName(), ranIn.get());
    }

    @Test
    void failedTaskDoesNotStopTheGame() {
        AtomicReference<Boolean> secondRan = new AtomicReference<>(false);
        GameControl.runInGameThread(() -> {
            throw new IllegalStateException("screenshot failed");
        });
        GameControl.runInGameThread(() -> secondRan.set(true));

        assertDoesNotThrow(GameControl::checkStop);
        assertTrue(secondRan.get());
    }

    @Test
    void pauseHoldsGameThreadUntilResumeAndThenStartsOver() throws InterruptedException {
        AtomicReference<Throwable> leftWith = new AtomicReference<>();
        AtomicReference<Boolean> screenshotTaken = new AtomicReference<>(false);
        Thread gameThread = new Thread(() -> {
            try {
                while (true) {
                    GameControl.checkStop();
                    Thread.sleep(50);
                }
            } catch (Throwable e) {
                leftWith.set(e);
            }
        });
        GameControl.prepare(gameThread);
        GameControl.allowPause(true);
        gameThread.start();

        assertTrue(GameControl.requestPause());
        assertFalse(GameControl.requestPause(), "the second /pause changes nothing");
        Thread.sleep(300);
        assertTrue(gameThread.isAlive());
        assertTrue(GameControl.getLastStatus().startsWith("Paused"), GameControl.getLastStatus());
        // скриншот из Telegram снимается и на паузе
        GameControl.runInGameThread(() -> screenshotTaken.set(true));
        Thread.sleep(800);
        assertTrue(screenshotTaken.get());
        assertNull(leftWith.get(), "the game thread must wait while paused");

        assertTrue(GameControl.resume());
        gameThread.join(2000);

        assertInstanceOf(GameResumedException.class, leftWith.get());
        assertFalse(GameControl.resume(), "the bot isn't paused anymore");
    }

    @Test
    void stopWorksDuringPause() throws InterruptedException {
        AtomicReference<Throwable> leftWith = new AtomicReference<>();
        Thread gameThread = new Thread(() -> {
            try {
                while (true)
                    GameControl.checkStop();
            } catch (Throwable e) {
                leftWith.set(e);
            }
        });
        GameControl.prepare(gameThread);
        GameControl.allowPause(true);
        GameControl.requestPause();
        gameThread.start();
        Thread.sleep(200);

        GameControl.requestStop();
        gameThread.join(2000);

        assertInstanceOf(GameStoppedException.class, leftWith.get());
    }

    @Test
    void botCallsHumanAndWaitsForResume() throws InterruptedException {
        List<String> sent = new CopyOnWriteArrayList<>();
        Notifications.setNotifier(new Notifications.Notifier() {
            @Override
            public void message(String text) {
                sent.add(text);
            }

            @Override
            public void photo(String caption, byte[] png) {
                sent.add(caption);
            }
        });
        AtomicReference<Throwable> leftWith = new AtomicReference<>();
        Thread gameThread = new Thread(() -> {
            try {
                GameControl.pauseForHuman("Please connect", new byte[]{1});
            } catch (Throwable e) {
                leftWith.set(e);
            }
        });
        try {
            GameControl.prepare(gameThread);
            GameControl.allowPause(true);
            gameThread.start();
            Thread.sleep(300);

            assertTrue(gameThread.isAlive(), "the bot must wait for the human");
            assertEquals("Please connect", sent.get(0));

            GameControl.resume();
            gameThread.join(2000);

            assertInstanceOf(GameResumedException.class, leftWith.get());
        } finally {
            Notifications.setNotifier(null);
        }
    }

    @Test
    void callForHumanMentionsTheOwner() {
        try {
            Properties.setTelegramOwner(" @Amlett ");
            assertEquals("@Amlett Please connect", Notifications.mentionOwner("Please connect"));
            Properties.setTelegramOwner("Amlett");
            assertEquals("@Amlett Please connect", Notifications.mentionOwner("Please connect"));
            Properties.setTelegramOwner(" ");
            assertEquals("Please connect", Notifications.mentionOwner("Please connect"));
        } finally {
            Properties.setTelegramOwner(null);
        }
    }

    @Test
    void withoutTelegramTheBotDoesNotWaitForever() {
        GameControl.prepare(Thread.currentThread());
        GameControl.allowPause(true);

        assertDoesNotThrow(() -> GameControl.pauseForHuman("Please connect", null));
        assertFalse(GameControl.isPauseRequested());
    }

    @Test
    void pauseWaitsUntilTheGameLoopAllowsIt() {
        // при запуске браузера и входе пауза не срабатывает, а ждёт игрового цикла
        GameControl.prepare(Thread.currentThread());
        GameControl.requestPause();
        assertDoesNotThrow(GameControl::checkStop);
        assertTrue(GameControl.isPauseRequested());
    }

    @Test
    void pendingTasksAreDroppedWhenTheGameIsOver() {
        AtomicReference<Boolean> ran = new AtomicReference<>(false);
        GameControl.prepare(new Thread(() -> { }));
        GameControl.runInGameThread(() -> ran.set(true));

        GameControl.finish();
        GameControl.checkStop();

        assertFalse(ran.get());
    }

}
