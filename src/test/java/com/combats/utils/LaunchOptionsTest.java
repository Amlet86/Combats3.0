package com.combats.utils;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Тесты разбора параметров запуска ({@link LaunchOptions#parse}).
 */
class LaunchOptionsTest {

    @Test
    void noOptionsMeansForm() {
        LaunchOptions options = LaunchOptions.parse();
        assertFalse(options.server());
    }

    @Test
    void serverWithDefaults() {
        LaunchOptions options = LaunchOptions.parse("--server", "--login", "Amlett");
        assertTrue(options.server());
        assertEquals("Amlett", options.login());
        assertFalse(options.pet());
        assertFalse(options.dungeon());
        assertFalse(options.waitForPlay());
        assertEquals(0, options.endHours());
        assertEquals(0, options.endMinutes());
    }

    @Test
    void allServerOptions() {
        LaunchOptions options = LaunchOptions.parse("--server", "--login", "Amlett", "--pet", "--dungeon", "--end", "7:30", "--wait");
        assertTrue(options.pet());
        assertTrue(options.dungeon());
        assertTrue(options.waitForPlay());
        assertEquals(7, options.endHours());
        assertEquals(30, options.endMinutes());
    }

    @Test
    void wrongOptions() {
        assertThrows(IllegalArgumentException.class, () -> LaunchOptions.parse("--serve"));
        assertThrows(IllegalArgumentException.class, () -> LaunchOptions.parse("--server", "--login", "A", "--end"));
        assertThrows(IllegalArgumentException.class,
                () -> LaunchOptions.parse("--server", "--login", "A", "--end", "25:00"));
        assertThrows(IllegalArgumentException.class,
                () -> LaunchOptions.parse("--server", "--login", "A", "--end", "7.30"));
    }

    @Test
    void serverOptionsWithoutServer() {
        assertThrows(IllegalArgumentException.class, () -> LaunchOptions.parse("--pet"));
        assertThrows(IllegalArgumentException.class, () -> LaunchOptions.parse("--end", "23:00"));
        assertThrows(IllegalArgumentException.class, () -> LaunchOptions.parse("--login", "Amlett"));
    }

    @Test
    void serverNeedsLogin() {
        assertThrows(IllegalArgumentException.class, () -> LaunchOptions.parse("--server"));
        assertThrows(IllegalArgumentException.class, () -> LaunchOptions.parse("--server", "--login"));
        assertThrows(IllegalArgumentException.class, () -> LaunchOptions.parse("--server", "--login", "--pet"));
    }

}
