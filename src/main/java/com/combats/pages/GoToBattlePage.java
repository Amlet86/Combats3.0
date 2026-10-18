package com.combats.pages;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.codeborne.selenide.SelenideElement;
import org.openqa.selenium.By;
import org.openqa.selenium.support.FindBy;

import static com.codeborne.selenide.Selenide.*;
import static com.combats.utils.GameControl.status;
import static com.combats.utils.Properties.getUserLogin;
import static com.combats.utils.Utils.*;

/**
 * Страница «Поединки»: заявки на хаотичные бои и конфликты.
 */
public class GoToBattlePage extends BasePage {

    /** Заявка: (участники) (уровни) Бой начнется через N мин. — группа 1: участники, группа 2: минуты. */
    private static final Pattern APPLICATION =
            Pattern.compile("\\(([^()]*)\\)\\s*\\(\\d+-\\d+\\)\\s*Бой начн[её]тся через\\s*([\\d.,]+)\\s*мин");
    /** Шапка страницы, пока ждём свой бой: «Ваш бой начнется через 2.2 мин.». */
    private static final Pattern OWN_BATTLE = Pattern.compile("Ваш бой начн[её]тся через\\s*([\\d.,]+)\\s*мин");

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
     * и обновляет страницу, пока бой не начнётся. Пока ждёт, показывает в статусе, сколько
     * минут осталось до начала боя.
     */
    public BattlePage enterToPvPBattle() {
        status("Looking for a battle");
        humanClick(battles);
        if (chaosBattle.isDisplayed()) {
            humanClick(chaosBattle);
            while (refreshBtn.isDisplayed()) {
                checkGameCanContinue();
                String minutes = minutesToStart($("body").getText(), getUserLogin());
                status("Waiting for a chaotic battle to start" + (minutes == null ? "" : ": " + minutes + " min left"));
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

    /**
     * Сколько минут осталось до начала боя, по тексту страницы заявок. Берём из своей заявки,
     * сразу после списка участников: «23:42 (ЧУ-Ж-ОЙ [8], …, Amlett [8]) (7-9) Бой начнется через 2.2 мин., …».
     * Если свою заявку найти не удалось, берём строку в шапке «Ваш бой начнется через 2.2 мин.».
     *
     * @return минуты как на странице («2.2») или null, если времени на странице нет
     */
    static String minutesToStart(String pageText, String login) {
        if (pageText == null)
            return null;
        if (login != null && !login.isBlank()) {
            Matcher application = APPLICATION.matcher(pageText);
            while (application.find())
                if (application.group(1).contains(login.trim() + " ["))
                    return application.group(2);
        }
        Matcher own = OWN_BATTLE.matcher(pageText);
        return own.find() ? own.group(1) : null;
    }

}
