package com.combats.pages;

import com.codeborne.selenide.SelenideElement;
import org.openqa.selenium.support.FindBy;

import static com.codeborne.selenide.Selenide.*;
import static com.combats.utils.Utils.waitAboutSomeSeconds;

/**
 * Базовая страница игры: общие локаторы и действия для всех страниц (Page Object).
 */
public class BasePage implements BaseActions {

    static final String BASE_URL = "http://www.combats.com/";
    static final String FIRST_PART_OF_IMAGES_LOCATOR = "[src*='/i/images/subimages/";
    static final String GAME_FRAME_LOCATOR = "[onload='top.User.Framework.MainOnLoad( )']";

    @FindBy(css = "body")
    SelenideElement body;

    @FindBy(css = "span#effs")
    SelenideElement iconsOfEffectsFromUserInformation;

    @FindBy(css = "div#ione")
    SelenideElement frameOfTheCity;

    /**
     * Открывает стартовую страницу игры со входом.
     */
    public void openBasePage() {
        open(BASE_URL);
    }

    /**
     * Переключается в iframe, где игра показывает город, заявки и подземелье.
     * Сам экран боя находится в основном документе, а не в этом фрейме.
     */
    void switchToGameFrame() {
        switchTo().frame($(GAME_FRAME_LOCATOR));
    }

    @Override
    public void humanClick(SelenideElement element) {
        waitAboutSomeSeconds(1);
        if (element != null && element.isDisplayed()) {
            element.click();
        }
    }

    @Override
    public void humanMoveOnTheMap(CharSequence keyToMove) {
        waitAboutSomeSeconds(1);
        body.sendKeys(keyToMove);
        waitAboutSomeSeconds(1);
    }

}
