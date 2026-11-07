package com.combats.pages;

import java.util.List;

import org.junit.jupiter.api.Test;

import static com.combats.pages.BattleMethods.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Тесты порядка приёмов ({@link BattleMethods}).
 */
class BattleMethodsTest {

    private static final List<String> USUAL = List.of(GRINDER, ACTIVE_SHIELD, ENRAGE, DEFENCE);

    @Test
    void preparationGoesFirstOnTheFirstMoveOnly() {
        assertEquals(List.of(PREPARATION, GRINDER, ACTIVE_SHIELD, ENRAGE, DEFENCE), priority(true, 100, 100));
        assertEquals(PREPARATION, priority(true, 10, 10).get(0));
        assertFalse(priority(false, 100, 100).contains(PREPARATION));
    }

    @Test
    void grinderGoesFirstWhileEverybodyIsHealthy() {
        assertEquals(USUAL, priority(false, 100, 100));
    }

    @Test
    void willpowerGoesFirstBelowThirtyPercent() {
        assertEquals(WILLPOWER, priority(false, 29, 100).get(0));
        assertEquals(USUAL, priority(false, 30, 100));
    }

    @Test
    void doubleThrowGoesFirstWhenTheEnemyIsBelowTwentyPercent() {
        assertEquals(DOUBLE_THROW, priority(false, 100, 19).get(0));
        assertEquals(USUAL, priority(false, 100, 20));
    }

    @Test
    void bothLowHealthRulesWork() {
        assertEquals(List.of(WILLPOWER, DOUBLE_THROW, GRINDER, ACTIVE_SHIELD, ENRAGE, DEFENCE), priority(false, 10, 10));
    }

    @Test
    void onlyDoubleThrowAndWillpowerWaitForTheConfirmation() {
        assertTrue(asksConfirmation(DOUBLE_THROW));
        assertTrue(asksConfirmation(WILLPOWER));
        assertFalse(asksConfirmation(GRINDER));
        assertFalse(asksConfirmation(PREPARATION));
    }

    @Test
    void unknownHealthChangesNothing() {
        assertEquals(USUAL, priority(false, null, null));
    }

}
