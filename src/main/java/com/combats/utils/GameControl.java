package com.combats.utils;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Consumer;

import com.combats.exception.GameStoppedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Связь игры с окном статуса и Telegram: игра сообщает, чем занята, а снаружи можно попросить её
 * остановиться или выполнить действие в потоке игры (например, снять скриншот).
 * Код игры ничего не знает о Swing и Telegram: окно подписывается на статус через {@link #setStatusListener}.
 */
public class GameControl {

    private static final Logger log = LoggerFactory.getLogger(GameControl.class);

    private static volatile Consumer<String> statusListener = status -> { };
    private static volatile String lastStatus;
    private static volatile boolean stopRequested;
    private static volatile boolean running;
    private static volatile Thread gameThread;
    private static final Queue<Runnable> gameThreadTasks = new ConcurrentLinkedQueue<>();

    /**
     * Задаёт получателя статуса, например окно статуса.
     */
    public static void setStatusListener(Consumer<String> listener) {
        statusListener = listener;
    }

    /**
     * Сообщает, чем сейчас занята игра: пишет в лог и передаёт получателю статуса.
     */
    public static void status(String text) {
        log.info("Status: {}", text);
        lastStatus = text;
        statusListener.accept(text);
    }

    /**
     * @return последний статус игры или null, если игра ещё ничего не сообщала
     */
    public static String getLastStatus() {
        return lastStatus;
    }

    /**
     * Готовит новую игру: запоминает её поток и сбрасывает запрос на остановку от прошлой игры.
     *
     * @param thread поток, в котором будет идти игра; null — игры нет
     */
    public static void prepare(Thread thread) {
        stopRequested = false;
        lastStatus = null;
        gameThreadTasks.clear();
        gameThread = thread;
        running = thread != null;
    }

    /**
     * Отмечает, что игра закончилась. Задачи, которые поток игры не успел выполнить, отбрасываются.
     */
    public static void finish() {
        running = false;
        gameThread = null;
        if (!gameThreadTasks.isEmpty()) {
            log.info("The game is over, {} pending task(s) are dropped", gameThreadTasks.size());
            gameThreadTasks.clear();
        }
    }

    /**
     * @return true, если игра запущена и ещё не закончилась
     */
    public static boolean isRunning() {
        return running;
    }

    /**
     * Просит игру остановиться. Вызывается из окна статуса или из Telegram.
     * Прерывание будит поток из пауз, а флаг остаётся, даже если прерывание «съест» библиотека.
     */
    public static void requestStop() {
        stopRequested = true;
        Thread thread = gameThread;
        if (thread != null)
            thread.interrupt();
    }

    /**
     * @return true, если пользователь попросил остановить игру
     */
    public static boolean isStopRequested() {
        return stopRequested;
    }

    /**
     * Ставит задачу в очередь потока игры. Нужна для действий с браузером из другого потока:
     * Selenide привязывает браузер к потоку, который его запустил.
     * Задача выполнится при ближайшем вызове {@link #checkStop()}.
     */
    public static void runInGameThread(Runnable task) {
        gameThreadTasks.add(task);
    }

    /**
     * Для циклов игры: выполняет задачи из очереди потока игры и прекращает игру,
     * если пользователь попросил её остановить.
     *
     * @throws GameStoppedException если пользователь попросил остановить игру
     */
    public static void checkStop() {
        if (stopRequested)
            throw new GameStoppedException();
        Runnable task;
        while ((task = gameThreadTasks.poll()) != null) {
            try {
                task.run();
            } catch (RuntimeException e) {
                log.warn("A task in the game thread failed", e);
            }
        }
    }

}
