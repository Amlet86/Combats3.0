package com.combats;

import com.combats.be.TopLevelLogic;
import com.combats.fe.LoginForm;

/**
 * Точка входа: открывает форму настроек бота.
 * <p>
 * Запуск собранного jar из консоли:
 * java -jar target/Combats-&lt;версия из pom.xml&gt;.jar
 */
public class Start {

    /**
     * Показывает форму {@link LoginForm}, игра запускается кнопкой «Start».
     *
     * @param args не используются
     */
    public static void main(String[] args) {
        LoginForm loginForm = new LoginForm();
        loginForm.setVisible(true);
    }

}
