package com.combats.utils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Тесты файла с данными пользователя ({@link FileWorker}). Пишут во временную папку, не в ~/.combats.
 */
class FileWorkerTest {

    @TempDir
    Path tempDir;

    @AfterEach
    void clearProperties() {
        setAll(null, null, null, null, null);
    }

    @Test
    void savedSettingsAreReadBack() {
        Path file = tempDir.resolve("sub/user-data.txt");
        setAll("login", "pass word", "owner", "123:token", "42");
        FileWorker.writeUserDataFile(file);

        clearProperties();
        FileWorker.readUserDataFile(file);

        assertEquals("login", Properties.getUserLogin());
        assertEquals("pass word", Properties.getUserPassword());
        assertEquals("owner", Properties.getTelegramOwner());
        assertEquals("123:token", Properties.getTelegramBotToken());
        assertEquals("42", Properties.getTelegramChatId());
    }

    @Test
    void emptySettingsAreReadAsNull() {
        Path file = tempDir.resolve("user-data.txt");
        setAll("login", "pass", null, null, null);
        FileWorker.writeUserDataFile(file);

        FileWorker.readUserDataFile(file);

        assertEquals("login", Properties.getUserLogin());
        assertNull(Properties.getTelegramOwner());
        assertNull(Properties.getTelegramChatId());
    }

    @Test
    void oldFileWithBotNameKeepsLoginAndTokenButNotOwner() throws IOException {
        Path file = tempDir.resolve("user-data.txt");
        Files.write(file, List.of("login", "pass", "MyCombatsBot", "123:token"));

        FileWorker.readUserDataFile(file);

        assertEquals("login", Properties.getUserLogin());
        assertEquals("pass", Properties.getUserPassword());
        assertNull(Properties.getTelegramOwner());
        assertEquals("123:token", Properties.getTelegramBotToken());
    }

    @Test
    void oldFileWithProxySkipsProxyAndKeepsChatId() throws IOException {
        Path file = tempDir.resolve("user-data.txt");
        Files.write(file, List.of("login", "pass", "owner", "123:token", "socks5://127.0.0.1:1080", "42"));

        FileWorker.readUserDataFile(file);

        assertEquals("owner", Properties.getTelegramOwner());
        assertEquals("123:token", Properties.getTelegramBotToken());
        assertEquals("42", Properties.getTelegramChatId());
    }

    @Test
    void missingFileChangesNothing() {
        setAll("login", null, null, null, null);
        FileWorker.readUserDataFile(tempDir.resolve("absent.txt"));
        assertEquals("login", Properties.getUserLogin());
    }

    private static void setAll(String login, String password, String owner, String token, String chatId) {
        Properties.setUserLogin(login);
        Properties.setUserPassword(password);
        Properties.setTelegramOwner(owner);
        Properties.setTelegramBotToken(token);
        Properties.setTelegramChatId(chatId);
    }

}
