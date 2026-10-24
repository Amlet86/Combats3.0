package com.combats.be;

import java.util.Map;

import org.openqa.selenium.chrome.ChromeOptions;

import static com.codeborne.selenide.Configuration.*;

/**
 * Настройки Selenide для браузера, в котором играет бот.
 */
class ConfigBrowser {

    /**
     * @param headlessValue true — браузер без окна (галочка «Browser off» в форме),
     *                      false — окно на весь экран, которое остаётся открытым после игры
     */
    ConfigBrowser(boolean headlessValue) {
        ChromeOptions options = russianChrome();
        if (!headlessValue) {
            browserSize = null;
            options.addArguments("--start-maximized");
            holdBrowserOpen = true;
        } else {
            browserSize = "1900x1200";
            // на Linux-серверах /dev/shm маленький, и Chrome без окна падает от нехватки памяти
            options.addArguments("--disable-dev-shm-usage");
        }
        browserCapabilities = options;
        headless = headlessValue;
        savePageSource = false;
        reportsFolder = "fails";
        timeout = 10000;
    }

    /**
     * Chrome на русском. Сайт выбирает язык по браузеру, а бот ищет кнопки и переходы по русским
     * надписям («Поединки», «Зал воинов»). На сервере Chrome по умолчанию английский, и игра
     * открывалась на английском (снято 24.10.2026).
     */
    private static ChromeOptions russianChrome() {
        ChromeOptions options = new ChromeOptions().addArguments("--lang=ru-RU");
        options.setExperimentalOption("prefs", Map.of("intl.accept_languages", "ru-RU,ru"));
        return options;
    }

}
