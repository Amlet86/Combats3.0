package com.combats.utils;

import com.codeborne.selenide.WebDriverRunner;
import com.combats.exception.GameTimeIsOverException;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.TakesScreenshot;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.combats.utils.Properties.getEndTimeOfTheGame;
import static java.lang.Thread.sleep;

/**
 * Вспомогательные методы: время игры, разбор данных со страницы, случайные паузы «как у человека».
 */
public class Utils {

    private static final Logger log = LoggerFactory.getLogger(Utils.class);

    private static final Pattern HP_PATTERN = Pattern.compile("(\\d+)\\s*/\\s*(\\d+)");

    /**
     * @return true, если время окончания игры ещё не наступило или не задано
     */
    public static boolean hasGameTime() {
        return hasGameTime(getEndTimeOfTheGame(), LocalDateTime.now());
    }

    /**
     * Вариант {@link #hasGameTime()} с явным текущим временем, чтобы логику можно было проверить тестами.
     *
     * @param endTime время окончания игры, null — без ограничения
     * @param now     текущее время
     * @return true, если игру можно продолжать
     */
    static boolean hasGameTime(LocalDateTime endTime, LocalDateTime now) {
        return endTime == null || now.isBefore(endTime);
    }

    /**
     * Для долгих циклов ожидания: останавливает игру, если пользователь нажал «Stop»
     * или наступило время окончания игры.
     *
     * @throws com.combats.exception.GameStoppedException если пользователь нажал «Stop»
     * @throws GameTimeIsOverException                     если время окончания игры наступило
     */
    public static void checkGameCanContinue() {
        GameControl.checkStop();
        if (!hasGameTime())
            throw new GameTimeIsOverException();
    }

    /**
     * Снимает скриншот окна браузера. Вызывать только из потока игры: Selenide привязывает браузер к нему.
     *
     * @return картинка PNG или null, если браузер не запущен или скриншот не получился
     */
    public static byte[] takeScreenshot() {
        if (!WebDriverRunner.hasWebDriverStarted())
            return null;
        try {
            return ((TakesScreenshot) WebDriverRunner.getWebDriver()).getScreenshotAs(OutputType.BYTES);
        } catch (RuntimeException e) {
            log.warn("Can't take a screenshot", e);
            return null;
        }
    }

    /**
     * Переводит HP со страницы в проценты.
     *
     * @param hpText текст вида "65/131" (текущее HP / максимальное HP)
     * @return текущее HP в процентах от максимального (с округлением вниз) или null, если текст не похож на HP
     */
    public static Integer parseHpPercent(String hpText) {
        Matcher matcher = HP_PATTERN.matcher(hpText == null ? "" : hpText);
        if (!matcher.find())
            return null;
        int current = Integer.parseInt(matcher.group(1));
        int max = Integer.parseInt(matcher.group(2));
        if (max == 0)
            return null;
        return current * 100 / max;
    }

    /**
     * @return случайное число миллисекунд в диапазоне от from до to секунд
     */
    private static int getRandomMultiplyThousand(int from, int to) {
        return new Random().nextInt(to * 1000 - from * 1000) + from * 1000;
    }

    /**
     * Пауза примерно на заданное число секунд (±10%), чтобы действия бота не выглядели механическими.
     *
     * @param seconds примерная длительность паузы в секундах
     */
    public static void waitAboutSomeSeconds(int seconds){
        int minWaitTime = (int) (seconds * 1000 / 1.1);
        int maxWaitTime = (int) (seconds * 1000 / 0.9);
        int exactWaitTime = new Random().nextInt(maxWaitTime - minWaitTime) + minWaitTime;
        try {
            sleep(exactWaitTime);
        } catch (InterruptedException e) {
            interrupted(e);
        }
    }

    /**
     * @return случайное число от from (включительно) до to (не включительно)
     */
    public static int getRandomInt(int from, int to) {
        return new Random().nextInt(to - from) + from;
    }

    /**
     * Пауза на случайное время от from до to секунд.
     */
    public static void waiting(int from, int to) {
        try {
            sleep(getRandomMultiplyThousand(from, to));
        } catch (InterruptedException e) {
            interrupted(e);
        }
    }

    /**
     * Пауза на точное время.
     *
     * @param fTime длительность паузы в секундах, можно дробную
     */
    public static void waiting(double fTime) {
        int iTime = (int) (fTime * 1000);
        try {
            sleep(iTime);
        } catch (InterruptedException e) {
            interrupted(e);
        }
    }

    /**
     * Восстанавливает флаг прерывания, чтобы вызывающий код увидел, что потоку велели остановиться.
     */
    private static void interrupted(InterruptedException e) {
        if (!GameControl.isStopRequested())
            log.warn("Waiting was interrupted", e);
        Thread.currentThread().interrupt();
    }

    /**
     * @return часы 0–23 для выпадающего списка времени окончания игры
     */
    public static String[] getListHours() {
        String[] listHours = new String[24];
        for (int i = 0; i < 24; i++) {
            listHours[i] = String.valueOf(i);
        }
        return listHours;
    }

    /**
     * @return минуты с шагом 10 для выпадающего списка времени окончания игры
     */
    public static String[] getListMinutes() {
        return new String[]{"00", "10", "20", "30", "40", "50"};
    }

}
