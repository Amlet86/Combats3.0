package com.combats.pages;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Тесты разбора времени до начала боя со страницы заявок ({@link GoToBattlePage#minutesToStart}).
 * Текст страницы снят 16.10.2026.
 */
class MinutesToStartTest {

    private static final String PAGE = "Amlett [8] 1617/1617 Бои: 1 на 1 Учебные Групповые Хаотичные Текущие Конфликты "
            + "Завершенные Ожидаем начала группового боя Ваш бой начнется через 2.2 мин. Показывать заявки "
            + "Подходящие мне все 23:45 (Святой Дух [9]) (8-10) Бой начнется через 5.0 мин., таймаут 1 мин., "
            + "сила противников ±50% (Увеличение опыта: +50%, Закрытый) 23:42 (ЧУ-Ж-ОЙ [8], вопиющий ишак [9], "
            + "riseofchance [8], Necroz [8], Amlett [8]) (7-9) Бой начнется через 1.7 мин., таймаут 1 мин., "
            + "сила противников ±50% (Увеличение опыта: +50%, Закрытый)";

    @Test
    void timeIsTakenFromOwnApplication() {
        assertEquals("1.7", GoToBattlePage.minutesToStart(PAGE, "Amlett"));
    }

    @Test
    void otherApplicationsAreSkipped() {
        assertEquals("5.0", GoToBattlePage.minutesToStart(PAGE, "Святой Дух"));
    }

    @Test
    void withoutOwnApplicationTimeIsTakenFromHeader() {
        assertEquals("2.2", GoToBattlePage.minutesToStart(PAGE, "Someone"));
        assertEquals("2.2", GoToBattlePage.minutesToStart(PAGE, null));
    }

    @Test
    void waitingForOwnBattleIsRecognised() {
        assertTrue(GoToBattlePage.isWaitingForOwnBattle(PAGE));
        assertFalse(GoToBattlePage.isWaitingForOwnBattle("Подать заявку на хаотичный бой"));
        assertFalse(GoToBattlePage.isWaitingForOwnBattle(null));
    }

    @Test
    void pageWithoutTime() {
        assertNull(GoToBattlePage.minutesToStart("Подать заявку на хаотичный бой", "Amlett"));
        assertNull(GoToBattlePage.minutesToStart(null, "Amlett"));
    }

}
