package com.combats.utils;

import java.io.Console;
import java.util.Map;

/**
 * Пароль и настройки Telegram для запуска на сервере ({@code --server}). Берутся из переменных
 * окружения, а не из параметров и не из файла: параметры процесса видят все пользователи сервера
 * ({@code ps}, {@code /proc/<pid>/cmdline}), а окружение процесса — только его владелец и root.
 * В systemd переменные задаются через {@code EnvironmentFile} с правами 600.
 * <p>
 * Если пароля в окружении нет, а программа запущена из консоли, пароль спрашивается без эха.
 *
 * @param password       пароль персонажа
 * @param telegramOwner  username владельца в Telegram или null
 * @param telegramToken  токен бота от @BotFather или null
 * @param telegramChatId чат владельца для уведомлений или null (тогда бот узнает его из первого сообщения)
 */
public record ServerSecrets(String password, String telegramOwner, String telegramToken, String telegramChatId) {

    public static final String PASSWORD = "COMBATS_PASSWORD";
    public static final String TELEGRAM_OWNER = "TELEGRAM_OWNER";
    public static final String TELEGRAM_TOKEN = "TELEGRAM_TOKEN";
    public static final String TELEGRAM_CHAT_ID = "TELEGRAM_CHAT_ID";

    /**
     * Читает секреты из окружения процесса; пароль, если его там нет, спрашивает в консоли.
     */
    public static ServerSecrets fromEnvironment() {
        ServerSecrets secrets = fromEnvironment(System.getenv());
        if (secrets.password() != null)
            return secrets;
        return new ServerSecrets(askPassword(), secrets.telegramOwner(), secrets.telegramToken(),
                secrets.telegramChatId());
    }

    /**
     * @param env переменные окружения
     * @return секреты; пустые и не заданные значения — null
     */
    static ServerSecrets fromEnvironment(Map<String, String> env) {
        return new ServerSecrets(value(env, PASSWORD), value(env, TELEGRAM_OWNER), value(env, TELEGRAM_TOKEN),
                value(env, TELEGRAM_CHAT_ID));
    }

    /**
     * @return пароль, введённый в консоли без эха, или null, если консоли нет (например, под systemd)
     */
    private static String askPassword() {
        Console console = System.console();
        if (console == null)
            return null;
        char[] password = console.readPassword("Password for the character: ");
        return password == null || password.length == 0 ? null : new String(password);
    }

    private static String value(Map<String, String> env, String name) {
        String value = env.get(name);
        return value == null || value.isBlank() ? null : value.trim();
    }

    /** Чтобы секреты не попали в лог через toString(). */
    @Override
    public String toString() {
        return "ServerSecrets[password=" + (password == null ? "none" : "***")
                + ", telegramOwner=" + telegramOwner
                + ", telegramToken=" + (telegramToken == null ? "none" : "***")
                + ", telegramChatId=" + telegramChatId + "]";
    }

}
