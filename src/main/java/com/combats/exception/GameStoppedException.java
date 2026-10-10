package com.combats.exception;

/**
 * Бросается из циклов игры, когда пользователь нажал «Stop».
 * Это не ошибка, а сигнал {@link com.combats.be.TopLevelLogic} штатно завершить игру.
 */
public class GameStoppedException extends RuntimeException {

    public GameStoppedException() {
        super("The game was stopped by the user");
    }

}
