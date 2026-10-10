package com.combats.pages;

import java.time.Duration;

import org.openqa.selenium.By;
import org.openqa.selenium.TimeoutException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.codeborne.selenide.Selenide.*;

/**
 * Стартовая страница со входом в игру.
 */
public class LoginPage extends BasePage {

    private static final Logger log = LoggerFactory.getLogger(LoginPage.class);

    private static final String ERROR_PAGE_TITLE = "Произошла ошибка";
    private static final Duration LOGIN_TIMEOUT = Duration.ofSeconds(30);

    /**
     * Заполняет логин и пароль и нажимает «Войти».
     */
    public void login(String login, String password) {
        $("[name=login]").setValue(login);
        $("[name=psw]").setValue(password);
        $("[value=' Войти ']").click();
    }

    /**
     * Ждёт после входа, пока загрузится игра или страница ошибки.
     *
     * @return true, если игра загрузилась
     */
    public boolean isLoggedIn() {
        By gameFrame = By.cssSelector(GAME_FRAME_LOCATOR);
        try {
            Wait().withTimeout(LOGIN_TIMEOUT).until(driver ->
                    ERROR_PAGE_TITLE.equals(driver.getTitle()) || !driver.findElements(gameFrame).isEmpty());
        } catch (TimeoutException e) {
            log.error("The game isn't loaded in {} seconds after login, page title: {}", LOGIN_TIMEOUT.toSeconds(), title());
            return false;
        }
        if (ERROR_PAGE_TITLE.equals(title())) {
            log.error("Login failed: the game shows the error page");
            return false;
        }
        return true;
    }

}
