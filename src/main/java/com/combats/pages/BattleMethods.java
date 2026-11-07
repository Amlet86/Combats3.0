package com.combats.pages;

import java.util.ArrayList;
import java.util.List;

/**
 * В каком порядке применять приёмы (решение Андрея, 06.11.2026). Приёмы названы именами своих иконок.
 * Других приёмов бот не трогает, чтобы не тратить на них то, что копится на Мясорубку. Свитки не используются.
 * Класс не трогает браузер, поэтому проверяется тестами.
 */
final class BattleMethods {

    static final String PREPARATION = "preparation";
    static final String WILLPOWER = "hit_willpower";
    static final String DOUBLE_THROW = "hit_throwdblaxe";
    static final String GRINDER = "hit_grinder";
    static final String ACTIVE_SHIELD = "block_activeshield";
    static final String ENRAGE = "hp_enrage";
    static final String DEFENCE = "hp_defence";

    /** Меньше этого здоровья у персонажа первым идёт Воля к победе. */
    private static final int LOW_OWN_HP_PERCENT = 30;
    /** Меньше этого здоровья у противника первым идёт Двойной бросок. */
    private static final int LOW_ENEMY_HP_PERCENT = 20;

    private BattleMethods() {
    }

    /**
     * Окно после Двойного броска снято 06.11.2026. Воли к победе в записи нет, поэтому окно ждём и после неё.
     * Остальные приёмы из списка применялись без окна, и время хода на него не тратим.
     *
     * @return true, если после приёма надо подождать окно подтверждения
     */
    static boolean asksConfirmation(String icon) {
        return DOUBLE_THROW.equals(icon) || WILLPOWER.equals(icon);
    }

    /**
     * @param firstMove     true на первом ходу боя: только тогда применяем Тактический расчет
     * @param ownHpPercent   здоровье персонажа в процентах; null, если его не удалось прочитать
     * @param enemyHpPercent здоровье противника в процентах; null, если его не удалось прочитать
     * @return иконки приёмов в порядке применения
     */
    static List<String> priority(boolean firstMove, Integer ownHpPercent, Integer enemyHpPercent) {
        List<String> methods = new ArrayList<>();
        if (firstMove)
            methods.add(PREPARATION);
        if (ownHpPercent != null && ownHpPercent < LOW_OWN_HP_PERCENT)
            methods.add(WILLPOWER);
        if (enemyHpPercent != null && enemyHpPercent < LOW_ENEMY_HP_PERCENT)
            methods.add(DOUBLE_THROW);
        methods.addAll(List.of(GRINDER, ACTIVE_SHIELD, ENRAGE, DEFENCE));
        return methods;
    }

}
