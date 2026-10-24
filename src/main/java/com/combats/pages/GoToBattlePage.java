package com.combats.pages;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.codeborne.selenide.SelenideElement;
import org.openqa.selenium.By;
import org.openqa.selenium.support.FindBy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.codeborne.selenide.Selenide.*;
import static com.combats.utils.GameControl.status;
import static com.combats.utils.Properties.getUserLogin;
import static com.combats.utils.Utils.*;

/**
 * Страница «Поединки»: заявки на хаотичные бои и конфликты.
 */
public class GoToBattlePage extends BasePage {

    private static final Logger log = LoggerFactory.getLogger(GoToBattlePage.class);

    /**
     * Сколько раз за одно ожидание боя можно вернуться в «Поединки», если игра выбросила персонажа
     * со страницы заявок (например, в город). Если выбрасывает снова и снова, дальше не пытаемся:
     * ошибку обработает игровой цикл.
     */
    private static final int MAX_RETURNS_TO_APPLICATIONS = 5;

    /**
     * Разброс силы противников для своей заявки: вариант списка «Сила противников», в тексте которого
     * есть эта строка («±50%»). Выбираем по тексту, потому что значения вариантов (value) не снимали.
     */
    static final String OPPONENTS_STRENGTH = "50%";

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

    /** Список «Сила противников» в форме своей заявки (±25% / ±50%). */
    @FindBy(css = "select[name=cena2]")
    private SelenideElement opponentsStrength;

    private By goCombat = By.cssSelector("[name=gocombat]");

    /**
     * Попадает в PvP-бой: вступает в чужую заявку с ближайшим стартом или подаёт свою
     * и обновляет страницу, пока бой не начнётся. Пока ждёт, показывает в статусе, сколько
     * минут осталось до начала боя.
     * <p>
     * Пока ждём, игра иногда выбрасывает персонажа со страницы заявок, например в город. Тогда
     * возвращаемся в «Поединки» → «Хаотичные» и ждём дальше: заявка либо ещё стоит, либо её
     * подадим заново.
     */
    public BattlePage enterToPvPBattle() {
        status("Looking for a battle");
        humanClick(battles);
        if (chaosBattle.isDisplayed()) {
            humanClick(chaosBattle);
            int returns = 0;
            while (true) {
                checkGameCanContinue();
                if (refreshBtn.isDisplayed()) {
                    waitOnApplicationsPage();
                    continue;
                }
                if (battleStarted())
                    break;
                // страница могла ещё грузиться после «Обновить»: даём ей время, прежде чем решать
                waitAboutSomeSeconds(3);
                if (refreshBtn.isDisplayed() || battleStarted())
                    continue;
                if (++returns > MAX_RETURNS_TO_APPLICATIONS) {
                    log.warn("Kicked out of the applications page {} times, stop going back", MAX_RETURNS_TO_APPLICATIONS);
                    break;
                }
                log.info("Kicked out of the applications page to {}, go back to «Поединки»", gameFramePath());
                status("Kicked out of the applications page, going back");
                humanClick(battles);
                humanClick(chaosBattle);
            }
        }
        if (conflictsBattle.isDisplayed()) {
            humanClick(conflictsBattle);
        }
        return page(BattlePage.class);
    }

    /**
     * Один шаг ожидания на странице хаотичных заявок: показывает время до начала боя, вступает
     * в заявку или подаёт свою, нажимает «Обновить» и ждёт.
     * <p>
     * После того как бот вступил в заявку или подал свою, шаг заканчивается: страница
     * перезагружается не сразу, и пока на экране старая, на ней ещё видна ссылка «Подать заявку».
     * Раньше бот тут же жал её, а к клику страница уже обновлялась, и ссылки не было:
     * «Element not found {//*[.='Подать заявку на хаотичный бой']}», хотя заявка уже подана.
     */
    private void waitOnApplicationsPage() {
        String pageText = $("body").getText();
        String minutes = minutesToStart(pageText, getUserLogin());
        status("Waiting for a chaotic battle to start" + (minutes == null ? "" : ": " + minutes + " min left"));
        // уже стоим в заявке: вступать в другую или подавать свою не нужно
        if (!isWaitingForOwnBattle(pageText) && (joinApplication() || submitApplication())) {
            waitAboutSomeSeconds(5);
            return;
        }
        if (refreshBtn.isDisplayed())
            refreshBtn.click();
        waitAboutSomeSeconds(10);
    }

    /**
     * Вступает в чужую заявку с ближайшим стартом.
     *
     * @return true, если нажали «Принять участие»
     */
    private boolean joinApplication() {
        if (!confirm.isDisplayed())
            return false;
        int number = chooseRadioWithMinTime();
        if (number < 0)
            return false;
        $$(goCombat).get(number).click();
        humanClick(confirm);
        log.info("Joined a chaotic battle application");
        return true;
    }

    /**
     * Подаёт свою заявку на хаотичный бой с разбросом силы противников ±50%.
     * Если такого варианта в списке нет, заявку всё равно подаём, с тем, что выбрано по умолчанию.
     *
     * @return true, если нажали «Подать заявку»
     */
    private boolean submitApplication() {
        if (!applicationChaos.isDisplayed())
            return false;
        applicationChaos.click();
        String strength = chooseOpponentsStrength();
        open.click();
        log.info("Submitted a chaotic battle application, opponents' strength {}", strength);
        return true;
    }

    /**
     * Выбирает в списке «Сила противников» вариант с {@link #OPPONENTS_STRENGTH}.
     *
     * @return текст выбранного варианта (как на странице)
     */
    private String chooseOpponentsStrength() {
        try {
            opponentsStrength.selectOptionContainingText(OPPONENTS_STRENGTH);
        } catch (RuntimeException e) {
            log.warn("Can't choose «{}» in the opponents' strength list, keep the default", OPPONENTS_STRENGTH, e);
        }
        try {
            return opponentsStrength.getSelectedOptionText();
        } catch (RuntimeException e) {
            return "unknown";
        }
    }

    /**
     * Персонаж уже в заявке: в шапке страницы «Ожидаем начала группового боя».
     */
    static boolean isWaitingForOwnBattle(String pageText) {
        return pageText != null && pageText.contains("Ожидаем начала");
    }

    /**
     * Бой начался, если основной фрейм перешёл на battle*.pl или в верхнем документе видна «Вперёд».
     * Второй признак нужен, потому что при старте боя игра иногда перезагружает весь верхний
     * документ, и адрес фрейма прочитать не получается.
     */
    private static boolean battleStarted() {
        if (isBattlePage(gameFramePath()))
            return true;
        try {
            Boolean commitVisible = executeJavaScript(
                    "const b = top.document.querySelector('button[action=commit]');"
                            + "return !!b && b.offsetWidth > 0 && getComputedStyle(b).visibility !== 'hidden';");
            return Boolean.TRUE.equals(commitVisible);
        } catch (RuntimeException e) {
            return false;
        }
    }

    /**
     * @return путь страницы в основном фрейме игры («/zayavka.pl», «/main.pl», «/battle4.pl») или пустая
     * строка, если фрейм сейчас перезагружается
     */
    private static String gameFramePath() {
        try {
            String path = executeJavaScript("return location.pathname;");
            return path == null ? "" : path;
        } catch (RuntimeException e) {
            return "";
        }
    }

    /**
     * Бой начался: основной фрейм переходит на battle.pl, battle2.pl, battle3.pl, battle4.pl…
     */
    static boolean isBattlePage(String path) {
        return path != null && path.matches("/battle\\d*\\.pl");
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
