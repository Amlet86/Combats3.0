package com.combats.pages;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;

import com.codeborne.selenide.SelenideElement;
import com.combats.exception.DungeonIsOverException;
import com.combats.utils.GameControl;
import com.combats.utils.Notifications;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.codeborne.selenide.Selenide.$;
import static com.codeborne.selenide.Selenide.executeJavaScript;
import static com.combats.utils.GameControl.status;
import static com.combats.utils.Utils.checkGameCanContinue;
import static com.combats.utils.Utils.takeScreenshot;
import static com.combats.utils.Utils.waitAboutSomeSeconds;

/**
 * Путь в Грибницу (Sun city, снято 03.11.2026): по улицам до Мухатора, вход, страница {@code dungeon.pl}
 * («Создать группу» → «Начать» → очередь), после чего игра перезагружает страницу и показывает карту.
 */
public class DungeonEntrancePage extends BasePage {

    private static final Logger log = LoggerFactory.getLogger(DungeonEntrancePage.class);

    /**
     * Переходы ко входу, от цели к началу пути: на каждом шаге берём первый видимый. Путь снят
     * 03.11.2026 от Оврага Возрождения (сюда приходят от Врат Душ после смерти).
     */
    private static final List<String> ROUTE_TO_ENTRANCE = List.of("Грибница", "Грибная поляна", "Странное Место",
            "Грибная лужайка", "Центральная грибница", "Овраг Возрождения");
    private static final int MAX_STEPS = 15;
    /** В очереди видели «Место в очереди: 1», сколько она длится, не знаем: ждём с запасом. */
    private static final Duration MAX_QUEUE_TIME = Duration.ofMinutes(15);
    private static final int QUEUE_CHECK_SECONDS = 10;
    /**
     * Сколько ждать, если на странице нет ни подземелья, ни дороги к нему. После боя в подземелье фрейм
     * бывает на странице {@code main.pl?top=…} (04.11.2026 бот с первого взгляда на неё решил, что дороги
     * нет, и закончил игру).
     */
    private static final Duration MAX_WAIT_FOR_ROUTE = Duration.ofSeconds(20);

    private final SelenideElement createGroupBtn = $("input[name=open][value='Создать группу']");
    private final SelenideElement startBtn = $("input[name=start]");
    private final SelenideElement leaveQueueBtn = $("input[name=stop]");
    private final SelenideElement refreshBtn = $("input[value='Обновить']");
    /** «Вернуться» со страницы заданий к самой странице входа. */
    private final SelenideElement backBtn = $("input[value='Вернуться']");

    /**
     * Доводит персонажа до карты подземелья.
     *
     * @throws DungeonIsOverException если не хватает единиц риска
     * @throws IllegalStateException  если дороги ко входу не видно и позвать человека некого
     */
    public void enter() {
        status("Going to the dungeon");
        Instant queueDeadline = null;
        Instant routeDeadline = null;
        boolean atEntrance = false;
        int steps = 0;
        while (steps < MAX_STEPS) {
            checkGameCanContinue();
            DungeonsPage.Place place = DungeonsPage.where();
            if (place == DungeonsPage.Place.DUNGEON) {
                // без страницы входа это тот же поход: фрейм просто дольше возвращался на карту
                if (atEntrance) {
                    DungeonsPage.startRun();
                    log.info("Entered the dungeon");
                    Notifications.message("Entered the dungeon");
                } else
                    log.info("The character is still in the dungeon, go on");
                return;
            }
            if (place == DungeonsPage.Place.BATTLE)
                return;
            if (place == DungeonsPage.Place.LOADING) {
                waitAboutSomeSeconds(2);
                continue;
            }
            if ("/dungeon.pl".equals(executeJavaScript("return location.pathname;"))) {
                atEntrance = true;
                if (leaveQueueBtn.isDisplayed()) {
                    if (queueDeadline == null)
                        queueDeadline = Instant.now().plus(MAX_QUEUE_TIME);
                    else if (Instant.now().isAfter(queueDeadline))
                        throw new IllegalStateException("Have been in the dungeon queue for "
                                + MAX_QUEUE_TIME.toMinutes() + " minutes");
                    status("In the dungeon queue");
                    waitAboutSomeSeconds(QUEUE_CHECK_SECONDS);
                    humanClick(refreshBtn);
                    continue;
                }
                onEntrancePage();
            } else {
                DungeonsPage.askHumanIfCharacterTalks();
                if (walkToEntrance()) {
                    routeDeadline = null;
                } else {
                    if (routeDeadline == null)
                        routeDeadline = Instant.now().plus(MAX_WAIT_FOR_ROUTE);
                    else if (Instant.now().isAfter(routeDeadline))
                        askHumanAboutUnknownPage();
                    waitAboutSomeSeconds(2);
                    continue;
                }
            }
            steps++;
            waitAboutSomeSeconds(2);
        }
        // 05.11.2026 бот стоял на странице входа без нужных кнопок, а по логу причины не понять
        GoToBattlePage.savePageSnapshot("dungeon-no-entry");
        throw new IllegalStateException("Haven't got into the dungeon in " + MAX_STEPS + " steps");
    }

