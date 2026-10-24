package com.combats;

import java.awt.GraphicsEnvironment;

import javax.swing.SwingUtilities;

import com.combats.fe.GameLauncher;
import com.combats.fe.LoginForm;
import com.combats.telegram.TelegramService;
import com.combats.utils.FileWorker;
import com.combats.utils.LaunchOptions;
import com.combats.utils.Properties;
import com.combats.utils.ServerSecrets;
import com.formdev.flatlaf.FlatLightLaf;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Точка входа: читает сохранённые настройки, запускает Telegram-бота (если заданы владелец и токен)
 * и открывает форму настроек. С параметром {@code --server} работает без окон (см. {@link LaunchOptions}).
 * <p>
 * Запуск собранного jar из консоли:
 * java -jar target/Combats-&lt;версия из pom.xml&gt;.jar [--server ...]
 */
public class Start {

    private static final Logger log = LoggerFactory.getLogger(Start.class);

    /**
     * Без параметров показывает форму {@link LoginForm}, игра запускается кнопкой «Start» или командой /play
     * в Telegram. С {@code --server} сразу начинает игру (или ждёт /play с {@code --wait}).
     *
     * @param args параметры запуска, см. {@link LaunchOptions}
     */
    public static void main(String[] args) {
        LaunchOptions options;
        try {
            options = LaunchOptions.parse(args);
        } catch (IllegalArgumentException e) {
            log.error("{}\n{}", e.getMessage(), LaunchOptions.USAGE);
            System.exit(2);
            return;
        }
        if (options.server()) {
            startServer(options);
            return;
        }
        FileWorker.readUserDataFile();
        if (GraphicsEnvironment.isHeadless()) {
            log.error("There is no display for the settings form. Run with --server.\n{}", LaunchOptions.USAGE);
            System.exit(2);
            return;
        }
        TelegramService.startInBackground(GameLauncher::playFromTelegram);
        // современный вид для всех окон программы (форма, окно статуса)
        FlatLightLaf.setup();
        SwingUtilities.invokeLater(() -> new LoginForm().setVisible(true));
    }

    /**
     * Работа без окон: логин из параметра {@code --login}, пароль и Telegram из окружения
     * ({@link ServerSecrets}), браузер всегда без окна. Файл настроек не читается и не пишется,
     * чтобы пароль не лежал на сервере. Управление — командами Telegram.
     */
    private static void startServer(LaunchOptions options) {
        FileWorker.disable();
        ServerSecrets secrets = ServerSecrets.fromEnvironment();
        if (secrets.password() == null) {
            log.error("No password: set {} in the environment (systemd EnvironmentFile) or run the bot "
                    + "in a console to type it.", ServerSecrets.PASSWORD);
            System.exit(1);
            return;
        }
        Properties.setUserLogin(options.login());
        Properties.setUserPassword(secrets.password());
        Properties.setTelegramOwner(secrets.telegramOwner());
        Properties.setTelegramBotToken(secrets.telegramToken());
        Properties.setTelegramChatId(secrets.telegramChatId());
        boolean telegram = TelegramService.isEnabled(Properties.getTelegramBotToken(), Properties.getTelegramOwner());
        if (options.waitForPlay() && !telegram) {
            log.error("--wait needs Telegram: set {} and {}, otherwise nobody can send /play.",
                    ServerSecrets.TELEGRAM_OWNER, ServerSecrets.TELEGRAM_TOKEN);
            System.exit(1);
            return;
        }
        Properties.setTypeOfGame(!options.dungeon());
        Properties.setPet(options.pet());
        Properties.setHeadless(true);
        Properties.setEndTimeOfTheGame(options.endHours(), options.endMinutes());
        log.info("Server mode: {} as {}, pet {}, {}, Telegram {}", options.dungeon() ? "Dungeon" : "PvP",
                options.login(),
                options.pet() ? "on" : "off",
                Properties.getEndTimeOfTheGame() == null ? "no end time" : "until " + Properties.getEndTimeOfTheGame(),
                telegram ? "on" : "off");

        GameLauncher.setServerMode();
        TelegramService.startInBackground(GameLauncher::playFromTelegram);
        if (options.waitForPlay())
            log.info("Waiting for /play in Telegram");
        else
            GameLauncher.start();
    }

}
