package com.combats.pages;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import static com.combats.pages.Direction.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Тесты маршрута по сетке карты ({@link DungeonRoute}). Названия тестов описывают проверяемый случай.
 */
class DungeonRouteTest {

    private static List<Direction> walk(DungeonRoute route) {
        List<Direction> sides = new ArrayList<>();
        for (Optional<Direction> side = route.next(); side.isPresent(); side = route.next()) {
            sides.add(side.get());
            route.moved();
        }
        return sides;
    }

    @Test
    void cellsAreTurnedIntoSides() {
        // столбцы растут на восток, строки на юг
        assertEquals(List.of(EAST, NORTH, WEST, SOUTH), walk(DungeonRoute.parse("H10 I10 I9 H9 H10")));
    }

    @Test
    void commentsAndLineBreaksAreSkipped() {
        assertEquals(List.of(SOUTH, EAST), walk(DungeonRoute.parse("# маршрут\nc8\n\nC9 D9\n")));
    }

    @Test
    void notNeighbouringCellsAreAnError() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> DungeonRoute.parse("C8 C10"));
        assertTrue(e.getMessage().contains("C8 C10"));
    }

    @Test
    void wrongCellIsAnError() {
        assertThrows(IllegalArgumentException.class, () -> DungeonRoute.parse("C8 Ц9"));
    }

    @Test
    void anEmptyRouteHasNoSteps() {
        assertEquals(Optional.empty(), DungeonRoute.parse("# пусто").next());
    }

    @Test
    void aPassageIsMarkedOnItsStepOnly() {
        DungeonRoute route = DungeonRoute.parse("I3 J3 >J2 J1");
        assertFalse(route.nextIsPassage());
        route.moved();
        assertEquals(Optional.of(NORTH), route.next());
        assertTrue(route.nextIsPassage());
        route.moved();
        assertEquals(Optional.of(NORTH), route.next());
        assertFalse(route.nextIsPassage());
        route.moved();
        assertFalse(route.nextIsPassage());
    }

    @Test
    void aLookStaysInTheCell() {
        DungeonRoute route = DungeonRoute.parse("H2 H1 @E H2");
        route.moved();
        assertTrue(route.nextIsLook());
        assertEquals(Optional.of(EAST), route.next());
        route.moved();
        assertFalse(route.nextIsLook());
        assertEquals(Optional.of(SOUTH), route.next());
    }

    @Test
    void wrongLookIsAnError() {
        assertThrows(IllegalArgumentException.class, () -> DungeonRoute.parse("H1 @X"));
    }

    @Test
    void theGribnitsaRouteIsReadable() {
        DungeonRoute route = DungeonRoute.load(DungeonsPage.FIRST_FLOOR_ROUTE).orElseThrow();
        assertEquals(109, route.size());
    }

    @Test
    void theSecondFloorRouteIsReadable() {
        DungeonRoute route = DungeonRoute.load(DungeonsPage.SECOND_FLOOR_ROUTE).orElseThrow();
        assertEquals(134, route.size());
    }

    @Test
    void aPassageToAFarCellGoesToTheGivenSide() {
        DungeonRoute route = DungeonRoute.parse("C6 >E:D7 D8");
        assertEquals(Optional.of(EAST), route.next());
        assertTrue(route.nextIsPassage());
        route.moved();
        assertEquals(Optional.of(SOUTH), route.next());
        assertFalse(route.nextIsPassage());
    }

    @Test
    void wrongPassageSideIsAnError() {
        assertThrows(IllegalArgumentException.class, () -> DungeonRoute.parse("C6 >X:D7"));
    }

    @Test
    void aMissingRouteGivesNothing() {
        assertTrue(DungeonRoute.load("maps/nothing.txt").isEmpty());
    }

}
