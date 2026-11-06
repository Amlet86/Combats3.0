package com.combats.pages;

import java.util.Locale;

/**
 * Сторона света в подземелье. Код — цифра, которой игра обозначает сторону на мини-карте:
 * значок персонажа {@code move/p2/d<код>.gif} показывает, куда он смотрит, а в имени клетки
 * {@code sprites/map/d<коды>.gif} перечислены открытые стороны (снято 03.11.2026 в Грибнице).
 * Ось y направлена на юг, как на мини-карте.
 */
public enum Direction {

    NORTH(1, 0, -1, "север"),
    EAST(3, 1, 0, "восток"),
    SOUTH(5, 0, 1, "юг"),
    WEST(7, -1, 0, "запад");

    private final int code;
    private final int dx;
    private final int dy;
    private final String russian;

    Direction(int code, int dx, int dy, String russian) {
        this.code = code;
        this.dx = dx;
        this.dy = dy;
        this.russian = russian;
    }

    public int dx() {
        return dx;
    }

    public int dy() {
        return dy;
    }

    /** @return сторона справа, если стоять лицом сюда */
    public Direction right() {
        return values()[(ordinal() + 1) % 4];
    }

    /** @return сторона слева, если стоять лицом сюда */
    public Direction left() {
        return values()[(ordinal() + 3) % 4];
    }

    public Direction opposite() {
        return values()[(ordinal() + 2) % 4];
    }

    /**
     * @param code цифра из картинки ({@code d3.gif} → 3)
     * @return сторона или null, если код незнакомый
     */
    public static Direction fromCode(String code) {
        if (code == null)
            return null;
        for (Direction direction : values())
            if (String.valueOf(direction.code).equals(code.trim()))
                return direction;
        return null;
    }

    /**
     * @param text текст страницы с «смотрим на восток»
     * @return сторона из текста или null, если её там нет
     */
    public static Direction fromText(String text) {
        if (text == null)
            return null;
        String lower = text.toLowerCase(Locale.ROOT);
        for (Direction direction : values())
            if (lower.contains("смотрим на " + direction.russian))
                return direction;
        return null;
    }

}
