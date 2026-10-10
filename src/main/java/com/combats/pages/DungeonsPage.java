package com.combats.pages;

import java.util.ArrayList;

import com.codeborne.selenide.SelenideElement;

import static com.codeborne.selenide.Selenide.$;
import static com.codeborne.selenide.Selenide.page;
import static com.combats.utils.MapNavigator.roadMapList;
import static com.combats.utils.GameControl.status;
import static com.combats.utils.Utils.checkGameCanContinue;
import static com.combats.utils.Utils.waitAboutSomeSeconds;

/**
 * Подземелье для PvE: ходьба по маршруту из {@link com.combats.utils.MapNavigator} до встречи с монстром.
 */
public class DungeonsPage extends BasePage {

    private SelenideElement map = $("div#room");
    private SelenideElement imageOfMap = $("div#brodilka");
    private SelenideElement anyNPC = $("h3");

    private ArrayList<SelenideElement> dungeonCases = new ArrayList<SelenideElement>() {{
        add(map);
        add(anyNPC);
    }};

    /**
     * Ходит по подземелью, пока персонаж в нём: когда начинается бой, карта пропадает и цикл заканчивается.
     */
    public BattlePage walkOnTheDungeon() {
        status("Walking in the dungeon");
        while (AmIInTheDungeon()) {
            checkGameCanContinue();
            if (map.isDisplayed())
                moveOnTheMap();
            else
                waitAboutSomeSeconds(3);
        }
        return page(BattlePage.class);
    }

    /**
     * @return true, если на странице есть карта подземелья или NPC
     */
    private boolean AmIInTheDungeon() {
        boolean inTheDungeon = false;
        for (SelenideElement element : dungeonCases) {
            if (element.isDisplayed())
                inTheDungeon = true;
        }
        return inTheDungeon;
    }

    /**
     * Делает шаг по маршруту; когда маршрут закончился, идёт вперёд.
     * Шаг убирается из маршрута, только если персонаж остался на карте.
     */
    private void moveOnTheMap() {
        humanMoveOnTheMap("z");
        if (!roadMapList.isEmpty()) {
            humanMoveOnTheMap(roadMapList.get(0));
            if(map.isDisplayed())
            roadMapList.remove(0);
        }
        else
            humanMoveOnTheMap("w");
        humanMoveOnTheMap("f");
    }

}
