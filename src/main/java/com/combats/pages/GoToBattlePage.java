package com.combats.pages;

import com.codeborne.selenide.SelenideElement;
import org.openqa.selenium.By;
import org.openqa.selenium.support.FindBy;

import static com.codeborne.selenide.Selenide.*;
import static com.combats.utils.GameControl.status;
import static com.combats.utils.Utils.*;

/**
 * Страница «Поединки»: заявки на хаотичные бои и конфликты.
 */
public class GoToBattlePage extends BasePage {

    @FindBy(css = "td.menutop")
    private SelenideElement menutop;

    @FindBy(css = "input[value='Поединки']")
    private SelenideElement battles;

    @FindBy(xpath = "//*[.='Хаотичные']")
    private SelenideElement chaosBattle;

    @FindBy(xpath = "//*[.='Конфликты']")
    private SelenideElement conflictsBattle;

    @FindBy(css = "[value='Обновить']")
    private SelenideElement refreshBtn;

    @FindBy(css = "[name=confirm1]")
    private SelenideElement confirm;

    @FindBy(xpath = "//*[.='Подать заявку на хаотичный бой']")
    private SelenideElement applicationChaos;

    @FindBy(css = "[name=open]")
    private SelenideElement open;

    private By goCombat = By.cssSelector("[name=gocombat]");

    /**
     * Попадает в PvP-бой: вступает в чужую заявку с ближайшим стартом или подаёт свою
     * и обновляет страницу, пока бой не начнётся.
     */
    public BattlePage enterToPvPBattle() {
        status("Looking for a battle");
        humanClick(battles);
        if (chaosBattle.isDisplayed()) {
            humanClick(chaosBattle);
            while (refreshBtn.isDisplayed()) {
                checkGameCanContinue();
                status("Waiting for a chaotic battle to start");
                if (confirm.isDisplayed()) {
                    int number = chooseRadioWithMinTime();
                    if (number >= 0) {
                        $$(goCombat).get(number).click();
                        humanClick(confirm);
                    }
                }
                if (applicationChaos.isDisplayed()) {
                    applicationChaos.click();
                    open.click();
                }
                if (refreshBtn.isDisplayed())
                    refreshBtn.click();
                waitAboutSomeSeconds(10);
            }
        }
        if (conflictsBattle.isDisplayed()) {
            humanClick(conflictsBattle);
        }
        return page(BattlePage.class);
    }

}
