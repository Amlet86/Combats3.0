package com.combats.pages;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import com.codeborne.selenide.SelenideElement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.codeborne.selenide.ClickOptions.usingJavaScript;
import static com.codeborne.selenide.Condition.visible;
import static com.codeborne.selenide.Selenide.*;
import static com.combats.utils.GameControl.status;
import static com.combats.utils.Utils.checkGameCanContinue;
import static com.combats.utils.Utils.parseHpPercent;
import static com.combats.utils.Utils.waitAboutSomeSeconds;
import static com.combats.utils.Utils.waiting;

/**
 * Город: выход из боя, ежедневное задание, возврат от врат и ожидание восстановления HP.
 */
public class CityPage extends BasePage {

    private static final Logger log = LoggerFactory.getLogger(CityPage.class);

    private static final int RECOVERED_HP_PERCENT = 80;
    private static final Duration MAX_HP_RECOVERY_TIME = Duration.ofMinutes(15);
    private static final Duration HP_CHECK_INTERVAL = Duration.ofSeconds(10);
    private static final int ATTEMPTS_TO_FIND_HP = 6;

    /** Переходы к Залу воинов, от цели к началу пути: на каждом шаге берём первый видимый. */
    private static final List<String> ROUTE_TO_WARRIORS_HALL =
            List.of("Зал воинов", "Центральная грибница", "Овраг Возрождения");
    private static final int MAX_STEPS_TO_WARRIORS_HALL = 6;
    /** Сколько ждать перехода с таймером. Вживую видели ~9 с, берём с запасом. */
    private static final Duration MAX_PENDING_MOVE_TIME = Duration.ofSeconds(60);

    private SelenideElement battlesBtn = $("input[value='Поединки']");
    /**
     * Переход с таймером: «Вы перейдете в: Врата Душ (отмена)», где «отмена» — ссылка с clear_solo().
     * Пока она видна, персонаж ещё на старом месте, и повторный клик по переходу не нужен.
     */
    private SelenideElement pendingMove = $$("a[onclick*='clear_solo']").findBy(visible);
    private SelenideElement hp = $("#HP");
    private SelenideElement gameover = $("[action=gameover]");
    private SelenideElement battleEnd = $("button.UserBattleEnd");

    /**
     * Нажимает «Вернуться», если на экране остался прошлый бой, и переходит во фрейм игры.
     */
    public CityPage exitFromBattle() {
        humanClick(gameover);
        humanClick(battleEnd);
        switchToGameFrame();
        return page(CityPage.class);
    }

    /**
     * Ждёт, пока HP восстановится после боя.
     * Если HP на странице не найдено, ждёт около 5 минут, как раньше.
     */
    public void waitForHpRecovery() {
        switchToGameFrame();
        try {
            Instant deadline = Instant.now().plus(MAX_HP_RECOVERY_TIME);
            int attemptsToFindHp = 0;
            while (Instant.now().isBefore(deadline)) {
                checkGameCanContinue();
                // пока ждём, может закончиться бой, из которого бот вышел после смерти
                ChatPage.reportNewBattleResults();
                Integer hpPercent = hp.isDisplayed() ? parseHpPercent(hp.getText()) : null;
                status(hpPercent == null ? "Waiting for HP recovery" : "Waiting for HP recovery: " + hpPercent + "%");
                if (hpPercent == null) {
                    if (++attemptsToFindHp >= ATTEMPTS_TO_FIND_HP) {
                        log.warn("HP isn't found on the page, wait about 5 minutes");
                        waiting(300, 310);
                        return;
                    }
                } else if (hpPercent >= RECOVERED_HP_PERCENT) {
                    log.info("HP is recovered: {}", hp.getText());
                    return;
                }
                waitAboutSomeSeconds((int) HP_CHECK_INTERVAL.toSeconds());
            }
            log.warn("HP hasn't been recovered in {} minutes: {}", MAX_HP_RECOVERY_TIME.toMinutes(), hp.getText());
        } finally {
            // exitFromBattle() ожидает, что активен основной документ
            switchTo().defaultContent();
        }
    }

