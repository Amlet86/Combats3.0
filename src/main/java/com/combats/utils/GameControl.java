package com.combats.utils;

import java.util.function.Consumer;

import com.combats.exception.GameStoppedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Связь игры с окном статуса: игра сообщает, чем занята, а окно может попросить её остановиться.
 * Код игры ничего не знает о Swing: окно подписывается на статус через {@link #setStatusListener}.
 */
public class GameControl {

    private static final Logger log = LoggerFactory.getLogger(GameControl.class);

    private static volatile Consumer<String> statusListener = status -> { };
    private static volatile boolean stopRequested;
    private static volatile Thread gameThread;

    /**
     * Задаёт получателя статуса, например окно статуса. Вызывается из потока игры.
     */
    public static void setStatusListener(Consumer<String> listener) {
        statusListener = listener;
    }

    /**
     * Сообщает, чем сейчас занята игра: пишет в лог и передаёт получателю статуса.
     */
    public static void status(String text) {
        log.info("Status: {}", text);
        statusListener.accept(text);
    }

    /**
     * Готовит новую игру: запоминает её поток и сбрасывает запрос на остановку от прошлой игры.
     *
     * @param thread поток, в котором будет идти игра
     */
    public static void prepare(Thread thread) {
        stopRequested = false;
        gameThread = thread;
    }

    /**
     * Просит игру остановиться. Вызывается из окна статуса.
     * Прерывание будит поток из пауз, а флаг остаётся, даже если прерывание «съест» библиотека.
     */
    public static void requestStop() {
        stopRequested = true;
        Thread thread = gameThread;
        if (thread != null)
            thread.interrupt();
    }

    /**
     * @return true, если пользователь нажал «Stop»
     */
    public static boolean isStopRequested() {
        return stopRequested;
    }

    /**
     * Для циклов игры: прекращает игру, если пользователь нажал «Stop».
     *
     * @throws GameStoppedException если пользователь нажал «Stop»
     */
    public static void checkStop() {
        if (stopRequested)
            throw new GameStoppedException();
    }

}
