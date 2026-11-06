package com.combats.exception;

/**
 * Бросается, когда в подземелье больше идти нельзя: не хватает единиц риска.
 * Это не ошибка, а сигнал {@link com.combats.be.TopLevelLogic} штатно завершить игру
 * с этим сообщением: перезагрузка страницы тут не поможет.
 */
public class DungeonIsOverException extends RuntimeException {

    public DungeonIsOverException(String message) {
        super(message);
    }

}
