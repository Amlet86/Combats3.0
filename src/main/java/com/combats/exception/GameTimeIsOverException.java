package com.combats.exception;

/**
 * Бросается из долгих циклов ожидания, когда наступило время окончания игры.
 * Это не ошибка, а сигнал {@link com.combats.be.TopLevelLogic} штатно завершить игру.
 */
public class GameTimeIsOverException extends RuntimeException {

    public GameTimeIsOverException() {
        super("The end time of the game has come");
    }

}
