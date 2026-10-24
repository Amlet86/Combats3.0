package com.combats.utils;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Параметры запуска из командной строки. Без {@code --server} программа открывает форму настроек,
 * с ним работает без окон: для сервера, где нет экрана.
 * <pre>
 * java -jar Combats-3.0.0.jar --server --login ЛОГИН [--pet] [--dungeon] [--end ЧЧ:ММ] [--wait]
 * </pre>
 * Пароля и токена Telegram в параметрах нет: параметры видят все пользователи сервера ({@code ps}),
 * и они остаются в истории команд. Секреты берёт из окружения {@link ServerSecrets}.
 *
 * @param server  без окон: браузер без окна, файл ~/.combats/user-data.txt не читается и не пишется
 * @param login   логин персонажа, обязателен с --server
 * @param pet     использовать питомца в бою (как галочка «Pet»)
 * @param dungeon режим подземелья вместо PvP (как переключатель «Dungeon»)
 * @param endHours   час окончания игры, 0–23; вместе с endMinutes 0:00 — без ограничения
 * @param endMinutes минуты окончания игры, 0–59
 * @param waitForPlay не начинать игру сразу, а ждать команду /play в Telegram
 */
public record LaunchOptions(boolean server, String login, boolean pet, boolean dungeon, int endHours, int endMinutes,
                            boolean waitForPlay) {

    private static final Pattern TIME = Pattern.compile("(\\d{1,2}):(\\d{2})");

    /** Подсказка, которую показываем при неправильных параметрах. */
    public static final String USAGE =
            "Usage: java -jar Combats.jar [--server --login LOGIN [--pet] [--dungeon] [--end HH:MM] [--wait]]\n"
                    + "With --server the password is taken from " + ServerSecrets.PASSWORD
                    + " (or asked in the console), Telegram from " + ServerSecrets.TELEGRAM_OWNER
                    + ", " + ServerSecrets.TELEGRAM_TOKEN + " and " + ServerSecrets.TELEGRAM_CHAT_ID + ".";

    /**
     * Разбирает параметры командной строки.
     *
     * @throws IllegalArgumentException если параметр неизвестен, время записано неверно,
     *                                  параметры сервера заданы без {@code --server} или
     *                                  {@code --server} задан без {@code --login}
     */
    public static LaunchOptions parse(String... args) {
        boolean server = false;
        String login = null;
        boolean pet = false;
        boolean dungeon = false;
        boolean waitForPlay = false;
        int endHours = 0;
        int endMinutes = 0;
        boolean serverOnlyOption = false;
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--server" -> server = true;
                case "--pet" -> pet = serverOnlyOption = true;
                case "--dungeon" -> dungeon = serverOnlyOption = true;
                case "--wait" -> waitForPlay = serverOnlyOption = true;
                case "--login" -> {
                    if (i + 1 >= args.length || args[i + 1].isBlank() || args[i + 1].startsWith("--"))
                        throw new IllegalArgumentException("--login needs the character's login, e.g. --login Amlett");
                    login = args[++i].trim();
                    serverOnlyOption = true;
                }
                case "--end" -> {
                    if (i + 1 >= args.length)
                        throw new IllegalArgumentException("--end needs a time, e.g. --end 23:30");
                    Matcher time = TIME.matcher(args[++i]);
                    if (!time.matches())
                        throw new IllegalArgumentException("Wrong end time: " + args[i] + ", expected HH:MM");
                    endHours = Integer.parseInt(time.group(1));
                    endMinutes = Integer.parseInt(time.group(2));
                    if (endHours > 23 || endMinutes > 59)
                        throw new IllegalArgumentException("Wrong end time: " + args[i]);
                    serverOnlyOption = true;
                }
                default -> throw new IllegalArgumentException("Unknown option: " + args[i]);
            }
        }
        if (serverOnlyOption && !server)
            throw new IllegalArgumentException("--login, --pet, --dungeon, --end and --wait work only with --server");
        if (server && login == null)
            throw new IllegalArgumentException("--server needs --login");
        return new LaunchOptions(server, login, pet, dungeon, endHours, endMinutes, waitForPlay);
    }

}
