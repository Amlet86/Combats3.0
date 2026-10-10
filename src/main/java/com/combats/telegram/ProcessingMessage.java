package com.combats.telegram;

import java.util.Date;

import com.combats.be.TopLevelLogic;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;

import static com.combats.utils.Properties.setTelegramChatId;

/**
 * Обработка входящих сообщений Telegram-бота: выбирает ответ на команду.
 */
public class ProcessingMessage extends AbstractMessage {

    /**
     * Запускает игру и ждёт её окончания.
     *
     * @return сообщение о том, почему игра закончилась
     */
    private String startBrowserAndGame() {
        TopLevelLogic topLevelLogic = new TopLevelLogic();
        return topLevelLogic.game();
    }

    /**
     * Отвечает на текстовую команду с клавиатуры бота.
     */
    private SendMessage sendText(Update update) {
        SendMessage sendMessage = prepareMessage(update);
        String question = update.getMessage().getText();
        if (question.contains("What time is it?"))
            sendMessage.setText(new Date().toString());
        else if (question.contains("Who are you?"))
            sendMessage.setText("Just a dumb piece of iron.");
        else if (question.contains("Start the game.")) {
            setTelegramChatId(update.getMessage().getChatId().toString());
            String endGameMessage = startBrowserAndGame();
            sendMessage.setText(endGameMessage);
        } else if (question.contains("Give me a screenshot.")) {
            sendMessage.setText("Mock");
        } else
            sendMessage.setText("I don't understand you.");
        return sendMessage;
    }

    /**
     * Точка входа обработки: на «menu» показывает клавиатуру, иначе отвечает на команду.
     */
    public SendMessage processing(Update update) {
        String inputMessage = update.getMessage().getText();
        if (inputMessage.contains("menu") || inputMessage.contains("Menu"))
            return new Keyboard().sendKeyboard(update);
        else
            return sendText(update);
    }

}
