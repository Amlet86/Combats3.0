package com.combats.telegram;

import java.util.function.Supplier;

import com.combats.utils.Notifications;
import com.combats.utils.Properties;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.OkHttpClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.telegram.telegrambots.client.okhttp.OkHttpTelegramClient;
import org.telegram.telegrambots.longpolling.TelegramBotsLongPollingApplication;
import org.telegram.telegrambots.longpolling.util.TelegramOkHttpClientFactory;
import org.telegram.telegrambots.meta.api.methods.GetMe;
import org.telegram.telegrambots.meta.api.objects.User;
import org.telegram.telegrambots.meta.generics.TelegramClient;

/**
 * Запуск и остановка Telegram-бота по настройкам из {@link Properties}: токен и владелец.
 * Бот работает, пока открыта программа, и между играми тоже, чтобы новую игру можно было начать
 * командой /play.
 */
public final class TelegramService {

    private static final Logger log = LoggerFactory.getLogger(TelegramService.class);

    private static TelegramBotsLongPollingApplication application;
    private static String runningSettings;

    private TelegramService() {
    }

    /**
     * Запускает бота в фоновом потоке, чтобы не задерживать окно программы. Если бот уже работает
     * с теми же настройками, ничего не делает; если настройки изменились, перезапускает его.
     * Если владелец или токен не заданы, Telegram выключен: бот не запускается (а запущенный
     * останавливается), уведомления только пишутся в лог.
     *
     * @param play запуск игры по команде /play; возвращает ответ для пользователя
     */
    public static void startInBackground(Supplier<String> play) {
        Thread thread = new Thread(() -> start(play), "telegram-start");
        thread.setDaemon(true);
        thread.start();
    }

    private static synchronized void start(Supplier<String> play) {
        String token = trimToNull(Properties.getTelegramBotToken());
        String owner = trimToNull(Properties.getTelegramOwner());
        String settings = token + "\n" + owner;
        if (settings.equals(runningSettings))
            return;
        stop();
        if (!isEnabled(token, owner)) {
            log.info("Telegram owner or token isn't set, Telegram is off");
            return;
        }
        try {
            OkHttpClient httpClient = new TelegramOkHttpClientFactory.DefaultOkHttpClientCreator().get();
            TelegramClient client = new OkHttpTelegramClient(httpClient, token);
            User me = client.execute(new GetMe());
            TelegramBot bot = new TelegramBot(client, owner, play);
            bot.registerCommands();
            TelegramBotsLongPollingApplication newApplication =
                    new TelegramBotsLongPollingApplication(ObjectMapper::new, () -> httpClient);
            newApplication.registerBot(token, bot);
            application = newApplication;
            runningSettings = settings;
            Notifications.setNotifier(bot);
            log.info("Telegram bot @{} is started", me.getUserName());
            Notifications.message("CombatsBot is online. Send " + Command.HELP.text + " for the list of commands.");
        } catch (Exception e) {
            log.error("Can't start Telegram bot: check the token", e);
        }
    }

    /**
     * Останавливает бота, если он запущен.
     */
    public static synchronized void stop() {
        Notifications.setNotifier(null);
        runningSettings = null;
        if (application == null)
            return;
        try {
            application.close();
        } catch (Exception e) {
            log.warn("Can't stop Telegram bot", e);
        }
        application = null;
    }

    /**
     * Telegram включён, только когда заданы и владелец, и токен: без владельца бот отказывал бы
     * во всех командах, без токена не может подключиться.
     */
    static boolean isEnabled(String token, String owner) {
        return trimToNull(token) != null && trimToNull(owner) != null;
    }

    private static String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

}
