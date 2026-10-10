package com.combats.utils;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Маршрут по подземелью для PvE: последовательность клавиш перемещения из файла в папке maps.
 */
public class MapNavigator {

    private static final Logger log = LoggerFactory.getLogger(MapNavigator.class);

    private static final String ROAD_IN_A_DUNGEON_FILE_PATH = "maps/DemonsCityThroughTop.txt";
    public static ArrayList<String> roadMapList = new ArrayList<>();

    /**
     * Читает маршрут из файла в {@link #roadMapList}: одна строка — одна клавиша перемещения.
     */
    public static void prepareRoadFile() {
        try (BufferedReader br = new BufferedReader(new FileReader(ROAD_IN_A_DUNGEON_FILE_PATH))) {
            while (br.ready())
                roadMapList.add(br.readLine());
        } catch (IOException e) {
            log.error("Can't read the road map file {}", ROAD_IN_A_DUNGEON_FILE_PATH, e);
        }
    }

}
