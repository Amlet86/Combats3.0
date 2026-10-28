package com.combats.pages;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Чужая заявка на хаотичный бой на странице заявок: значение её радиокнопки {@code gocombat} и то,
 * что написано рядом: «23:42 (ЧУ-Ж-ОЙ [8], Necroz [8]) (7-9) Бой начнется через 1.7 мин., …».
 * <p>
 * Раньше бот брал времена всех заявок одним селектором, а радиокнопки другим, и сопоставлял их
 * по номеру. Если у какой-то заявки нет радиокнопки (не подходит уровень), номера съезжали.
 * Теперь текст каждой заявки берётся от её радиокнопки до следующей, и они не могут перепутаться.
 *
 * @param radioValue значение радиокнопки, по нему заявку выбираем
 * @param players    сколько участников уже в заявке
 * @param minLevel   минимальный уровень участников
 * @param maxLevel   максимальный уровень участников
 * @param minutes    сколько минут до начала боя
 */
record ChaosApplication(String radioValue, int players, int minLevel, int maxLevel, double minutes) {

    /** (участники) (уровни) Бой начнется через N мин.: группа 1 участники, 2 и 3 уровни, 4 минуты. */
    private static final Pattern APPLICATION = Pattern.compile(
            "\\(([^()]*)\\)\\s*\\((\\d+)-(\\d+)\\)\\s*Бой начн[её]тся через\\s*(\\d+(?:[.,]\\d+)?)\\s*мин");
    /** Текст скрипта перед участником, если он попал в текст заявки: drwfl("Alkatas",1783992227,"9",0,"Incognito"). */
    private static final Pattern SCRIPT = Pattern.compile("drwfl\\([^)]*\\)");
    /** Участник в списке заявки: «Necroz [8]». */
    private static final Pattern PLAYER = Pattern.compile("\\[\\d+]");

    /**
     * Меньше этого времени до начала заявка уже не годится: пока бот кликает, бой начнётся без него
     * (в логе 26.10.2026 бот «вступал» в заявку за 0.3 мин. до начала и потом подавал свою).
     */
    static final double MIN_MINUTES_TO_JOIN = 0.3;

    /**
     * @param radioValue значение радиокнопки
     * @param text       текст страницы от этой радиокнопки до следующей
     * @return заявка или пустой Optional, если в тексте нет заявки
     */
    static Optional<ChaosApplication> parse(String radioValue, String text) {
        if (radioValue == null || text == null)
            return Optional.empty();
        Matcher matcher = APPLICATION.matcher(SCRIPT.matcher(text).replaceAll(""));
        if (!matcher.find())
            return Optional.empty();
        int players = (int) PLAYER.matcher(matcher.group(1)).results().count();
        return Optional.of(new ChaosApplication(radioValue, players,
                Integer.parseInt(matcher.group(2)), Integer.parseInt(matcher.group(3)),
                Double.parseDouble(matcher.group(4).replace(',', '.'))));
    }

    /**
     * Выбирает заявку, в которую стоит вступить: подходит по уровню (если он известен), до начала
     * не меньше {@link #MIN_MINUTES_TO_JOIN}, в неё ещё не пробовали вступить безуспешно.
     * Из таких берём ту, где больше участников: бой не начнётся, если собралось меньше 4 человек.
     * При равенстве берём ту, что начнётся раньше.
     *
     * @param applications заявки на странице
     * @param ownLevel     уровень персонажа или null, если его не удалось прочитать
     * @param rejected     радиокнопки заявок, в которые вступить не получилось
     */
    static Optional<ChaosApplication> choose(List<ChaosApplication> applications, Integer ownLevel,
                                             Collection<String> rejected) {
        return applications.stream()
                .filter(application -> application.fits(ownLevel))
                .filter(application -> application.minutes >= MIN_MINUTES_TO_JOIN)
                .filter(application -> !rejected.contains(application.radioValue))
                .max(Comparator.comparingInt(ChaosApplication::players)
                        .thenComparing(Comparator.comparingDouble(ChaosApplication::minutes).reversed()));
    }

    /**
     * @return true, если уровень персонажа в пределах заявки или уровень неизвестен
     */
    boolean fits(Integer ownLevel) {
        return ownLevel == null || (ownLevel >= minLevel && ownLevel <= maxLevel);
    }

    /**
     * Уровень персонажа из шапки страницы: «Amlett [9] 1983/2092 …».
     *
     * @return уровень или null, если логина в тексте нет
     */
    static Integer ownLevel(String pageText, String login) {
        if (pageText == null || login == null || login.isBlank())
            return null;
        Matcher matcher = Pattern.compile(Pattern.quote(login.trim()) + " \\[(\\d+)]").matcher(pageText);
        return matcher.find() ? Integer.valueOf(matcher.group(1)) : null;
    }

    @Override
    public String toString() {
        return String.format("%s min, %d-%d, %d player(s), radio %s", minutes, minLevel, maxLevel, players, radioValue);
    }

}
