package com.combats.fe;

import javax.swing.SwingUtilities;

import com.combats.be.TopLevelLogic;
import com.combats.utils.GameControl;
import com.combats.utils.Notifications;
import com.combats.utils.Properties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Запуск игры из формы (кнопка «Start») и из Telegram (команда /play).
 * Игра идёт в отдельном потоке «game», её ход показывает окно статуса.
 */
public final class GameLauncher {

    private static final Logger log = LoggerFactory.getLogger(GameLauncher.class);

    /** Нажимает «Start» в форме, пока она открыта; после закрытия формы null. */
    private static volatile Runnable formStart;
    private static volatile StatusWindow statusWindow;

    private GameLauncher() {
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
    static synchronized void start() {
        if (GameControl.isRunning()) {
            log.warn("The game is already running");
            return;
        }
        Thread gameThread = new Thread(GameLauncher::playGame, "game");
        // до показа окна, чтобы повторный /play сразу видел, что игра уже идёт
        GameControl.prepare(gameThread);
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
        log.info(result);
        Notifications.message(result + "\nSend /play to start again.");
        statusWindow.showResult(result);
    }

}
