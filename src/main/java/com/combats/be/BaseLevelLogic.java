package com.combats.be;

import com.codeborne.selenide.WebDriverRunner;
import com.combats.pages.BasePage;
import com.combats.pages.BattlePage;
import com.combats.pages.CityPage;
import com.combats.pages.DungeonsPage;
import com.combats.pages.GoToBattlePage;
import com.combats.pages.LoginPage;
import com.combats.utils.FileWorker;

import static com.combats.utils.Properties.getUserLogin;
import static com.combats.utils.Properties.getUserPassword;

/**
 * Базовые шаги игры, из которых {@link TopLevelLogic} собирает игровой цикл.
 */
class BaseLevelLogic {

    /**
     * Открывает стартовую страницу игры.
     */
    void startBrowser() {
        BasePage basePage = new BasePage();
        basePage.openBasePage();
    }

    /**
     * Входит в игру под логином и паролем из формы.
     *
     * @return true, если вход успешен; только в этом случае данные пользователя сохраняются в файл
     */
    boolean loginInGame() {
        LoginPage loginPage = new LoginPage();
        loginPage.login(getUserLogin(), getUserPassword());
        if (!loginPage.isLoggedIn())
            return false;
        FileWorker.writeUserDataFile();
        return true;
    }

    /**
     * PvP: выход из прошлого боя, подготовка в городе, заявка на хаотичный бой и сам бой.
     */
    void PvP() {
        CityPage cityPage = new CityPage();
        cityPage.exitFromBattle()
            .moveInTheCity()
            .enterToPvPBattle()
            .fight();
    }

    /**
     * PvE: выход из прошлого боя, ходьба по подземелью до встречи с монстром и бой.
     */
    void PvE() {
        CityPage cityPage = new CityPage();
        cityPage.exitFromBattle()
            .switchToTheDungeon()
            .walkOnTheDungeon()
            .fight();
    }

    /**
     * Закрывает браузер, если он был запущен.
     */
    void quitWebDriver() {
        if (WebDriverRunner.hasWebDriverStarted())
            WebDriverRunner.getWebDriver().quit();
    }

    /**
     * Заготовка под скриншот для Telegram. Пока возвращает только адрес текущей страницы.
     *
     * @return адрес текущей страницы или null, если браузер не запущен
     */
    String takeScreenshot() {
        if (WebDriverRunner.hasWebDriverStarted())
            return WebDriverRunner.getWebDriver().getCurrentUrl();
        else
            return null;
    }

}