    /**
     * Страница входа: «Начать», если группа уже есть, иначе проверка риска и «Создать группу».
     */
    private void onEntrancePage() {
        if (startBtn.isDisplayed()) {
            status("Starting the dungeon");
            humanClick(startBtn);
        } else if (createGroupBtn.isDisplayed()) {
            DungeonRisk.parse($("body").getText()).ifPresentOrElse(risk -> {
                log.info("Dungeon risk: {} of {} available", risk.cost(), risk.available());
                if (!risk.enough())
                    throw new DungeonIsOverException(String.format(Locale.ROOT,
                            "Not enough risk for the dungeon: %.2f available, %.0f needed.", risk.available(), risk.cost()));
            }, () -> log.warn("The dungeon risk isn't found on the page"));
            status("Creating a group for the dungeon");
            // «Вы уверены, что хотите заплатить за участие 40 ед. риска?»
            acceptNextConfirm();
            humanClick(createGroupBtn);
        } else if (backBtn.isDisplayed()) {
            humanClick(backBtn);
        }
    }

    /**
     * Один переход по улицам к Мухатору. Переходы бывают с таймером, его пережидаем.
     *
     * @return false, если на странице нет дороги к подземелью
     */
    private boolean walkToEntrance() {
        waitForPendingMove();
        SelenideElement next = nearestTransition(ROUTE_TO_ENTRANCE);
        if (next == null)
            return false;
        status("Going to the dungeon");
        log.info("Go to «{}»", next.getAttribute("onclick"));
        humanJsClick(next);
        waitAboutSomeSeconds(2);
        waitForPendingMove();
        return true;
    }

    /**
     * Дороги не появилось: бот на незнакомой странице. Разговор с персонажем он узнаёт сразу
     * ({@link DungeonsPage#askHumanIfCharacterTalks}), но проверка может выглядеть и иначе, поэтому страницу
     * не трогает и не перезагружает, а зовёт человека и ждёт /resume.
     *
     * @throws IllegalStateException если позвать некого (нет Telegram): дальше как при обычной ошибке
     */
    private static void askHumanAboutUnknownPage() {
        GoToBattlePage.savePageSnapshot("no-way-to-dungeon");
        String page = executeJavaScript("return location.pathname + location.search;");
        GameControl.pauseForHuman("The bot needs you: it is on an unknown page " + page
                + ". Maybe a character is asking questions. Please connect and answer yourself, then send /resume.",
                takeScreenshot());
        throw new IllegalStateException("Can't find the way to the dungeon from " + page
                + ": the bot knows the way only from the Sun city streets.");
    }

}
