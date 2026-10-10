package com.combats.utils;

import java.time.LocalDateTime;

/**
 * Настройки текущей игры, которые пользователь задаёт в форме {@link com.combats.fe.LoginForm}.
 */
public class Properties {

    private static boolean headless;
    private static String userLogin;
    private static String userPassword;
    private static boolean typeOfGame;
    private static boolean pet;
    private static LocalDateTime endTimeOfTheGame;
    private static String telegramBotName;
    private static String telegramBotToken;
    private static String telegramChatId;

    /** @return true, если браузер запускается без окна (галочка «Browser off») */
    public static boolean isHeadless() {
        return headless;
    }

    /** Задаёт запуск браузера без окна. */
    public static void setHeadless(boolean headless) {
        Properties.headless = headless;
    }

    /** @return логин персонажа */
    public static String getUserLogin() {
        return userLogin;
    }

    /** Задаёт логин персонажа. */
    public static void setUserLogin(String userLogin) {
        Properties.userLogin = userLogin;
    }

    /** @return пароль персонажа */
    public static String getUserPassword() {
        return userPassword;
    }

    /** Задаёт пароль персонажа. */
    public static void setUserPassword(String userPassword) {
        Properties.userPassword = userPassword;
    }

    /** @return true — режим PvP (хаотичные бои), false — PvE (подземелье) */
    public static boolean getTypeOfGame() {
        return typeOfGame;
    }

    /** Задаёт режим игры: true — PvP, false — PvE. */
    public static void setTypeOfGame(boolean typeOfGame) {
        Properties.typeOfGame = typeOfGame;
    }

    /** @return true, если в бою используется питомец (галочка «Pet») */
    public static boolean getPet() {
        return pet;
    }

    /** Задаёт использование питомца в бою. */
    public static void setPet(boolean pet) {
        Properties.pet = pet;
    }

    /**
     * @return время окончания игры или null, если игра без ограничения по времени
     */
    public static LocalDateTime getEndTimeOfTheGame() {
        return endTimeOfTheGame;
    }

    /**
     * Задаёт время окончания игры по значениям из выпадающих списков формы.
     *
     * @param hours   часы, 0–23
     * @param minutes минуты, 0–59
     */
    public static void setEndTimeOfTheGame(Object hours, Object minutes) {
        endTimeOfTheGame = calculateEndTime(
                Integer.parseInt(hours.toString()),
                Integer.parseInt(minutes.toString()),
                LocalDateTime.now());
    }

    /**
     * Считает момент окончания игры. 0:00 означает «играть без ограничения по времени».
     * Если это время сегодня уже прошло, игра закончится в это же время завтра.
     *
     * @param now текущее время; передаётся параметром, чтобы метод можно было проверить тестами
     * @return время окончания игры или null, если ограничения нет
     */
    static LocalDateTime calculateEndTime(int hours, int minutes, LocalDateTime now) {
        if (hours == 0 && minutes == 0)
            return null;
        LocalDateTime endTime = now.toLocalDate().atTime(hours, minutes);
        return endTime.isAfter(now) ? endTime : endTime.plusDays(1);
    }

    /** @return имя Telegram-бота */
    public static String getTelegramBotName() {
        return telegramBotName;
    }

    /** Задаёт имя Telegram-бота. */
    public static void setTelegramBotName(String telegramBotName) {
        Properties.telegramBotName = telegramBotName;
    }

    /** @return токен Telegram-бота */
    public static String getTelegramBotToken() {
        return telegramBotToken;
    }

    /** Задаёт токен Telegram-бота. */
    public static void setTelegramBotToken(String telegramBotToken) {
        Properties.telegramBotToken = telegramBotToken;
    }

    /** @return id чата Telegram, куда бот отправляет сообщения, или null, если чат неизвестен */
    public static String getTelegramChatId() {
        return telegramChatId;
    }

    /** Задаёт id чата Telegram, из которого пришла команда. */
    public static void setTelegramChatId(String telegramChatId) {
        Properties.telegramChatId = telegramChatId;
    }


}
