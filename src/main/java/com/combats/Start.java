package com.combats;

import javax.swing.SwingUtilities;

import com.combats.fe.GameLauncher;
import com.formdev.flatlaf.FlatLightLaf;
import com.combats.fe.LoginForm;
import com.combats.telegram.TelegramService;
import com.combats.utils.FileWorker;

/**
 * Точка входа: читает сохранённые настройки, запускает Telegram-бота (если задан токен)
 * и открывает форму настроек.
 * <p>
 * Запуск собранного jar из консоли:
 * java -jar target/Combats-&lt;версия из pom.xml&gt;.jar
 */
public class Start {

    /**
     * Показывает форму {@link LoginForm}, игра запускается кнопкой «Start» или командой /play в Telegram.
     *
     * @param args не используются
     */
    public static void main(String[] args) {
        FileWorker.readUserDataFile();
        TelegramService.startInBackground(GameLauncher::playFromTelegram);
        // современный вид для всех окон программы (форма, окно статуса)
        FlatLightLaf.setup();
        SwingUtilities.invokeLater(() -> new LoginForm().setVisible(true));
    }

}
