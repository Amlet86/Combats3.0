package com.combats.utils;

import java.util.concurrent.atomic.AtomicReference;

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
    void pendingTasksAreDroppedWhenTheGameIsOver() {
        AtomicReference<Boolean> ran = new AtomicReference<>(false);
        GameControl.prepare(new Thread(() -> { }));
        GameControl.runInGameThread(() -> ran.set(true));

        GameControl.finish();
        GameControl.checkStop();

        assertFalse(ran.get());
    }

}
