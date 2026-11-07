package com.combats.be;

import com.codeborne.selenide.WebDriverRunner;
import com.combats.pages.BasePage;
import com.combats.pages.BattlePage;
import com.combats.pages.CityPage;
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
     * Лига: выход из прошлого боя, восстановление в Комнате Знахаря, заявка 1 на 1 в Таверне и сам бой.
     */
    void league() {
        new CityPage().exitFromBattle()
            .recoverInDoctorRoom()
            .enterToBattle()
            .fight();
    }

    /**
     * PvE: выход из прошлого боя, вход в подземелье (если персонаж ещё не в нём), обход этажа до боя
     * и бой. Если поход закончился без боя (этаж обойдён, персонаж погиб), следующий круг войдёт заново.
     */
    void PvE() {
        CityPage cityPage = new CityPage();
        BattlePage battle = cityPage.exitFromBattle()
            .switchToTheDungeon()
            .walkOnTheDungeon();
        if (battle != null)
            battle.fight();
    }

    /**
     * Закрывает браузер, если он был запущен.
     */
    void quitWebDriver() {
        if (WebDriverRunner.hasWebDriverStarted())
            WebDriverRunner.getWebDriver().quit();
    }

}

