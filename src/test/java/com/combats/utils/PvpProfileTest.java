package com.combats.utils;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Тесты профиля PvP ({@link PvpProfile}).
 */
class PvpProfileTest {

    @AfterEach
    void resetProfile() {
        Properties.setPvpProfile(PvpProfile.CHAOS);
    }

    @Test
    void formShowsChaosFirstAndThenLeague() {
        assertArrayEquals(new PvpProfile[]{PvpProfile.CHAOS, PvpProfile.LEAGUE}, PvpProfile.values());
        assertEquals("Хаоты", PvpProfile.CHAOS.toString());
        assertEquals("Лига", PvpProfile.LEAGUE.toString());
    }

    @Test
    void chaosIsTheDefaultProfile() {
        assertEquals(PvpProfile.CHAOS, Properties.getPvpProfile());
    }

    @Test
    void selectedProfileIsRemembered() {
        Properties.setPvpProfile(PvpProfile.LEAGUE);
        assertEquals(PvpProfile.LEAGUE, Properties.getPvpProfile());
    }

}
