package com.combats.telegram;

import java.io.ByteArrayInputStream;
import java.util.Arrays;
import java.util.Locale;
import java.util.function.Supplier;

import com.combats.utils.FileWorker;
import com.combats.utils.GameControl;
import com.combats.utils.Notifications;
import com.combats.utils.Properties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.telegram.telegrambots.longpolling.util.LongPollingSingleThreadUpdateConsumer;
import org.telegram.telegrambots.meta.api.methods.commands.SetMyCommands;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.send.SendPhoto;
import org.telegram.telegrambots.meta.api.objects.InputFile;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.User;
import org.telegram.telegrambots.meta.api.objects.commands.BotCommand;
import org.telegram.telegrambots.meta.api.objects.message.Message;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardRow;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.generics.TelegramClient;

import static com.combats.utils.Utils.takeScreenshot;

/**
 * Telegram-бот: принимает команды владельца и отправляет ему уведомления об игре.
 * <p>
 * Командами управляет только владелец (username из поля «Owner» формы), остальным бот отказывает.
 * Чат владельца запоминается при первом его сообщении и сохраняется в файл, поэтому уведомления
 * приходят и в игре, запущенной из формы.
 */
class TelegramBot implements LongPollingSingleThreadUpdateConsumer, Notifications.Notifier {

    private static final Logger log = LoggerFactory.getLogger(TelegramBot.class);

    /** Ограничения Telegram на длину текста сообщения и подписи к картинке. */
    private static final int MAX_MESSAGE_LENGTH = 4096;
    private static final int MAX_CAPTION_LENGTH = 1024;

    private static final ReplyKeyboardMarkup KEYBOARD = ReplyKeyboardMarkup.builder()
            .keyboardRow(new KeyboardRow(Command.PLAY.text, Command.STOP.text))
            .keyboardRow(new KeyboardRow(Command.STATUS.text, Command.SCREENSHOT.text))
            .resizeKeyboard(true)
            .build();

    private final TelegramClient client;
    private final String owner;
    private final Supplier<String> play;

    /**
     * @param client клиент для отправки сообщений
     * @param owner  username владельца, с «@» или без
     * @param play   запуск игры; возвращает ответ для пользователя
     */
    TelegramBot(TelegramClient client, String owner, Supplier<String> play) {
        this.client = client;
        this.owner = normalizeUsername(owner);
        this.play = play;
    }

    /**
     * Показывает команды в меню Telegram (кнопка «/» рядом с полем ввода).
     */
    void registerCommands() throws TelegramApiException {
        client.execute(new SetMyCommands(Arrays.stream(Command.values())
                .map(command -> new BotCommand(command.text.substring(1), command.description))
                .toList()));
    }

    /**
     * Обрабатывает входящее сообщение. Вызывается библиотекой в отдельном потоке по одному сообщению.
     */
    @Override
    public void consume(Update update) {
        if (!update.hasMessage() || !update.getMessage().hasText())
            return;
        Message message = update.getMessage();
        String chatId = message.getChatId().toString();
        if (!isOwner(message.getFrom())) {
            log.warn("Telegram message from a stranger @{} is ignored", username(message.getFrom()));
            send(chatId, "Access denied.");
            return;
        }
        rememberChat(chatId);
        Command command = Command.parse(message.getText());
        String reply = command == null
                ? "I don't understand you.\n" + Command.helpText()
                : execute(command);
        send(chatId, reply);
    }

    /**
     * Выполняет команду владельца.
     *
     * @return ответ для пользователя
     */
    private String execute(Command command) {
        return switch (command) {
            case PLAY -> play.get();
            case STOP -> {
                if (!GameControl.isRunning())
                    yield "No game is running.";
                GameControl.requestStop();
                yield "Stopping the game...";
            }
            case STATUS -> {
                if (!GameControl.isRunning())
                    yield "No game is running. Send " + Command.PLAY.text + " to start.";
                String status = GameControl.getLastStatus();
                yield "The game is running: " + (status == null ? "starting" : status);
            }
            case SCREENSHOT -> {
                if (!GameControl.isRunning())
                    yield "No game is running, the browser is closed.";
                // браузер доступен только из потока игры, поэтому скриншот снимает он
                GameControl.runInGameThread(this::sendScreenshot);
                yield "Taking a screenshot...";
            }
            case HELP -> Command.helpText();
        };
    }

    /**
     * Снимает и отправляет скриншот с последним статусом игры в подписи. Вызывается в потоке игры.
     */
    private void sendScreenshot() {
        String status = GameControl.getLastStatus();
        String caption = status == null ? "Screenshot" : status;
        byte[] png = takeScreenshot();
        if (png == null)
            message(caption + " (the browser isn't started yet)");
        else
            photo(caption, png);
    }

    /**
     * Запоминает чат владельца для уведомлений и сохраняет его в файл, если чат новый.
     */
    private void rememberChat(String chatId) {
        if (chatId.equals(Properties.getTelegramChatId()))
            return;
        Properties.setTelegramChatId(chatId);
        FileWorker.writeUserDataFile();
        log.info("Telegram chat of the owner is saved, notifications will be sent there");
    }

    private boolean isOwner(User user) {
        return owner != null && owner.equals(normalizeUsername(username(user)));
    }

    private static String username(User user) {
        return user == null ? null : user.getUserName();
    }

    /**
     * @return username без «@» в нижнем регистре или null, если он пустой
     */
    static String normalizeUsername(String username) {
        if (username == null)
            return null;
        String normalized = username.trim();
        if (normalized.startsWith("@"))
            normalized = normalized.substring(1);
        return normalized.isEmpty() ? null : normalized.toLowerCase(Locale.ROOT);
    }

    @Override
    public void message(String text) {
        String chatId = Properties.getTelegramChatId();
        if (chatId == null) {
            log.info("Telegram chat is unknown yet: write anything to the bot to get notifications");
            return;
        }
        send(chatId, text);
    }

    @Override
    public void photo(String caption, byte[] png) {
        String chatId = Properties.getTelegramChatId();
        if (chatId == null) {
            log.info("Telegram chat is unknown yet: write anything to the bot to get notifications");
            return;
        }
        SendPhoto sendPhoto = SendPhoto.builder()
                .chatId(chatId)
                .photo(new InputFile(new ByteArrayInputStream(png), "screenshot.png"))
                .caption(cut(caption, MAX_CAPTION_LENGTH))
                .build();
        // отправка асинхронная, чтобы медленная сеть не задерживала игру
        client.executeAsync(sendPhoto).whenComplete((sent, e) -> {
            if (e != null)
                log.error("Can't send a screenshot to Telegram", e);
        });
    }

    /**
     * Отправляет текст с клавиатурой команд. Ошибку отправки только пишет в лог.
     */
    private void send(String chatId, String text) {
        SendMessage sendMessage = SendMessage.builder()
                .chatId(chatId)
                .text(cut(text, MAX_MESSAGE_LENGTH))
                .replyMarkup(KEYBOARD)
                .build();
        try {
            client.executeAsync(sendMessage).whenComplete((sent, e) -> {
                if (e != null)
                    log.error("Can't send a message to Telegram", e);
            });
        } catch (TelegramApiException e) {
            log.error("Can't send a message to Telegram", e);
        }
    }

    private static String cut(String text, int maxLength) {
        return text.length() <= maxLength ? text : text.substring(0, maxLength - 1) + "…";
    }

}
