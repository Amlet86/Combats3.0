package com.combats.utils;

import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Тесты расчёта времени окончания игры ({@link Properties#calculateEndTime})
 * и проверки, можно ли продолжать игру ({@link Utils#hasGameTime(LocalDateTime, LocalDateTime)}).
 * Названия тестов описывают проверяемый случай.
 */
class GameTimeTest {

    /** Фиксированное «текущее время» для всех тестов: 22:00. */
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 10, 22, 0);

    @Test
    void midnightMeansNoTimeLimit() {
        assertNull(Properties.calculateEndTime(0, 0, NOW));
        assertTrue(Utils.hasGameTime(null, NOW));
    }

    @Test
    void endTimeLaterToday() {
        assertEquals(LocalDateTime.of(2026, 10, 10, 23, 30), Properties.calculateEndTime(23, 30, NOW));
    }

    @Test
    void endTimeWithZeroMinutesIsNotUnlimited() {
        assertEquals(LocalDateTime.of(2026, 10, 10, 23, 0), Properties.calculateEndTime(23, 0, NOW));
    }

    @Test
    void endTimeWithZeroHoursIsNotUnlimited() {
        assertEquals(LocalDateTime.of(2026, 10, 11, 0, 30), Properties.calculateEndTime(0, 30, NOW));
    }

    @Test
    void endTimeAfterMidnightMovesToTomorrow() {
        assertEquals(LocalDateTime.of(2026, 10, 11, 1, 30), Properties.calculateEndTime(1, 30, NOW));
    }

    @Test
    void endTimeEqualToNowMovesToTomorrow() {
        assertEquals(LocalDateTime.of(2026, 10, 11, 22, 0), Properties.calculateEndTime(22, 0, NOW));
    }

    @Test
    void gameContinuesBeforeEndTime() {
        LocalDateTime end = LocalDateTime.of(2026, 10, 11, 1, 30);
        assertTrue(Utils.hasGameTime(end, NOW));
        assertTrue(Utils.hasGameTime(end, end.minusMinutes(1)));
    }

    @Test
    void gameStopsAtEndTime() {
        LocalDateTime end = LocalDateTime.of(2026, 10, 11, 1, 30);
        assertFalse(Utils.hasGameTime(end, end));
        assertFalse(Utils.hasGameTime(end, end.plusMinutes(1)));
    }

}
