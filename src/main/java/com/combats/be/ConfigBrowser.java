package com.combats.be;

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
        if (!headlessValue) {
            browserSize = null;
            browserCapabilities = new ChromeOptions().addArguments("--start-maximized");
            holdBrowserOpen = true;
        } else {
            browserSize = "1900x1200";
        }
        headless = headlessValue;
        savePageSource = false;
        reportsFolder = "fails";
        timeout = 10000;
    }

}
