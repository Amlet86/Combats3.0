package com.combats.utils;

import java.time.Duration;
import java.time.Instant;
import java.util.Locale;

/**
 * Статистика текущей (или последней) игры для команды /stats и итога игры: сколько боёв, сколько
 * урона и опыта, сколько было ошибок. Бои считаются по итогам из чата игры
 * ({@link com.combats.pages.ChatPage}), поэтому бой, итог которого ещё не пришёл, не учитывается.
 * <p>
 * Пишет поток игры, читает поток Telegram, поэтому методы synchronized.
 */
public final class GameStats {

    private static Instant startedAt;
    private static Instant finishedAt;
    private static int battles;
    private static long damage;
    private static long experience;
    private static long bestDamage;
    private static int errors;
    private static int recoveries;

    private GameStats() {
    }

    /**
     * Начало новой игры: счётчики с нуля.
     */
    public static synchronized void start() {
        start(Instant.now());
    }

    static synchronized void start(Instant now) {
        startedAt = now;
        finishedAt = null;
        battles = 0;
        damage = 0;
        experience = 0;
        bestDamage = 0;
        errors = 0;
        recoveries = 0;
    }

    /** Для тестов: как будто игр ещё не было. */
    static synchronized void clear() {
        start(null);
    }

    /**
     * Конец игры: время игры дальше не растёт, статистика остаётся до следующей игры.
     */
    public static synchronized void finish() {
        finish(Instant.now());
    }

    static synchronized void finish(Instant now) {
        if (startedAt != null && finishedAt == null)
            finishedAt = now;
    }

    /**
     * Итог боя из чата.
     *
     * @param battleDamage     нанесённый урон или null, если его нет в итоге
     * @param battleExperience полученный опыт или null, если его нет в итоге
     */
    public static synchronized void battle(Long battleDamage, Long battleExperience) {
        battles++;
        if (battleDamage != null) {
            damage += battleDamage;
            bestDamage = Math.max(bestDamage, battleDamage);
        }
        if (battleExperience != null)
            experience += battleExperience;
    }

    /** Ошибка бота, которая считается в лимит ошибок подряд. */
    public static synchronized void error() {
        errors++;
    }

    /** Ошибка с внешней причиной, которую бот устранил: вошёл заново, дождался сайта, перезапустил браузер. */
    public static synchronized void recovery() {
        recoveries++;
    }

    /**
     * @return статистика текстом для Telegram
     */
    public static synchronized String summary() {
        return summary(Instant.now());
    }

    static synchronized String summary(Instant now) {
        if (startedAt == null)
            return "No game has been played yet.";
        Duration played = Duration.between(startedAt, finishedAt == null ? now : finishedAt);
        StringBuilder text = new StringBuilder()
                .append(finishedAt == null ? "The game is running for " : "The last game lasted ")
                .append(duration(played)).append('.')
                .append("\nBattles: ").append(battles);
        if (battles > 0) {
            text.append("\nDamage: ").append(number(damage)).append(" HP")
                    .append(" (average ").append(number(damage / battles))
                    .append(", best ").append(number(bestDamage)).append(')')
                    .append("\nExperience: ").append(number(experience))
                    .append(" (average ").append(number(experience / battles)).append(')');
            // за первый час опыт в час прыгает от боя к бою, поэтому показываем его с часа игры
            if (played.toHours() > 0)
                text.append("\nExperience per hour: ").append(number(experience * 3600 / played.toSeconds()));
        }
        text.append("\nErrors: ").append(errors)
                .append(", recovered (log in again, site outage, browser restart): ").append(recoveries);
        return text.toString();
    }

    /** @return «2 h 05 min» или «7 min» */
    static String duration(Duration duration) {
        long hours = duration.toHours();
        long minutes = duration.toMinutesPart();
        return hours > 0 ? String.format("%d h %02d min", hours, minutes) : minutes + " min";
    }

    /** @return число с разделителем тысяч: 25,400 */
    private static String number(long value) {
        return String.format(Locale.US, "%,d", value);
    }

}
