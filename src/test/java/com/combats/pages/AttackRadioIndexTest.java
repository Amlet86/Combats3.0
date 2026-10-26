package com.combats.pages;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Тесты раскладки кнопок удара ({@link BattlePage#attackRadioIndex}).
 * Названия тестов описывают проверяемый случай.
 */
class AttackRadioIndexTest {

    @Test
    void oneStrikeUsesRowIndex() {
        assertEquals(3, BattlePage.attackRadioIndex(3, 0, 1));
    }

    @Test
    void twoStrikesFirstStrikeIsLeftColumn() {
        assertEquals(0, BattlePage.attackRadioIndex(0, 0, 2));
        assertEquals(4, BattlePage.attackRadioIndex(2, 0, 2));
    }

    @Test
    void twoStrikesSecondStrikeIsRightColumn() {
        assertEquals(1, BattlePage.attackRadioIndex(0, 1, 2));
        assertEquals(9, BattlePage.attackRadioIndex(4, 1, 2));
    }
}
