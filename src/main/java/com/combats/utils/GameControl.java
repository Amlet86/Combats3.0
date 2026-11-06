package com.combats.utils;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Consumer;

import com.combats.exception.GameResumedException;
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
    private static volatile boolean pauseRequested;
    /** Пауза срабатывает только в игровом цикле: при запуске браузера и входе её некуда вернуть. */
    private static volatile boolean pauseAllowed;
    private static final long PAUSE_CHECK_INTERVAL_MS = 500;
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
        pauseRequested = false;
        pauseAllowed = false;
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
     * Просит игру встать на паузу, чтобы человек мог играть сам. Поток игры останавливается
     * при ближайшем вызове {@link #checkStop()}: в бою это следующий шаг, в ожидании до 10 секунд.
     *
     * @return false, если пауза уже была запрошена
     */
    public static boolean requestPause() {
        if (pauseRequested)
            return false;
        pauseRequested = true;
        return true;
    }

    /**
     * Снимает паузу. Игра начинает круг заново, см. {@link GameResumedException}.
     *
     * @return false, если паузы не было
     */
    public static boolean resume() {
        if (!pauseRequested)
            return false;
        pauseRequested = false;
        return true;
    }

    /**
     * @return true, если пауза запрошена (поток игры мог ещё не дойти до неё)
     */
    public static boolean isPauseRequested() {
        return pauseRequested;
    }

    /**
     * Бот не может продолжать сам: зовёт человека сообщением со скриншотом и встаёт на паузу до /resume.
     * Вызывать из потока игры. Если снять паузу некому (нет Telegram) или пауза сейчас запрещена,
     * ничего не делает: стоять вечно нельзя, вызывающий код должен сам решить, как быть.
     *
     * @throws GameResumedException когда человек снял паузу
     * @throws GameStoppedException если человек остановил игру
     */
    public static void pauseForHuman(String text, byte[] screenshot) {
        if (!pauseAllowed || !Notifications.hasReceiver())
            return;
        Notifications.photo(Notifications.mentionOwner(text), screenshot);
        requestPause();
        checkStop();
    }

    /**
     * Разрешает или запрещает паузу. Игровой цикл разрешает её, когда браузер запущен и вход выполнен:
     * после паузы цикл начинает круг заново. Запрошенная раньше пауза сработает, как только её разрешат.
     */
    public static void allowPause(boolean allowed) {
        pauseAllowed = allowed;
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
     * Для циклов игры: выполняет задачи из очереди потока игры, прекращает игру, если пользователь
     * попросил её остановить, и ждёт, если он поставил игру на паузу.
     *
     * @throws GameStoppedException если пользователь попросил остановить игру
     * @throws GameResumedException если игра стояла на паузе и пользователь её снял
     */
    public static void checkStop() {
        if (stopRequested)
            throw new GameStoppedException();
        runGameThreadTasks();
        if (pauseRequested && pauseAllowed)
            waitWhilePaused();
    }

    /**
     * Держит поток игры, пока стоит пауза. Браузер в это время свободен, человек играет сам.
     * Скриншоты из Telegram снимаются и на паузе, «Stop» тоже работает.
     */
    private static void waitWhilePaused() {
        log.info("The game is paused");
        status("Paused: you can play by hand, send /resume to give control back to the bot");
        Notifications.message("The bot is paused, you can play by hand. Send /resume to give control back to the bot.");
        while (pauseRequested && !stopRequested) {
            runGameThreadTasks();
            try {
                Thread.sleep(PAUSE_CHECK_INTERVAL_MS);
            } catch (InterruptedException e) {
                // «Stop» будит поток прерыванием, флаг остановки проверит цикл
            }
        }
        if (stopRequested)
            throw new GameStoppedException();
        log.info("The game is resumed");
        status("Resumed after the pause");
        throw new GameResumedException();
    }

    private static void runGameThreadTasks() {
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
