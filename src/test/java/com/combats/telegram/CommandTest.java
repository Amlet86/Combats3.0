package com.combats.telegram;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Тесты распознавания команд Telegram-бота ({@link Command}).
 */
class CommandTest {

    @Test
    void parsesCommandsFromKeyboardAndMenu() {
        assertEquals(Command.PLAY, Command.parse("/play"));
        assertEquals(Command.STOP, Command.parse("/stop"));
        assertEquals(Command.STATUS, Command.parse("/status"));
        assertEquals(Command.SCREENSHOT, Command.parse("/screenshot"));
        assertEquals(Command.HELP, Command.parse("/help"));
    }

    @Test
    void ignoresCaseSpacesBotNameAndArguments() {
        assertEquals(Command.PLAY, Command.parse("  /PLAY  "));
        assertEquals(Command.STOP, Command.parse("/stop@CombatsBot"));
        assertEquals(Command.STATUS, Command.parse("/status now"));
    }

    @Test
    void startOfTheChatShowsHelp() {
        assertEquals(Command.HELP, Command.parse("/start"));
    }

    @Test
    void unknownTextIsNotACommand() {
        assertNull(Command.parse("play"));
        assertNull(Command.parse("/unknown"));
        assertNull(Command.parse(""));
        assertNull(Command.parse(null));
    }

    @Test
    void helpListsAllCommands() {
        String help = Command.helpText();
        for (Command command : Command.values())
            assertTrue(help.contains(command.text), command.text);
    }

}
