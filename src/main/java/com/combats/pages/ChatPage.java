package com.combats.pages;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

import com.combats.utils.Notifications;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.codeborne.selenide.Selenide.executeJavaScript;
import static com.combats.utils.GameControl.status;

/**
 * Итоги боёв из чата игры. После смерти бот выходит из боя, не дожидаясь остальных, и итога на
 * экране боя ещё нет: игра пишет его в чат позже, когда бой закончится у всех. Поэтому итоги
 * берём из чата и отправляем каждый один раз.
 * <p>
 * Чат лежит в верхнем документе: каждое сообщение — {@code div.Chat}, одно и то же сообщение
 * бывает сразу в нескольких вкладках чата (снято 18.10.2026).
 */
public class ChatPage {

    private static final Logger log = LoggerFactory.getLogger(ChatPage.class);

    /** Итог боя: «21:09 Внимание! Бой закончен. Всего вами нанесено урона: 1800 HP. Получено опыта: …». */
    static final Pattern BATTLE_RESULT = Pattern.compile("^\\d{1,2}:\\d{2} Внимание! Бой закончен\\. Всего вами нанесено урона.*");
    /** Текст всех сообщений чата. Выполняется из любого фрейма игры: чат всегда в верхнем документе. */
    private static final String READ_CHAT = "return [...top.document.querySelectorAll('div.Chat')]"
        + ".map(e => e.textContent.replace(/\\s+/g, ' ').trim());";

    private static final Set<String> reported = new HashSet<>();
    private static boolean started;

    private ChatPage() {
    }

    /**
     * Начало новой игры: итоги, которые уже лежат в чате после входа, относятся к прошлым боям.
     * Первое чтение чата после этого их только запомнит.
     */
    public static synchronized void reset() {
        reported.clear();
        started = false;
    }

    /**
     * Отправляет в уведомления (Telegram) итоги боёв, которые появились в чате с прошлой проверки.
     */
    public static synchronized void reportNewBattleResults() {
        List<String> results = newBattleResults(readChat(), reported);
        if (!started) {
            started = true;
            return;
        }
        for (String result : results) {
            String message = "Battle is over: " + result.replace("Внимание! ", "");
            status(message);
            Notifications.message(message);
        }
    }

    /**
     * Выбирает из сообщений чата итоги боёв, которых ещё нет в {@code reported}, и добавляет их туда.
     */
    static List<String> newBattleResults(List<String> chat, Set<String> reported) {
        List<String> results = new ArrayList<>();
        for (String line : chat)
            if (BATTLE_RESULT.matcher(line).matches() && reported.add(line))
                results.add(line);
        return results;
    }

    private static List<String> readChat() {
        try {
            List<?> lines = executeJavaScript(READ_CHAT);
            List<String> chat = new ArrayList<>();
            if (lines != null)
                lines.forEach(line -> chat.add(String.valueOf(line)));
            return chat;
        } catch (RuntimeException e) {
            log.warn("Can't read the chat", e);
            return List.of();
        }
    }

}