    /**
     * Готовит персонажа к бою в городе: берёт ежедневное задание и возвращается от врат.
     */
    public GoToBattlePage moveInTheCity() {
        takeDailyQuest();
        returnFromSoulGate();
        return page(GoToBattlePage.class);
    }

    /**
     * Берёт ежедневное задание, если игра показала окно с ним.
     */
    private void takeDailyQuest() {
        if ($("#dailypopup").isDisplayed())
            $x("//*[.='Взять задание']").click();
    }

    /**
     * Возвращает персонажа в Зал воинов, если после смерти его выбило к Личу (Врата Душ) или он
     * оказался на улице: там нет кнопки «Поединки».
     * <p>
     * Идём по названиям переходов ({@code solo('oN', 'Название', '')}), а не по номерам картинок:
     * число и порядок картинок на улицах меняются. Раньше бот жал четвёртую {@code sn_club.gif}
     * на Центральной грибнице, а их там стало три, и он падал. Маршрут Sun city (снято 22.10.2026):
     * Врата Душ → «Овраг Возрождения» → «Центральная грибница» → «Зал воинов». На каждом шаге
     * выбираем самый близкий к цели переход из {@link #ROUTE_TO_WARRIORS_HALL}.
     */
    private void returnFromSoulGate() {
        for (int step = 0; step < MAX_STEPS_TO_WARRIORS_HALL && !battlesBtn.isDisplayed(); step++) {
            checkGameCanContinue();
            waitForPendingMove();
            if (battlesBtn.isDisplayed())
                break;
            SelenideElement next = nextStepToWarriorsHall();
            if (next == null)
                return;
            status("Returning to the Warriors' Hall");
            log.info("Not in the club after the battle, go to «{}»", next.getAttribute("onclick"));
            // кликаем через JavaScript, а не мышью: ночью на Центральной грибнице «Башня рыцарей-магов»
            // лежит поверх «Зала воинов» в тех же координатах, и клик мышью доставался ей
            // (element click intercepted, 22.10.2026). Игре всё равно: onclick вызывает solo(...)
            waitAboutSomeSeconds(1);
            next.click(usingJavaScript());
            // переход бывает с таймером: ждём, пока он закончится, и не жмём его повторно
            waitAboutSomeSeconds(2);
            waitForPendingMove();
        }
        if (!battlesBtn.isDisplayed())
            log.warn("Haven't returned to the Warriors' Hall in {} steps", MAX_STEPS_TO_WARRIORS_HALL);
    }

    /**
     * Ждёт, пока закончится переход с таймером («Вы перейдете в: … (отмена)»). Повторный клик
     * по переходу в это время мог перезапускать таймер, и бот не доходил до Зала воинов
     * (снято 22.10.2026: таймер ~9 с при нескольких переходах подряд).
     */
    private void waitForPendingMove() {
        Instant deadline = Instant.now().plus(MAX_PENDING_MOVE_TIME);
        while (pendingMove.isDisplayed() && Instant.now().isBefore(deadline)) {
            status("Moving to the next place");
            sleep(1000);
        }
    }

    /**
     * @return переход, ближайший к Залу воинов, или null, если на этой странице маршрута нет
     */
    private SelenideElement nextStepToWarriorsHall() {
        for (String place : ROUTE_TO_WARRIORS_HALL) {
            SelenideElement transition = $$("img[onclick*=\"'" + place + "'\"]").findBy(visible);
            if (transition.isDisplayed())
                return transition;
        }
        // Demons city: маршрут по названиям не снимали, жмём ворота как раньше
        SelenideElement demonsGate = $(FIRST_PART_OF_IMAGES_LOCATOR + "dem_new_gates.gif']");
        return demonsGate.isDisplayed() ? demonsGate : null;
    }

    /**
     * Переход к странице подземелья. Персонаж должен уже находиться в подземелье.
     */
    public DungeonsPage switchToTheDungeon() {
        return page(DungeonsPage.class);
    }

}
