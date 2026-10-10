package com.combats.telegram;

import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;

/**
 * Базовый класс для ответов Telegram-бота.
 */
public class AbstractMessage {

    /**
     * Создаёт ответ в тот же чат, из которого пришло сообщение.
     */
    SendMessage prepareMessage(Update update) {
        SendMessage sendMessage = new SendMessage();
        sendMessage.enableMarkdown(true);
        sendMessage.setChatId(update.getMessage().getChatId());
        return sendMessage;
    }
}
