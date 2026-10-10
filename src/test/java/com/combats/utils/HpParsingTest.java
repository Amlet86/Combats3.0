package com.combats.utils;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Тесты разбора HP со страницы в проценты ({@link Utils#parseHpPercent}).
 * Названия тестов описывают проверяемый случай.
 */
class HpParsingTest {

    @Test
    void fullHp() {
        assertEquals(100, Utils.parseHpPercent("131/131"));
    }

    @Test
    void partialHpIsRoundedDown() {
        assertEquals(49, Utils.parseHpPercent("65/131"));
    }

    @Test
    void zeroHp() {
        assertEquals(0, Utils.parseHpPercent("0/131"));
    }

    @Test
    void spacesAndSurroundingTextAreIgnored() {
        assertEquals(50, Utils.parseHpPercent(" HP: 100 / 200 "));
    }

    @Test
    void notHpText() {
        assertNull(Utils.parseHpPercent(""));
        assertNull(Utils.parseHpPercent(null));
        assertNull(Utils.parseHpPercent("Amlett [2]"));
        assertNull(Utils.parseHpPercent("10/0"));
    }

}
