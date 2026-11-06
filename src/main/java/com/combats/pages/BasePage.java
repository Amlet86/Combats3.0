package com.combats.pages;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import com.codeborne.selenide.SelenideElement;
import org.openqa.selenium.support.FindBy;

import static com.codeborne.selenide.ClickOptions.usingJavaScript;
import static com.codeborne.selenide.Condition.visible;
import static com.codeborne.selenide.Selenide.*;
import static com.combats.utils.GameControl.status;
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

    /** Сколько ждать перехода с таймером. Вживую видели ~9 с, берём с запасом. */
    private static final Duration MAX_PENDING_MOVE_TIME = Duration.ofSeconds(60);
    /**
     * Переход с таймером: «Вы перейдете в: Врата Душ (отмена)», где «отмена» — ссылка с clear_solo().
     * Пока она видна, персонаж ещё на старом месте, и повторный клик по переходу не нужен.
     */
    private final SelenideElement pendingMove = $$("a[onclick*='clear_solo']").findBy(visible);

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

    /**
     * Как {@link #humanClick}, но клик через JavaScript прямо по элементу, а не мышью по координатам.
     * Нужен, когда поверх кнопки может лежать другой элемент: подсказка приёма над окном
     * «Выход из боя», ночная картинка соседнего здания на улице. Обычный клик тогда падает
     * с «element click intercepted».
     */
    void humanJsClick(SelenideElement element) {
        waitAboutSomeSeconds(1);
        if (element != null && element.isDisplayed()) {
            element.click(usingJavaScript());
        }
    }

    /**
     * Ждёт, пока закончится переход с таймером («Вы перейдете в: … (отмена)»). Повторный клик
     * по переходу в это время мог перезапускать таймер, и бот не доходил до цели
     * (снято 22.10.2026: таймер ~9 с при нескольких переходах подряд).
     */
    void waitForPendingMove() {
        Instant deadline = Instant.now().plus(MAX_PENDING_MOVE_TIME);
        while (pendingMove.isDisplayed() && Instant.now().isBefore(deadline)) {
            status("Moving to the next place");
            sleep(1000);
        }
    }

    /**
     * Переходы по городу — картинки с {@code solo('oN', 'Название', '')}. Номера {@code oN} в каждой комнате
     * свои, поэтому ищем по названию.
     *
     * @param routeFromGoal названия мест от цели к началу пути
     * @return видимый переход, ближайший к цели, или null, если на этой странице маршрута нет
     */
    SelenideElement nearestTransition(List<String> routeFromGoal) {
        for (String place : routeFromGoal) {
            SelenideElement transition = $$("img[onclick*=\"'" + place + "'\"]").findBy(visible);
            if (transition.isDisplayed())
                return transition;
        }
        return null;
    }

    /**
     * Следующее окно {@code confirm()} в текущем фрейме будет подтверждено без показа. Окна вроде
     * «заплатить 40 ед. риска?» в браузере бота мешают: Selenium упирается в открытое окно, а встроенный
     * браузер Claude их вовсе не показывает и отвечает «Отмена» (снято 03.11.2026).
     */
    void acceptNextConfirm() {
        executeJavaScript("const original = window.confirm;"
                + "window.confirm = function () { window.confirm = original; return true; };");
    }

    @Override
    public void humanMoveOnTheMap(CharSequence keyToMove) {
        waitAboutSomeSeconds(1);
        body.sendKeys(keyToMove);
        waitAboutSomeSeconds(1);
    }

}
