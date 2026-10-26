package com.combats.fe;

import javax.swing.SwingUtilities;

import com.combats.be.TopLevelLogic;
import com.combats.utils.GameControl;
import com.combats.utils.GameStats;
import com.combats.utils.LogContext;
import com.combats.utils.Notifications;
import com.combats.utils.Properties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Запуск игры из формы (кнопка «Start»), из Telegram (команда /play) и сразу при старте в серверном
 * режиме. Игра идёт в отдельном потоке «game», её ход показывает окно статуса (на сервере — только лог).
 */
public final class GameLauncher {

    private static final Logger log = LoggerFactory.getLogger(GameLauncher.class);

    /** Сколько ждать, пока игра закроет браузер, когда программу останавливают (systemd stop, Ctrl+C). */
    private static final long SHUTDOWN_WAIT_MS = 30_000;

    /** Нажимает «Start» в форме, пока она открыта; после закрытия формы null. */
    private static volatile Runnable formStart;
    private static volatile StatusWindow statusWindow;
    /** Работа без окон (параметр --server): окно статуса не создаём, статус пишется только в лог. */
    private static volatile boolean serverMode;
    private static volatile Thread gameThread;

    private GameLauncher() {
    }

    /**
     * Включает работу без окон: для сервера, где нет экрана. Когда программу останавливают
     * (systemd stop, Ctrl+C), просит игру остановиться и ждёт, пока она закроет браузер,
     * иначе Chrome остался бы висеть на сервере.
     */
    public static void setServerMode() {
        serverMode = true;
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            Thread thread = gameThread;
            if (!GameControl.isRunning() || thread == null)
                return;
            log.info("The program is being stopped, stop the game and close the browser");
            GameControl.requestStop();
            try {
                thread.join(SHUTDOWN_WAIT_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }, "shutdown"));
    }

    /**
     * Форма сообщает, как нажать её кнопку «Start», чтобы /play запускал игру с настройками из формы.
     *
     * @param start нажатие «Start» или null, когда форма закрыта
     */
    static void setFormStart(Runnable start) {
        formStart = start;
    }

    /**
     * Запуск по команде /play. Пока открыта форма, нажимает в ней «Start»; после первой игры
     * запускает новую с теми же настройками, время окончания пересчитывается от текущего момента.
     *
     * @return ответ для пользователя
     */
    public static synchronized String playFromTelegram() {
        if (GameControl.isRunning())
            return "The game is already running.";
        Runnable start = formStart;
        if (start != null) {
            SwingUtilities.invokeLater(start);
            return "Starting the game with the settings from the form...";
        }
        Properties.recalculateEndTimeOfTheGame();
        start();
        return "Starting the game...";
    }

    /**
     * Запускает игру с текущими настройками из {@link Properties}. Можно вызывать из любого потока.
     */
    public static synchronized void start() {
        if (GameControl.isRunning()) {
            log.warn("The game is already running");
            return;
        }
        Thread gameThread = new Thread(GameLauncher::playGame, "game");
        GameLauncher.gameThread = gameThread;
        // до показа окна, чтобы повторный /play сразу видел, что игра уже идёт
        GameControl.prepare(gameThread);
        if (serverMode) {
            gameThread.start();
            return;
        }
        SwingUtilities.invokeLater(() -> {
            if (statusWindow != null)
                statusWindow.dispose();
            StatusWindow window = new StatusWindow();
            statusWindow = window;
            GameControl.setStatusListener(window::showStatus);
            window.setVisible(true);
            gameThread.start();
        });
    }

    /**
     * Играет вне потока Swing, потому что игра длится часами и не должна блокировать интерфейс.
     * По окончании показывает итог игры в окне статуса и отправляет его в уведомления.
     */
    private static void playGame() {
        LogContext.startGame(Properties.getUserLogin(), Properties.getTypeOfGame());
        GameStats.start();
        Notifications.message("The game is started: " + (Properties.getTypeOfGame() ? "PvP" : "Dungeon")
                + (Properties.getEndTimeOfTheGame() == null ? ", no end time"
                : ", until " + Properties.getEndTimeOfTheGame().toLocalTime()));
        String result;
        try {
            result = new TopLevelLogic().game();
        } catch (Error | RuntimeException e) {
            log.error("The game was stopped by an error", e);
            result = "The game was stopped by an error: " + e;
        }
        GameControl.finish();
        GameStats.finish();
        log.info(result);
        Notifications.message(result + "\n\n" + GameStats.summary() + "\n\nSend /play to start again.");
        if (statusWindow != null)
            statusWindow.showResult(result);
        LogContext.clear();
    }

}
