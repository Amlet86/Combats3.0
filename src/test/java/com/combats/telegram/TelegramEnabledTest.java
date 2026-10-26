package com.combats.telegram;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Тесты условия включения Telegram ({@link TelegramService#isEnabled}): нужны и владелец, и токен.
 */
class TelegramEnabledTest {

    @Test
    void ownerAndTokenAreSet() {
        assertTrue(TelegramService.isEnabled("123:token", "owner"));
    }

    @Test
    void emptyFormMeansNoTelegram() {
        assertFalse(TelegramService.isEnabled(null, null));
        assertFalse(TelegramService.isEnabled("", ""));
    }

    @Test
    void onlyOneFieldIsNotEnough() {
        assertFalse(TelegramService.isEnabled("123:token", null));
        assertFalse(TelegramService.isEnabled(null, "owner"));
        assertFalse(TelegramService.isEnabled("123:token", "   "));
    }

}
