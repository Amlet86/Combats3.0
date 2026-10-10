package com.combats.pages;

import com.codeborne.selenide.SelenideElement;

/**
 * Действия на странице «как у человека»: с паузами, чтобы бот не выглядел механическим.
 */
public interface BaseActions {

    /**
     * Кликает по элементу после короткой паузы, если элемент виден.
     */
    void humanClick(SelenideElement element);

    /**
     * Нажимает клавишу перемещения по карте подземелья с паузами до и после.
     */
    void humanMoveOnTheMap(CharSequence keyToMove);

}
