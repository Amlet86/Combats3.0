package com.combats.pages;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Обход этажа подземелья в глубину. Абсолютных координат игра не показывает (мини-карта всегда держит
 * персонажа в центре), поэтому координаты считаем сами: начало обхода — (0, 0), каждый удачный шаг
 * сдвигает на клетку. Куда из клетки можно пройти, страница говорит ссылками шагов, их переводит
 * в стороны света {@link DungeonsPage}.
 * <p>
 * Из клетки идём в ещё не посещённую соседнюю (сначала прямо, потом направо, налево, назад), а из
 * тупика возвращаемся по своим следам. Когда возвращаться некуда, этаж обойдён. Если по дороге видели
 * «Спуск» на этаж ниже, запоминаем клетку и после обхода ведём к ней кратчайшим путём по пройденным проходам.
 * Класс не трогает браузер, поэтому проверяется тестами.
 */
public class DungeonExplorer {

    private int x;
    private int y;
    private final Set<String> visited = new HashSet<>();
    /** Шаги от начала до текущей клетки: по ним возвращаемся из тупиков. */
    private final Deque<Direction> trail = new ArrayDeque<>();
    /** Стороны, куда шаг не прошёл, хотя ссылка была: больше туда не пробуем. */
    private final Set<String> blocked = new HashSet<>();
    /** Пройденные проходы: из клетки — в какие стороны. По ним ищем путь к «Спуску». */
    private final Map<String, Set<Direction>> passages = new HashMap<>();
    /** Клетка, откуда виден «Спуск», и куда надо смотреть, чтобы его увидеть; null, если не видели. */
    private String descentCell;
    private Direction descentFacing;
    /** Часть этажа могла остаться за закрытой стороной или брошенным маршрутом. */
    private boolean incomplete;

    public DungeonExplorer() {
        reset();
    }

    /**
     * Начинает обход заново: новый поход или новый этаж.
     */
    public void reset() {
        x = 0;
        y = 0;
        visited.clear();
        trail.clear();
        blocked.clear();
        passages.clear();
        descentCell = null;
        descentFacing = null;
        incomplete = false;
        visited.add(key(0, 0));
    }

    /**
     * @param facing   куда смотрит персонаж
     * @param openSides открытые стороны текущей клетки
     * @return куда идти дальше; пусто, если этаж обойдён
     */
    public Optional<Direction> next(Direction facing, Set<Direction> openSides) {
        for (Direction side : List.of(facing, facing.right(), facing.left(), facing.opposite())) {
            if (openSides.contains(side) && !blocked.contains(sideKey(side))
                    && !visited.contains(key(x + side.dx(), y + side.dy())))
                return Optional.of(side);
        }
        return trail.isEmpty() ? Optional.empty() : Optional.of(trail.peek().opposite());
    }

    /**
     * Персонаж перешёл в соседнюю клетку.
     */
    public void moved(Direction side) {
        String from = key(x, y);
        x += side.dx();
        y += side.dy();
        String to = key(x, y);
        passages.computeIfAbsent(from, k -> EnumSet.noneOf(Direction.class)).add(side);
        passages.computeIfAbsent(to, k -> EnumSet.noneOf(Direction.class)).add(side.opposite());
        if (!trail.isEmpty() && trail.peek() == side.opposite())
            trail.pop();
        else
            trail.push(side);
        visited.add(to);
    }

    /**
     * Шаг в эту сторону не прошёл несколько раз подряд: считаем её закрытой (дверь, решётка).
     */
    public void blocked(Direction side) {
        blocked.add(sideKey(side));
        incomplete = true;
    }

    /**
     * В эту сторону не идём, но этаж от этого не становится неполным: клетку занял персонаж для разговора.
     */
    public void avoid(Direction side) {
        blocked.add(sideKey(side));
    }

    /**
     * Обход больше не гарантирует, что этаж пройден целиком: например, бросили маршрут.
     */
    public void markIncomplete() {
        incomplete = true;
    }

    /**
     * Если идти некуда, но этаж не полный, выходить нельзя: 05.11.2026 монстр занял единственный проход,
     * бот принял его за стену и вышел, пройдя 30 клеток из 73.
     *
     * @return true, если ни одна сторона не закрывалась и маршрут не бросали
     */
    public boolean isComplete() {
        return !incomplete;
    }

    /**
     * Из текущей клетки виден «Спуск».
     *
     * @param facing куда смотрит персонаж: объекты открываются, только когда они перед ним
     */
    public void sawDescent(Direction facing) {
        descentCell = key(x, y);
        descentFacing = facing;
    }

    /** @return true, если на этаже видели «Спуск» */
    public boolean knowsDescent() {
        return descentCell != null;
    }

    /** @return true, если персонаж в клетке, откуда виден «Спуск» */
    public boolean atDescent() {
        return key(x, y).equals(descentCell);
    }

    /** @return куда смотреть в клетке «Спуска», чтобы он был перед персонажем; null, если не видели */
    public Direction descentFacing() {
        return descentFacing;
    }

    /**
     * Первый шаг кратчайшего пути к «Спуску» по пройденным проходам (поиск в ширину).
     *
     * @return сторона; пусто, если «Спуск» не видели, персонаж уже там или пути по известным проходам нет
     */
    public Optional<Direction> towardsDescent() {
        if (descentCell == null || atDescent())
            return Optional.empty();
        String start = key(x, y);
        Map<String, Direction> firstStep = new HashMap<>();
        Deque<int[]> queue = new ArrayDeque<>();
        queue.add(new int[]{x, y});
        firstStep.put(start, null);
        while (!queue.isEmpty()) {
            int[] cell = queue.poll();
            String cellKey = key(cell[0], cell[1]);
            for (Direction side : passages.getOrDefault(cellKey, Set.of())) {
                String nextKey = key(cell[0] + side.dx(), cell[1] + side.dy());
                if (firstStep.containsKey(nextKey))
                    continue;
                Direction first = firstStep.get(cellKey) == null ? side : firstStep.get(cellKey);
                if (nextKey.equals(descentCell))
                    return Optional.of(first);
                firstStep.put(nextKey, first);
                queue.add(new int[]{cell[0] + side.dx(), cell[1] + side.dy()});
            }
        }
        return Optional.empty();
    }

    /** @return сколько клеток уже посещено */
    public int visitedCells() {
        return visited.size();
    }

    /** @return открытые стороны по ссылкам шагов: вперёд m1, вправо m3, назад m5, влево m7 */
    static Set<Direction> openSides(Direction facing, Set<String> stepLinks) {
        Set<Direction> sides = EnumSet.noneOf(Direction.class);
        if (stepLinks.contains(DungeonsPage.FORWARD))
            sides.add(facing);
        if (stepLinks.contains(DungeonsPage.RIGHT))
            sides.add(facing.right());
        if (stepLinks.contains(DungeonsPage.BACK))
            sides.add(facing.opposite());
        if (stepLinks.contains(DungeonsPage.LEFT))
            sides.add(facing.left());
        return sides;
    }

    private String sideKey(Direction side) {
        return key(x, y) + side;
    }

    private static String key(int x, int y) {
        return x + ":" + y;
    }

}
