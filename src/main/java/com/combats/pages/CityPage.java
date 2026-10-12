package com.combats.pages;

import java.time.Duration;
import java.time.Instant;

import com.codeborne.selenide.SelenideElement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

    private static final int RECOVERED_HP_PERCENT = 90;
    private static final Duration MAX_HP_RECOVERY_TIME = Duration.ofMinutes(15);
    private static final Duration HP_CHECK_INTERVAL = Duration.ofSeconds(10);
    private static final int ATTEMPTS_TO_FIND_HP = 6;

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
     * Возвращает персонажа в клуб, если после боя он оказался у врат или на улице города.
     */
    private void returnFromSoulGate() {
        if ($(FIRST_PART_OF_IMAGES_LOCATOR + "sun_109_npc.gif']").isDisplayed()) {
            waitAboutSomeSeconds(14);
            $(FIRST_PART_OF_IMAGES_LOCATOR + "sun_new_gate.gif']").click();
        }
        if ($$(FIRST_PART_OF_IMAGES_LOCATOR + "sn_arrow.gif']").get(1).isDisplayed()) {
            waitAboutSomeSeconds(10);
            $$(FIRST_PART_OF_IMAGES_LOCATOR + "sn_arrow.gif']").get(1).click();
        }
        if ($(FIRST_PART_OF_IMAGES_LOCATOR + "sn_club.gif']").isDisplayed()) {
            waitAboutSomeSeconds(10);
            $$(FIRST_PART_OF_IMAGES_LOCATOR + "sn_club.gif']").get(3).click();
        }
        if($(FIRST_PART_OF_IMAGES_LOCATOR + "dem_new_gates.gif']").isDisplayed()){
            waitAboutSomeSeconds(14);
            $(FIRST_PART_OF_IMAGES_LOCATOR + "dem_new_gates.gif']").click();
        }
    }

    /**
     * Переход к странице подземелья. Персонаж должен уже находиться в подземелье.
     */
    public DungeonsPage switchToTheDungeon() {
        return page(DungeonsPage.class);
    }

}
