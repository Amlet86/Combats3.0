package com.combats.utils;

/**
 * Какие PvP-бои играет бот. Значения показываются в выпадающем списке формы.
 */
public enum PvpProfile {

    /** Хаотичные бои: заявки на странице «Хаотичные». */
    CHAOS("Хаоты"),
    /** Бои Лиги 1 на 1: между боями персонаж восстанавливается в Комнате Знахаря. */
    LEAGUE("Лига");

    private final String title;

    PvpProfile(String title) {
        this.title = title;
    }

    @Override
    public String toString() {
        return title;
    }

}
