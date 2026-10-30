package com.combats.pages;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Тесты разбора и выбора чужих заявок на хаотичный бой ({@link ChaosApplication}).
 * Тексты заявок сняты со страницы 16.10.2026 и 10.10.2026.
 * Названия тестов описывают проверяемый случай.
 */
class ChaosApplicationTest {

    private static final String SOLO = " 23:45 (Святой Дух [9]) (8-10) Бой начнется через 5.0 мин., таймаут 1 мин., "
            + "сила противников ±50% (Увеличение опыта: +50%, Закрытый) ";
    private static final String FIVE_PLAYERS = " 23:42 (ЧУ-Ж-ОЙ [8], вопиющий ишак [9], riseofchance [8], Necroz [8], "
            + "Петров [8]) (7-9) Бой начнется через 1.7 мин., таймаут 1 мин., сила противников ±50% ";

    @Test
    void applicationIsParsed() {
        ChaosApplication application = ChaosApplication.parse("1791664273", FIVE_PLAYERS).orElseThrow();
        assertEquals("1791664273", application.radioValue());
        assertEquals(5, application.players());
        assertEquals(7, application.minLevel());
        assertEquals(9, application.maxLevel());
        assertEquals(1.7, application.minutes());
    }

    @Test
    void onlyTheFirstApplicationAfterTheRadioIsTaken() {
        // у следующей заявки нет радиокнопки (не подходит уровень), её текст попадает в ту же строку
        ChaosApplication application = ChaosApplication.parse("1", SOLO + FIVE_PLAYERS).orElseThrow();
        assertEquals(1, application.players());
        assertEquals(5.0, application.minutes());
    }

    @Test
    void scriptTextBeforePlayersIsIgnored() {
        // так текст заявки читался 28.10.2026: скобки скрипта ломали разбор, и бот подавал свою заявку
        String text = " 13:53 (drwfl(\"Alkatas\",1783992227,\"9\",0,\"Incognito\")Alkatas [9], "
                + "drwfl(\"ibavone afym\",1637753768,\"10\",0,\"\")ibavone afym [10], "
                + "drwfl(\"Сырколбас\",1325564309,\"10\",12,\"Siberia\")Сырколбас [10]) (8-10) "
                + "Бой начнется через 4.5 мин., таймаут 1 мин., сила противников ±50% ";
        ChaosApplication application = ChaosApplication.parse("1793184808", text).orElseThrow();
        assertEquals(3, application.players());
        assertEquals(8, application.minLevel());
        assertEquals(10, application.maxLevel());
        assertEquals(4.5, application.minutes());
    }

    @Test
    void textWithoutApplicationIsSkipped() {
        assertTrue(ChaosApplication.parse("1", "Принять участие в мясорубке").isEmpty());
        assertTrue(ChaosApplication.parse("1", null).isEmpty());
        assertTrue(ChaosApplication.parse(null, SOLO).isEmpty());
    }

    @Test
    void ownLevelIsReadFromThePageHeader() {
        assertEquals(9, ChaosApplication.ownLevel("Amlett [9] 1983/2092 Бои: 1 на 1", "Amlett"));
        assertNull(ChaosApplication.ownLevel("Someone [9] 1983/2092", "Amlett"));
        assertNull(ChaosApplication.ownLevel("Amlett [9]", null));
    }

    @Test
    void applicationWithMorePlayersIsChosen() {
        List<ChaosApplication> applications = List.of(
                new ChaosApplication("solo", 1, 8, 10, 0.5),
                new ChaosApplication("five", 5, 7, 9, 3.0));
        assertEquals("five", choose(applications, 9).radioValue());
    }

    @Test
    void withEqualPlayersTheSoonestIsChosen() {
        List<ChaosApplication> applications = List.of(
                new ChaosApplication("later", 2, 8, 10, 4.0),
                new ChaosApplication("sooner", 2, 8, 10, 1.0));
        assertEquals("sooner", choose(applications, 9).radioValue());
    }

    @Test
    void applicationForOtherLevelsIsNotChosen() {
        List<ChaosApplication> applications = List.of(new ChaosApplication("low", 5, 2, 4, 3.0));
        assertTrue(ChaosApplication.choose(applications, 9, Set.of()).isEmpty());
    }

    @Test
    void unknownLevelDoesNotBlockTheChoice() {
        List<ChaosApplication> applications = List.of(new ChaosApplication("low", 5, 2, 4, 3.0));
        assertEquals("low", ChaosApplication.choose(applications, null, Set.of()).orElseThrow().radioValue());
    }

    @Test
    void applicationAboutToStartIsNotChosen() {
        List<ChaosApplication> applications = List.of(new ChaosApplication("now", 9, 8, 10, 0.2));
        assertTrue(ChaosApplication.choose(applications, 9, Set.of()).isEmpty());
    }

    @Test
    void rejectedApplicationIsSkippedForTheNextOne() {
        List<ChaosApplication> applications = List.of(
                new ChaosApplication("full", 9, 8, 10, 2.0),
                new ChaosApplication("other", 3, 8, 10, 2.0));
        assertEquals("other", ChaosApplication.choose(applications, 9, Set.of("full")).orElseThrow().radioValue());
        assertTrue(ChaosApplication.choose(applications, 9, Set.of("full", "other")).isEmpty());
    }

    @Test
    void applicationLongerThanFiveMinutesIsNotChosen() {
        // долгое ожидание тормозит фарм опыта: лучше подать свою заявку, она начнётся через 5 минут
        List<ChaosApplication> applications = List.of(new ChaosApplication("long", 9, 8, 10, 27.0));
        assertTrue(ChaosApplication.choose(applications, 9, Set.of()).isEmpty());
    }

    @Test
    void applicationInFiveMinutesIsChosenOverLongerOne() {
        List<ChaosApplication> applications = List.of(
                new ChaosApplication("long", 9, 8, 10, 5.1),
                new ChaosApplication("five", 1, 8, 10, 5.0));
        assertEquals("five", choose(applications, 9).radioValue());
    }

    private static ChaosApplication choose(List<ChaosApplication> applications, Integer ownLevel) {
        Optional<ChaosApplication> chosen = ChaosApplication.choose(applications, ownLevel, Set.of());
        return chosen.orElseThrow();
    }

}
