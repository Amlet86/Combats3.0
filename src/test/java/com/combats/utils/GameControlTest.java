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
    }

}
