package com.combats.telegram;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import static com.combats.utils.Properties.getTelegramBotName;
import static com.combats.utils.Properties.getTelegramBotToken;

/**
 * Telegram-бот для управления игрой из чата и получения итогов боёв.
 */
public class Bot extends TelegramLongPollingBot {

    private static final Logger log = LoggerFactory.getLogger(Bot.class);

    /**
     * Вызывается библиотекой на каждое входящее сообщение и отправляет ответ.
     */
    @Override
    public void onUpdateReceived(Update update) {
        ProcessingMessage processingMessage = new ProcessingMessage();
        SendMessage sendMessage = processingMessage.processing(update);
        sendMsg(sendMessage);
    }

    /**
     * Отправляет текст в указанный чат.
     */
    public synchronized void sendMsg(String chatId, String outputMessage) {
        SendMessage sendMessage = new SendMessage();
        sendMessage.enableMarkdown(true);
        sendMessage.setChatId(chatId);
        sendMessage.setText(outputMessage);
        sendMsg(sendMessage);
    }

    /**
     * Отправляет готовое сообщение; ошибку отправки только пишет в лог.
     */
    public synchronized void sendMsg(SendMessage sendMessage) {
        try {
            execute(sendMessage);
        } catch (TelegramApiException e) {
            log.error("Can't send a message to Telegram", e);
        }
    }

    /**
     * @return имя бота из формы
     */
    @Override
    public String getBotUsername() {
        return getTelegramBotName();
    }

    /**
     * @return токен бота из формы
     */
    @Override
    public String getBotToken() {
        return getTelegramBotToken();
    }

}
