package com.combats.exception;

/**
 * Бросается из циклов игры после паузы, когда пользователь отправил /resume.
 * Пока бот стоял на паузе, человек играл сам и мог уйти с той страницы, где бот остановился,
 * поэтому продолжать с того же места нельзя. Это не ошибка, а сигнал {@link com.combats.be.TopLevelLogic}
 * начать круг игры заново.
 */
public class GameResumedException extends RuntimeException {

    public GameResumedException() {
        super("The game was resumed after a pause");
    }

}
