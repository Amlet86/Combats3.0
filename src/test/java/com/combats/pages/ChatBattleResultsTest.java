package com.combats.pages;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Тесты выбора итогов боёв из сообщений чата ({@link ChatPage#newBattleResults}).
 * Сообщения взяты из живого чата 18.10.2026.
 */
class ChatBattleResultsTest {

    private static final String RESULT_1 = "21:09 Внимание! Бой закончен. Всего вами нанесено урона: 1800 HP. Получено опыта: 14476 (201%).";
    private static final String RESULT_2 = "21:10 Внимание! Бой закончен. Всего вами нанесено урона: 1362 HP. Получено опыта: 5760 (201%).";

    @Test
    void onlyBattleResultsAreChosen() {
        List<String> chat = List.of(
            "21:05 Внимание! Закончилось действие эффекта \"Защита от нападения\"",
            RESULT_1,
            "21:09 [Montavr] привет",
            "Для вас бой окончен. Ожидаем пока закончат и другие игроки...");
        assertEquals(List.of(RESULT_1), ChatPage.newBattleResults(chat, new HashSet<>()));
    }

    @Test
    void sameMessageFromSeveralChatTabsIsReturnedOnce() {
        List<String> chat = List.of(RESULT_1, RESULT_2, RESULT_1, RESULT_2);
        assertEquals(List.of(RESULT_1, RESULT_2), ChatPage.newBattleResults(chat, new HashSet<>()));
    }

    @Test
    void alreadyReportedResultsAreSkipped() {
        Set<String> reported = new HashSet<>();
        assertEquals(List.of(RESULT_1), ChatPage.newBattleResults(List.of(RESULT_1), reported));
        assertEquals(List.of(RESULT_2), ChatPage.newBattleResults(List.of(RESULT_1, RESULT_2), reported));
        assertEquals(List.of(), ChatPage.newBattleResults(List.of(RESULT_1, RESULT_2), reported));
    }

    @Test
    void resultWithoutTimeIsNotChosen() {
        assertEquals(List.of(), ChatPage.newBattleResults(
            List.of("Внимание! Бой закончен. Всего вами нанесено урона: 10 HP."), new HashSet<>()));
    }

    @Test
    void damageAndExperienceAreParsed() {
        assertEquals(1800L, ChatPage.number(ChatPage.DAMAGE, RESULT_1));
        assertEquals(14476L, ChatPage.number(ChatPage.EXPERIENCE, RESULT_1));
        assertNull(ChatPage.number(ChatPage.EXPERIENCE, "21:09 Внимание! Бой закончен. Всего вами нанесено урона: 0 HP."));
    }

}
