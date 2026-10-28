package com.combats.pages;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Тесты решения подать свою заявку на хаотичный бой ({@link GoToBattlePage#shouldSubmitOwn}).
 * Названия тестов описывают проверяемый случай.
 */
class OwnApplicationTest {

    @Test
    void firstLookWithoutApplicationsDoesNotSubmitOwn() {
        // список заявок мог ещё не загрузиться: сначала обновляем страницу и смотрим снова
        assertFalse(GoToBattlePage.shouldSubmitOwn(1));
    }

    @Test
    void secondLookInARowWithoutApplicationsSubmitsOwn() {
        assertTrue(GoToBattlePage.shouldSubmitOwn(GoToBattlePage.LOOKS_BEFORE_OWN_APPLICATION));
        assertTrue(GoToBattlePage.shouldSubmitOwn(5));
    }

}
