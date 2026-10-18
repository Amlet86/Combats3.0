package com.combats.utils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Сохраняет и читает данные пользователя, чтобы форма заполнялась сама, а Telegram-бот
 * запускался вместе с программой. Файл лежит вне проекта: ~/.combats/user-data.txt.
 * <p>
 * Строки файла: логин, пароль, владелец в Telegram, токен бота, id чата владельца.
 * Старые форматы при чтении понимаем: файл из 4 строк хранил в третьей строке имя бота, а файл
 * из 6 строк (до 18.10.2026) — в пятой строке прокси для Telegram. Обе строки пропускаем.
 */
public class FileWorker {

    private static final Logger log = LoggerFactory.getLogger(FileWorker.class);

    private static final Path USER_DATA_FILE_PATH =
            Paths.get(System.getProperty("user.home"), ".combats", "user-data.txt");

    private static final int OLD_FORMAT_LINES = 4;
    private static final int FORMAT_WITH_PROXY_LINES = 6;
    private static final int PROXY_LINE = 4;

    /**
     * Записывает текущие настройки пользователя в файл, создавая папку при необходимости.
     * Вызывается из потока игры и из потока Telegram, поэтому synchronized.
     */
    public static synchronized void writeUserDataFile() {
        writeUserDataFile(USER_DATA_FILE_PATH);
    }

    /**
     * Читает настройки пользователя из файла в {@link Properties}. Если файла нет (первый запуск),
     * ничего не меняет.
     */
    public static synchronized void readUserDataFile() {
        readUserDataFile(USER_DATA_FILE_PATH);
    }

    static void writeUserDataFile(Path file) {
        List<String> lines = List.of(
                nullToEmpty(Properties.getUserLogin()),
                nullToEmpty(Properties.getUserPassword()),
                nullToEmpty(Properties.getTelegramOwner()),
                nullToEmpty(Properties.getTelegramBotToken()),
                nullToEmpty(Properties.getTelegramChatId()));
        try {
            Files.createDirectories(file.getParent());
            Files.write(file, lines, StandardCharsets.UTF_8);
        } catch (IOException e) {
            log.error("Can't write user data file {}", file, e);
        }
    }

    static void readUserDataFile(Path file) {
        if (!Files.exists(file))
            return;
        List<String> lines;
        try {
            lines = new ArrayList<>(Files.readAllLines(file, StandardCharsets.UTF_8));
        } catch (IOException e) {
            log.error("Can't read user data file {}", file, e);
            return;
        }
        if (lines.size() == OLD_FORMAT_LINES)
            lines.set(2, "");
        if (lines.size() == FORMAT_WITH_PROXY_LINES)
            lines.remove(PROXY_LINE);
        Properties.setUserLogin(line(lines, 0));
        Properties.setUserPassword(line(lines, 1));
        Properties.setTelegramOwner(line(lines, 2));
        Properties.setTelegramBotToken(line(lines, 3));
        Properties.setTelegramChatId(line(lines, 4));
    }

    /**
     * @return строка файла с номером index или null, если строки нет или она пустая
     */
    private static String line(List<String> lines, int index) {
        if (index >= lines.size() || lines.get(index).isBlank())
            return null;
        return lines.get(index);
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

}
