package com.combats.utils;

import java.util.Map;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Тесты чтения пароля и Telegram из окружения ({@link ServerSecrets#fromEnvironment(Map)}).
 */
class ServerSecretsTest {

    @Test
    void allValuesFromEnvironment() {
        ServerSecrets secrets = ServerSecrets.fromEnvironment(Map.of(
                ServerSecrets.PASSWORD, "secret",
                ServerSecrets.TELEGRAM_OWNER, "@owner",
                ServerSecrets.TELEGRAM_TOKEN, " 123:abc ",
                ServerSecrets.TELEGRAM_CHAT_ID, "42"));
        assertEquals("secret", secrets.password());
        assertEquals("@owner", secrets.telegramOwner());
        assertEquals("123:abc", secrets.telegramToken());
        assertEquals("42", secrets.telegramChatId());
    }

    @Test
    void missingAndBlankValuesAreNull() {
        ServerSecrets secrets = ServerSecrets.fromEnvironment(Map.of(ServerSecrets.TELEGRAM_TOKEN, "  "));
        assertNull(secrets.password());
        assertNull(secrets.telegramOwner());
        assertNull(secrets.telegramToken());
        assertNull(secrets.telegramChatId());
    }

    @Test
    void toStringHidesSecrets() {
        String text = new ServerSecrets("secret", "owner", "123:abc", "42").toString();
        assertFalse(text.contains("secret"));
        assertFalse(text.contains("123:abc"));
    }

}
