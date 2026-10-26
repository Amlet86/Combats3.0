package com.combats.pages;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Тесты признака начала боя по адресу основного фрейма ({@link GoToBattlePage#isBattlePage}).
 * Адреса сняты вживую: в бою фрейм открывает battle.pl … battle4.pl.
 */
class BattlePagePathTest {

    @Test
    void battlePages() {
        assertTrue(GoToBattlePage.isBattlePage("/battle.pl"));
        assertTrue(GoToBattlePage.isBattlePage("/battle3.pl"));
        assertTrue(GoToBattlePage.isBattlePage("/battle4.pl"));
    }

    @Test
    void otherPagesAreNotBattle() {
        assertFalse(GoToBattlePage.isBattlePage("/zayavka.pl"));
        assertFalse(GoToBattlePage.isBattlePage("/main.pl"));
        assertFalse(GoToBattlePage.isBattlePage(""));
        assertFalse(GoToBattlePage.isBattlePage(null));
    }

}
