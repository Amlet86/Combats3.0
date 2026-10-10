package com.combats.telegram;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.telegram.telegrambots.ApiContextInitializer;
import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.meta.exceptions.TelegramApiRequestException;

/**
 * Запуск Telegram-бота. Сейчас нигде не вызывается.
 */
public class InitializerTelegramBot {

    private static final Logger log = LoggerFactory.getLogger(InitializerTelegramBot.class);

    /**
     * Направляет сетевые соединения JVM через локальный Tor (SOCKS 127.0.0.1:9150), когда Telegram был заблокирован.
     * Внимание: настройка глобальная и действует на все соединения, включая браузер.
     * Подробности: https://ru.stackoverflow.com/questions/839400/telegramapirequestexception-%D0%BF%D1%80%D0%B8-%D0%B7%D0%B0%D0%BF%D1%83%D1%81%D0%BA%D0%B5-telegram-%D0%B1%D0%BE%D1%82%D0%B0
     */
    private static void setVPNThroughTOR() {
        System.getProperties().put("proxySet", "true");
        System.getProperties().put("socksProxyHost", "127.0.0.1");
        System.getProperties().put("socksProxyPort", "9150");
    }

    /**
     * Регистрирует бота в Telegram и начинает получать сообщения.
     *
     * @return запущенный бот; если регистрация не удалась, бот возвращается, но сообщения получать не будет
     */
    public static Bot startTelegramBot() {
        setVPNThroughTOR();

        ApiContextInitializer.init();
        TelegramBotsApi telegramBotsApi = new TelegramBotsApi();
        Bot bot = new Bot();
        try {
            telegramBotsApi.registerBot(bot);
        } catch (TelegramApiRequestException e) {
            log.error("Can't register Telegram bot", e);
        }
        return bot;
    }

}
