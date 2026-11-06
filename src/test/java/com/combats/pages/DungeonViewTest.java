package com.combats.pages;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Тесты разбора страницы подземелья ({@link DungeonView}) и строки с риском ({@link DungeonRisk}).
 * Ссылки и тексты сняты в Грибнице 03.11.2026. Названия тестов описывают проверяемый случай.
 */
class DungeonViewTest {

    @Test
    void charactersForTalkAreNotMonsters() {
        assertTrue(DungeonView.isPeaceful("Мастер Фунгус"));
        assertTrue(DungeonView.isPeaceful("Мудрый Гусениц (2)"));
        assertFalse(DungeonView.isPeaceful("Мясистый Гусениц"));
        assertFalse(DungeonView.isPeaceful("Неприкаянная душа"));
    }

    private static final String TEXT = "Выйти Грибница - Этаж 1 - Логово Amlett [10] 2967/2967 "
            + "В комнате разбросаны вещи: Подобрать все смотрим на запад Задание: Забияка ... 0/1";

    private static Map<String, Object> raw(String facingCode, String text, List<String> links) {
        return Map.of("path", "/dungeon2.pl", "room", "Грибница - Этаж 1 - Логово", "facingCode", facingCode,
                "text", text, "map", "d15.gif", "message", "", "hp", "2967/2967", "links", links);
    }

    @Test
    void linksAndPlaceAreParsed() {
        DungeonView view = DungeonView.from(raw("7", TEXT, List.of(
                "javascript:dung_link('path=m1','m1')\tГрибница - Этаж 1 - Грибной коридор",
                "javascript:dung_link('path=rr')\tповернуться направо",
                "javascript:dung_link('path=m5','m5')\tГрибница - Этаж 1 - Логово",
                "javascript:dung_link('path=rl')\tповернуться налево",
                "javascript:dung_link('')\t",
                "javascript:dung_link('getall=l')\tПодобрать все",
                "javascript:dung_link('useobj=358554083')\tЗаросший тайник")));
        assertTrue(view.inDungeon());
        assertEquals(Direction.WEST, view.facing());
        assertEquals(Set.of("m1", "rr", "m5", "rl"), view.pathLinks());
        assertTrue(view.hasLoot());
        assertEquals(List.of(new DungeonView.Link("useobj=358554083", "Заросший тайник")), view.objects());
        assertEquals(1, view.floor());
        assertEquals("2967/2967", view.hp());
        assertFalse(view.moving());
    }

    @Test
    void facingIsTakenFromTheTextWhenTheMapMarkIsMissing() {
        DungeonView view = DungeonView.from(raw("", TEXT, List.of()));
        assertEquals(Direction.WEST, view.facing());
    }

    @Test
    void pendingStepIsSeen() {
        DungeonView view = DungeonView.from(raw("1",
                "Выйти Грибница - Этаж 1 - Грибной коридор Amlett [10] Вы перейдете вперед (отмена) смотрим на север",
                List.of()));
        assertTrue(view.moving());
    }

    @Test
    void notADungLinkIsSkipped() {
        assertNull(DungeonView.parseLink("/clans_inf.pl?CROCO\tCROCO"));
        assertNull(DungeonView.parseLink("javascript:dung_link('')\t"));
        assertEquals(new DungeonView.Link("path=m3", "Грибница - Этаж 1 - Грибной коридор"),
                DungeonView.parseLink("javascript:dung_link('path=m3','m3')\tГрибница - Этаж 1 - Грибной коридор"));
    }

    @Test
    void anyNumberedDungeonPageIsTheMap() {
        // 03.11.2026 карта была и dungeon2.pl, и dungeon0.pl; dungeon.pl — страница входа
        assertTrue(DungeonView.isMapPage("/dungeon0.pl"));
        assertTrue(DungeonView.isMapPage("/dungeon2.pl"));
        assertFalse(DungeonView.isMapPage("/dungeon.pl"));
        assertFalse(DungeonView.isMapPage("/main.pl"));
        assertFalse(DungeonView.isMapPage(null));
    }

    @Test
    void emptyScriptAnswerIsNotADungeon() {
        DungeonView view = DungeonView.from(Map.of());
        assertFalse(view.inDungeon());
        assertNull(view.facing());
        assertTrue(view.pathLinks().isEmpty());
    }

    @Test
    void riskIsParsed() {
        DungeonRisk risk = DungeonRisk.parse("Грибница Стоимость похода: 40 единиц риска (доступно 83.52 единиц) "
                + "21:26 () Amlett [10] Группа").orElseThrow();
        assertEquals(40, risk.cost());
        assertEquals(83.52, risk.available());
        assertTrue(risk.enough());
    }

    @Test
    void notEnoughRiskIsSeen() {
        assertFalse(DungeonRisk.parse("Стоимость похода: 40 единиц риска (доступно 3.66 единиц)").orElseThrow().enough());
    }

    @Test
    void pageWithoutRiskGivesNothing() {
        assertTrue(DungeonRisk.parse("Текущие квесты [1] Забияка").isEmpty());
        assertTrue(DungeonRisk.parse(null).isEmpty());
    }

}
