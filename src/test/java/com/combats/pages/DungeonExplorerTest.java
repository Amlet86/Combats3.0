package com.combats.pages;

import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;

import static com.combats.pages.Direction.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Тесты сторон света ({@link Direction}) и обхода этажа ({@link DungeonExplorer}).
 * Коды сторон сняты с мини-карты Грибницы 03.11.2026. Названия тестов описывают проверяемый случай.
 */
class DungeonExplorerTest {

    @Test
    void directionIsTakenFromTheMapMarkCode() {
        assertEquals(NORTH, Direction.fromCode("1"));
        assertEquals(EAST, Direction.fromCode("3"));
        assertEquals(SOUTH, Direction.fromCode("5"));
        assertEquals(WEST, Direction.fromCode("7"));
        assertNull(Direction.fromCode("2"));
        assertNull(Direction.fromCode(null));
    }

    @Test
    void directionIsTakenFromThePageText() {
        assertEquals(EAST, Direction.fromText("Выйти Грибница - Этаж 1 - Грибной коридор Amlett [10] смотрим на восток"));
        assertEquals(SOUTH, Direction.fromText("смотрим на юг Задание: Забияка"));
        assertNull(Direction.fromText("Грибница"));
    }

    @Test
    void turnsGoClockwise() {
        assertEquals(EAST, NORTH.right());
        assertEquals(WEST, NORTH.left());
        assertEquals(SOUTH, NORTH.opposite());
        assertEquals(NORTH, WEST.right());
    }

    @Test
    void stepLinksAreTurnedIntoSides() {
        // смотрим на восток: вперёд — восток, вправо — юг, назад — запад, влево — север
        assertEquals(EnumSet.of(EAST, WEST), DungeonExplorer.openSides(EAST, Set.of("m1", "m5", "rl", "rr")));
        assertEquals(EnumSet.of(SOUTH, NORTH), DungeonExplorer.openSides(EAST, Set.of("m3", "m7")));
    }

    @Test
    void forwardIsPreferredToOtherNewCells() {
        DungeonExplorer explorer = new DungeonExplorer();
        assertEquals(Optional.of(EAST), explorer.next(EAST, EnumSet.of(EAST, NORTH, SOUTH)));
    }

    @Test
    void theCellWeCameFromIsNotVisitedAgainWhileThereAreNewOnes() {
        DungeonExplorer explorer = new DungeonExplorer();
        explorer.moved(EAST);
        // назад, на запад, только что были: идём на север
        assertEquals(Optional.of(NORTH), explorer.next(EAST, EnumSet.of(WEST, NORTH)));
    }

    @Test
    void fromADeadEndTheExplorerGoesBackByItsTrail() {
        DungeonExplorer explorer = new DungeonExplorer();
        explorer.moved(EAST);
        explorer.moved(NORTH);
        // тупик: открыт только путь назад, на юг
        assertEquals(Optional.of(SOUTH), explorer.next(NORTH, EnumSet.of(SOUTH)));
        explorer.moved(SOUTH);
        // в этой клетке всё посещено: назад, на запад
        assertEquals(Optional.of(WEST), explorer.next(SOUTH, EnumSet.of(WEST, NORTH)));
        explorer.moved(WEST);
        // в начале и всё посещено: этаж обойдён
        assertEquals(Optional.empty(), explorer.next(WEST, EnumSet.of(EAST)));
        assertEquals(3, explorer.visitedCells());
    }

    @Test
    void aLoopIsWalkedOnceAndThenTheExplorerReturns() {
        // квадрат 2×2: (0,0) → восток → юг → запад → и снова (0,0) уже посещена
        DungeonExplorer explorer = new DungeonExplorer();
        explorer.moved(EAST);
        explorer.moved(SOUTH);
        explorer.moved(WEST);
        assertEquals(Optional.of(EAST), explorer.next(WEST, EnumSet.of(NORTH, EAST)));
    }

    @Test
    void aBlockedSideIsNotTriedAgain() {
        DungeonExplorer explorer = new DungeonExplorer();
        explorer.blocked(NORTH);
        assertEquals(Optional.of(EAST), explorer.next(NORTH, EnumSet.of(NORTH, EAST)));
        assertEquals(Optional.empty(), explorer.next(NORTH, EnumSet.of(NORTH)));
    }

    @Test
    void theShortestWayToTheDescentGoesByWalkedPassages() {
        // (0,0) → восток (1,0) → восток (2,0), «Спуск» виден отсюда, смотрим на восток;
        // дальше юг (2,1) → запад (1,1): от (1,1) к (2,0) короче через (2,1), чем через (1,0) — прохода (1,1)→(1,0) нет
        DungeonExplorer explorer = new DungeonExplorer();
        explorer.moved(EAST);
        explorer.moved(EAST);
        explorer.sawDescent(EAST);
        explorer.moved(SOUTH);
        explorer.moved(WEST);
        assertTrue(explorer.knowsDescent());
        assertFalse(explorer.atDescent());
        assertEquals(Optional.of(EAST), explorer.towardsDescent());
        explorer.moved(EAST);
        assertEquals(Optional.of(NORTH), explorer.towardsDescent());
        explorer.moved(NORTH);
        assertTrue(explorer.atDescent());
        assertEquals(EAST, explorer.descentFacing());
        assertEquals(Optional.empty(), explorer.towardsDescent());
    }

    @Test
    void withoutADescentThereIsNoWayToIt() {
        DungeonExplorer explorer = new DungeonExplorer();
        explorer.moved(EAST);
        assertFalse(explorer.knowsDescent());
        assertEquals(Optional.empty(), explorer.towardsDescent());
    }

    @Test
    void aNewFloorForgetsTheDescent() {
        DungeonExplorer explorer = new DungeonExplorer();
        explorer.sawDescent(NORTH);
        explorer.reset();
        assertFalse(explorer.knowsDescent());
    }

    @Test
    void aBlockedSideMakesTheFloorIncomplete() {
        DungeonExplorer explorer = new DungeonExplorer();
        assertTrue(explorer.isComplete());
        explorer.blocked(NORTH);
        assertFalse(explorer.isComplete());
    }

    @Test
    void aLeftRouteMakesTheFloorIncomplete() {
        DungeonExplorer explorer = new DungeonExplorer();
        explorer.markIncomplete();
        assertFalse(explorer.isComplete());
    }

    @Test
    void anAvoidedSideIsNotTriedAndTheFloorStaysComplete() {
        DungeonExplorer explorer = new DungeonExplorer();
        explorer.avoid(WEST);
        assertEquals(Optional.of(NORTH), explorer.next(WEST, EnumSet.of(WEST, NORTH)));
        assertTrue(explorer.isComplete());
    }

    @Test
    void aNewFloorIsCompleteAgain() {
        DungeonExplorer explorer = new DungeonExplorer();
        explorer.blocked(NORTH);
        explorer.reset();
        assertTrue(explorer.isComplete());
    }

    @Test
    void resetStartsTheExplorationOver() {
        DungeonExplorer explorer = new DungeonExplorer();
        explorer.moved(EAST);
        explorer.reset();
        assertEquals(1, explorer.visitedCells());
        assertEquals(Optional.of(EAST), explorer.next(EAST, EnumSet.of(EAST)));
    }

}
