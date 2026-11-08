package com.combats.pages;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Тесты ответа игры на открытие объекта в подземелье ({@link DungeonsPage#tryFromAnotherCell}).
 * Тексты сняты 07–08.11.2026 на 2-м этаже Грибницы.
 */
class ObjectAnswerTest {

    @Test
    void objectBehindWallIsTriedAgain() {
        assertTrue(DungeonsPage.tryFromAnotherCell("Недостижимая цель"));
    }

    @Test
    void objectNearMonstersIsTriedAgain() {
        assertTrue(DungeonsPage.tryFromAnotherCell(
                "Вы опасаетесь покопаться в нише, пока окрестности наводнены опасными чудовищами."));
    }

    @Test
    void openedObjectIsNotTriedAgain() {
        assertFalse(DungeonsPage.tryFromAnotherCell("Вы обнаружили 'Скользкий ключ' Вы обнаружили 'Скользкий ключ'"));
    }
}
