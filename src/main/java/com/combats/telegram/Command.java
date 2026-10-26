package com.combats.telegram;

import java.util.Locale;

/**
 * Команды Telegram-бота. Кнопки клавиатуры отправляют те же тексты, что и команды.
 */
enum Command {

    PLAY("/play", "start the game with the last settings"),
    STOP("/stop", "stop the game"),
    STATUS("/status", "what the bot is doing now"),
    SCREENSHOT("/screenshot", "screenshot of the game"),
    STATS("/stats", "battles, damage and experience in the current game"),
    HELP("/help", "list of commands");

    final String text;
    final String description;

    Command(String text, String description) {
        this.text = text;
        this.description = description;
    }

    /**
     * Распознаёт команду. Понимает «/play», «/play@MyBot» (так Telegram пишет команды в группах)
     * и «/start» — её Telegram отправляет при первом открытии бота.
     *
     * @return команда или null, если текст не команда бота
     */
    static Command parse(String message) {
        if (message == null)
            return null;
        String word = message.trim().split("\\s+")[0].toLowerCase(Locale.ROOT);
        int botNameStart = word.indexOf('@');
        if (botNameStart >= 0)
            word = word.substring(0, botNameStart);
        if (word.equals("/start"))
            return HELP;
        for (Command command : values()) {
            if (command.text.equals(word))
                return command;
        }
        return null;
    }

    /**
     * @return список команд с описаниями для ответа на /help
     */
    static String helpText() {
        StringBuilder help = new StringBuilder("Commands:");
        for (Command command : values())
            help.append('\n').append(command.text).append(" — ").append(command.description);
        return help.toString();
    }

}
