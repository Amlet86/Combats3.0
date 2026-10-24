package com.combats.utils;

import java.util.UUID;

import org.slf4j.MDC;

/**
 * Поля игры, которые попадают в каждую запись лога потока игры (MDC). В JSON-логе
 * (~/.combats/logs/bot.json) по ним удобно фильтровать в Kibana: {@code game_id} — все записи одной
 * игры от Start до остановки, {@code battle} — все записи одного круга игры (поиск боя, бой,
 * восстановление HP).
 * <p>
 * MDC привязан к потоку, поэтому поля заполняет и очищает сам поток игры.
 */
public final class LogContext {

    public static final String GAME_ID = "game_id";
    public static final String LOGIN = "login";
    public static final String MODE = "mode";
    public static final String BATTLE = "battle";

    private static int battle;

    private LogContext() {
    }

    /**
     * Начало игры: новый game_id, логин и режим, счётчик боёв с нуля.
     *
     * @param login логин персонажа
     * @param pvp   true — PvP, false — подземелье
     */
    public static void startGame(String login, boolean pvp) {
        battle = 0;
        MDC.clear();
        MDC.put(GAME_ID, UUID.randomUUID().toString().substring(0, 8));
        if (login != null)
            MDC.put(LOGIN, login);
        MDC.put(MODE, pvp ? "PvP" : "Dungeon");
    }

    /**
     * Начало очередного круга игры. Круг, прерванный ошибкой, тоже считается.
     *
     * @return номер круга, с 1
     */
    public static int nextBattle() {
        battle++;
        MDC.put(BATTLE, String.valueOf(battle));
        return battle;
    }

    /**
     * Конец игры: поля больше не нужны.
     */
    public static void clear() {
        MDC.clear();
    }

}
