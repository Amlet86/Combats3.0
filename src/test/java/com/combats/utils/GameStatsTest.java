package com.combats.utils;

import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Тесты статистики игры для /stats ({@link GameStats}). Время передаётся явно, чтобы тексты
 * не зависели от того, когда запущен тест. Цифры боёв взяты из живого лога 24.10.2026.
 * Названия тестов описывают проверяемый случай.
 */
class GameStatsTest {

    private static final Instant START = Instant.parse("2026-10-24T09:00:00Z");

    @AfterEach
    void clear() {
        GameStats.clear();
    }

    @Test
    void beforeTheFirstGameThereAreNoStats() {
        GameStats.clear();
        assertEquals("No game has been played yet.", GameStats.summary(START));
    }

    @Test
    void newGameWithoutBattles() {
        GameStats.start(START);
        String summary = GameStats.summary(START.plus(Duration.ofMinutes(7)));
        assertTrue(summary.startsWith("The game is running for 7 min."), summary);
        assertTrue(summary.contains("Battles: 0"), summary);
        assertFalse(summary.contains("Damage"), summary);
    }

    @Test
    void battlesAreSummedUp() {
        GameStats.start(START);
        GameStats.battle(3802L, 23364L);
        GameStats.battle(4400L, 17757L);
        String summary = GameStats.summary(START.plus(Duration.ofMinutes(30)));
        assertTrue(summary.contains("Battles: 2"), summary);
        assertTrue(summary.contains("Damage: 8,202 HP (average 4,101, best 4,400)"), summary);
        assertTrue(summary.contains("Experience: 41,121 (average 20,560)"), summary);
        assertFalse(summary.contains("per hour"), "less than an hour of play: " + summary);
    }

    @Test
    void experiencePerHourIsShownAfterAnHour() {
        GameStats.start(START);
        GameStats.battle(4000L, 30000L);
        String summary = GameStats.summary(START.plus(Duration.ofMinutes(90)));
        assertTrue(summary.contains("Experience per hour: 20,000"), summary);
    }

    @Test
    void battleWithoutNumbersIsCountedButAddsNothing() {
        GameStats.start(START);
        GameStats.battle(1000L, null);
        GameStats.battle(null, null);
        String summary = GameStats.summary(START);
        assertTrue(summary.contains("Battles: 2"), summary);
        assertTrue(summary.contains("Damage: 1,000 HP"), summary);
        assertTrue(summary.contains("Experience: 0"), summary);
    }

    @Test
    void errorsAndRecoveriesAreCounted() {
        GameStats.start(START);
        GameStats.error();
        GameStats.recovery();
        GameStats.recovery();
        assertTrue(GameStats.summary(START).contains("Errors: 1, recovered (log in again, site outage, browser restart): 2"));
    }

    @Test
    void finishedGameKeepsItsDuration() {
        GameStats.start(START);
        GameStats.finish(START.plus(Duration.ofMinutes(125)));
        String summary = GameStats.summary(START.plus(Duration.ofHours(10)));
        assertTrue(summary.startsWith("The last game lasted 2 h 05 min."), summary);
    }

    @Test
    void newGameResetsTheStats() {
        GameStats.start(START);
        GameStats.battle(3802L, 23364L);
        GameStats.error();
        GameStats.start(START.plus(Duration.ofHours(1)));
        String summary = GameStats.summary(START.plus(Duration.ofHours(1)));
        assertTrue(summary.contains("Battles: 0"), summary);
        assertTrue(summary.contains("Errors: 0"), summary);
    }

}
