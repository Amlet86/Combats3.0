package com.combats.telegram;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

import com.combats.utils.GameControl;
import com.combats.utils.GameStats;
import com.combats.utils.Properties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.User;
import org.telegram.telegrambots.meta.api.objects.chat.Chat;
import org.telegram.telegrambots.meta.api.objects.message.Message;
import org.telegram.telegrambots.meta.generics.TelegramClient;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Тесты обработки сообщений Telegram-ботом ({@link TelegramBot}) без сети:
 * вместо клиента Telegram подставлен объект, который запоминает отправленные сообщения.
 * <p>
 * Чат владельца заранее записан в настройки, чтобы бот не сохранял его в настоящий ~/.combats.
 */
class TelegramBotTest {

    private static final long OWNER_CHAT = 42;

    private final List<Object> sent = new ArrayList<>();
    private final AtomicInteger playCalls = new AtomicInteger();
    private TelegramBot bot;

    @BeforeEach
    void setUp() {
        TelegramClient client = (TelegramClient) Proxy.newProxyInstance(
                TelegramClient.class.getClassLoader(), new Class<?>[]{TelegramClient.class},
                (proxy, method, args) -> {
                    sent.add(args[0]);
                    return method.getName().equals("executeAsync") ? CompletableFuture.completedFuture(null) : null;
                });
        bot = new TelegramBot(client, "@Amlett", () -> {
            playCalls.incrementAndGet();
            return "Starting the game...";
        });
        Properties.setTelegramChatId(String.valueOf(OWNER_CHAT));
    }

    @AfterEach
    void tearDown() {
        Properties.setTelegramChatId(null);
        GameControl.prepare(null);
    }

    @Test
    void strangerIsRefusedAndCannotStartTheGame() {
        bot.consume(update("someone", 7, "/play"));

        assertEquals(0, playCalls.get());
        assertEquals("Access denied.", lastText());
        assertEquals("7", lastMessage().getChatId());
    }

    @Test
    void ownerIsRecognizedWithoutAtSignAndCase() {
        bot.consume(update("amlett", OWNER_CHAT, "/play"));

        assertEquals(1, playCalls.get());
        assertEquals("Starting the game...", lastText());
    }

    @Test
    void userWithoutUsernameIsRefused() {
        bot.consume(update(null, 8, "/status"));
        assertEquals("Access denied.", lastText());
    }

    @Test
    void statusWithoutGame() {
        bot.consume(update("Amlett", OWNER_CHAT, "/status"));
        assertTrue(lastText().startsWith("No game is running."), lastText());
    }

    @Test
    void statusDuringGameShowsLastStatus() {
        GameControl.prepare(new Thread(() -> { }));
        GameControl.status("In battle");

        bot.consume(update("Amlett", OWNER_CHAT, "/status"));

        assertEquals("The game is running: In battle", lastText());
    }

    @Test
    void stopRequestsGameStop() {
        GameControl.prepare(new Thread(() -> { }));

        bot.consume(update("Amlett", OWNER_CHAT, "/stop"));

        assertTrue(GameControl.isStopRequested());
        assertEquals("Stopping the game...", lastText());
    }

    @Test
    void stopWithoutGame() {
        bot.consume(update("Amlett", OWNER_CHAT, "/stop"));
        assertFalse(GameControl.isStopRequested());
        assertEquals("No game is running.", lastText());
    }

    @Test
    void screenshotIsTakenByTheGameThread() {
        GameControl.prepare(new Thread(() -> { }));

        bot.consume(update("Amlett", OWNER_CHAT, "/screenshot"));
        assertEquals("Taking a screenshot...", lastText());
        int sentBefore = sent.size();

        // поток игры выполняет задачу; браузера в тесте нет, поэтому уходит только подпись
        GameControl.checkStop();

        assertEquals(sentBefore + 1, sent.size());
        assertEquals("Screenshot (the browser isn't started yet)", lastText());
    }

    @Test
    void statsShowBattlesOfTheGame() {
        GameStats.start();
        GameStats.battle(3802L, 23364L);

        bot.consume(update("Amlett", OWNER_CHAT, "/stats"));

        assertTrue(lastText().startsWith("The game is running for"), lastText());
        assertTrue(lastText().contains("Battles: 1"), lastText());
        assertTrue(lastText().contains("Damage: 3,802 HP"), lastText());
    }

    @Test
    void unknownTextGetsHelp() {
        bot.consume(update("Amlett", OWNER_CHAT, "hello"));
        assertTrue(lastText().startsWith("I don't understand you."));
        assertTrue(lastText().contains("/play"));
    }

    @Test
    void notificationGoesToOwnerChat() {
        bot.message("Battle is over");
        assertEquals("Battle is over", lastText());
        assertEquals(String.valueOf(OWNER_CHAT), lastMessage().getChatId());
    }

    @Test
    void notificationIsSkippedWhileOwnerChatIsUnknown() {
        Properties.setTelegramChatId(null);
        bot.message("Battle is over");
        assertTrue(sent.isEmpty());
    }

    @Test
    void longNotificationIsCutToTelegramLimit() {
        bot.message("x".repeat(5000));
        assertEquals(4096, lastText().length());
    }

    @Test
    void usernameNormalization() {
        assertEquals("amlett", TelegramBot.normalizeUsername(" @Amlett "));
        assertNull(TelegramBot.normalizeUsername("@"));
        assertNull(TelegramBot.normalizeUsername(null));
    }

    private SendMessage lastMessage() {
        assertFalse(sent.isEmpty(), "nothing was sent");
        return (SendMessage) sent.get(sent.size() - 1);
    }

    private String lastText() {
        return lastMessage().getText();
    }

    private static Update update(String username, long chatId, String text) {
        User user = User.builder().id(chatId).firstName("Test").isBot(false).userName(username).build();
        Chat chat = Chat.builder().id(chatId).type("private").build();
        Message message = new Message();
        message.setFrom(user);
        message.setChat(chat);
        message.setText(text);
        Update update = new Update();
        update.setMessage(message);
        return update;
    }

}
