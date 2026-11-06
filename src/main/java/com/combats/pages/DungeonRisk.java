package com.combats.pages;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Единицы риска у входа в подземелье: «Стоимость похода: 40 единиц риска (доступно 83.52 единиц)»
 * (снято 03.11.2026 у Грибницы). Каждый поход тратит стоимость, риск восстанавливается медленно
 * (за 2 минуты 83.52 → 83.66), поэтому, когда его не хватает, бот заканчивает игру.
 *
 * @param cost      стоимость похода
 * @param available сколько единиц есть сейчас
 */
public record DungeonRisk(double cost, double available) {

    private static final Pattern RISK = Pattern.compile(
            "Стоимость похода:\\s*([\\d.]+)\\s*единиц\\S*\\s+риска\\s*\\(доступно\\s*([\\d.]+)");

    /**
     * @param pageText текст страницы {@code dungeon.pl}
     * @return риск или пусто, если строки о стоимости на странице нет
     */
    static Optional<DungeonRisk> parse(String pageText) {
        if (pageText == null)
            return Optional.empty();
        Matcher matcher = RISK.matcher(pageText);
        if (!matcher.find())
            return Optional.empty();
        try {
            return Optional.of(new DungeonRisk(Double.parseDouble(matcher.group(1)), Double.parseDouble(matcher.group(2))));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    /** @return true, если риска хватает на поход */
    public boolean enough() {
        return available >= cost;
    }

}
