package com.combats.utils;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Сохраняет и читает данные пользователя для автозаполнения формы:
 * логин, пароль, имя и токен Telegram-бота. Файл лежит вне проекта: ~/.combats/user-data.txt.
 */
public class FileWorker {

    private static final Logger log = LoggerFactory.getLogger(FileWorker.class);

    private static final Path USER_DATA_FILE_PATH =
            Paths.get(System.getProperty("user.home"), ".combats", "user-data.txt");

    /**
     * Записывает текущие настройки пользователя в файл, создавая папку при необходимости.
     */
    public static void writeUserDataFile() {
        try {
            Files.createDirectories(USER_DATA_FILE_PATH.getParent());
        } catch (IOException e) {
            log.error("Can't create directory {}", USER_DATA_FILE_PATH.getParent(), e);
        }
        try (FileWriter writer = new FileWriter(USER_DATA_FILE_PATH.toFile(), false)) {

            writer.write(Properties.getUserLogin());
            writer.append('\n');
            writer.append(Properties.getUserPassword());
            writer.append('\n');
            writer.append(Properties.getTelegramBotName());
            writer.append('\n');
            writer.append(Properties.getTelegramBotToken());
            writer.flush();

        } catch (IOException e) {
            log.error("Can't write user data file {}", USER_DATA_FILE_PATH, e);
        }
    }

    /**
     * @return массив из 4 строк: логин, пароль, имя и токен Telegram-бота;
     *         если файла нет (первый запуск), все элементы null
     */
    public static String[] readUserDataFile() {
        String[] userData = new String[4];
        if (!Files.exists(USER_DATA_FILE_PATH))
            return userData;
        try (BufferedReader br = new BufferedReader(new FileReader(USER_DATA_FILE_PATH.toFile()))) {
            if (br.ready()) {
                userData[0] = br.readLine();
                userData[1] = br.readLine();
                userData[2] = br.readLine();
                userData[3] = br.readLine();
            }
        } catch (IOException e) {
            log.error("Can't read user data file {}", USER_DATA_FILE_PATH, e);
        }
        return userData;
    }

}
