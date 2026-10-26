package com.combats.utils;

import java.nio.charset.StandardCharsets;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.LoggingEvent;
import net.logstash.logback.encoder.LogstashEncoder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import static net.logstash.logback.argument.StructuredArguments.kv;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Тесты полей игры в MDC ({@link LogContext}) и того, как они попадают в JSON-лог.
 */
class LogContextTest {

    @AfterEach
    void clearMdc() {
        LogContext.clear();
    }

    @Test
    void startGameFillsFieldsAndResetsBattles() {
        LogContext.startGame("Amlett", true);
        String gameId = MDC.get(LogContext.GAME_ID);
        assertNotNull(gameId);
        assertEquals("Amlett", MDC.get(LogContext.LOGIN));
        assertEquals("PvP", MDC.get(LogContext.MODE));
        assertNull(MDC.get(LogContext.BATTLE));

        assertEquals(1, LogContext.nextBattle());
        assertEquals(2, LogContext.nextBattle());
        assertEquals("2", MDC.get(LogContext.BATTLE));

        LogContext.startGame("Amlett", false);
        assertNotEquals(gameId, MDC.get(LogContext.GAME_ID));
        assertEquals("Dungeon", MDC.get(LogContext.MODE));
        assertNull(MDC.get(LogContext.BATTLE));
        assertEquals(1, LogContext.nextBattle());
    }

    @Test
    void clearRemovesFields() {
        LogContext.startGame("Amlett", true);
        LogContext.clear();
        assertNull(MDC.get(LogContext.GAME_ID));
        assertNull(MDC.get(LogContext.LOGIN));
    }

    @Test
    void jsonRecordHasGameFieldsAndEventFields() {
        // настоящий контекст logback: у нового LoggerContext нет MDC
        LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
        LogstashEncoder encoder = new LogstashEncoder();
        encoder.setContext(context);
        encoder.start();
        Logger logger = context.getLogger("com.combats.pages.ChatPage");

        LogContext.startGame("Amlett", true);
        LogContext.nextBattle();
        LoggingEvent event = new LoggingEvent(Logger.class.getName(), logger, Level.INFO,
                "Battle result: {}, {}", null, new Object[]{kv("damage_hp", 1800L), kv("experience", 14476L)});
        String json = new String(encoder.encode(event), StandardCharsets.UTF_8);

        assertTrue(json.contains("\"message\":\"Battle result: damage_hp=1800, experience=14476\""), json);
        assertTrue(json.contains("\"damage_hp\":1800"), json);
        assertTrue(json.contains("\"experience\":14476"), json);
        assertTrue(json.contains("\"login\":\"Amlett\""), json);
        assertTrue(json.contains("\"mode\":\"PvP\""), json);
        assertTrue(json.contains("\"battle\":\"1\""), json);
        assertTrue(json.contains("\"level\":\"INFO\""), json);
    }

}
