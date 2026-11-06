package com.combats.pages;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Целевой маршрут по этажу: клетки сетки карты подряд ({@code H10 I10 J10 …}), как их называют, глядя на
 * {@code maps/gribnitsa-floor1.svg}: столбцы A–J с запада на восток, строки 1–10 с севера на юг.
 * {@code >} перед клеткой значит, что в неё попадаем через объект-переход («Заросший проход», «Спуск в нору»),
 * {@code >E:D7} — то же, когда клетка не соседняя: объект на востоке, окажемся в D7. {@code @E} — посмотреть
 * на восток, не сходя с места (стороны N, E, S, W). Строки с {@code #} — комментарии.
 * Класс не трогает браузер, поэтому проверяется тестами.
 */
public class DungeonRoute {

    private static final Pattern CELL = Pattern.compile("([A-Z])(\\d+)");
    /** Перед клеткой: в неё идём через объект-переход, а не шагом ({@code J3 >J2}). */
    private static final String PASSAGE_MARK = ">";

    /** Перед стороной света: остаться в клетке и посмотреть туда, чтобы увидеть объект ({@code H1 @E}). */
    private static final String LOOK_MARK = "@";

    /** Между стороной и клеткой: объект-переход с этой стороны ведёт в несоседнюю клетку ({@code C6 >E:D7}). */
    private static final String SIDE_MARK = ":";

    /** Как выполнить шаг маршрута: обычный шаг, проход в стене или поворот на месте. */
    private enum Kind { WALK, PASSAGE, LOOK }

    private record Step(Direction side, Kind kind) {
    }

    private final List<Step> steps;
    private int position;

    private DungeonRoute(List<Step> steps) {
        this.steps = steps;
    }

    /**
     * @param text клетки маршрута через пробелы и переводы строк
     * @throws IllegalArgumentException если клетка записана неверно или следующая клетка не соседняя
     */
    static DungeonRoute parse(String text) {
        List<Step> steps = new ArrayList<>();
        String previous = null;
        for (String line : text.split("\\R")) {
            if (line.isBlank() || line.strip().startsWith("#"))
                continue;
            for (String token : line.strip().toUpperCase(Locale.ROOT).split("\\s+")) {
                if (token.startsWith(LOOK_MARK)) {
                    steps.add(new Step(look(token), Kind.LOOK));
                    continue;
                }
                boolean passage = token.startsWith(PASSAGE_MARK);
                String cell = passage ? token.substring(PASSAGE_MARK.length()) : token;
                Direction objectSide = null;
                if (passage && cell.contains(SIDE_MARK)) {
                    String[] parts = cell.split(SIDE_MARK, 2);
                    objectSide = look(LOOK_MARK + parts[0]);
                    cell = parts[1];
                }
                if (previous != null) {
                    Direction side = objectSide != null ? objectSide : side(previous, cell);
                    steps.add(new Step(side, passage ? Kind.PASSAGE : Kind.WALK));
                }
                previous = cell;
            }
        }
        return new DungeonRoute(steps);
    }

    /**
     * @param file файл маршрута, путь от папки запуска программы
     * @return маршрут или пусто, если такого файла нет
     * @throws IllegalArgumentException если файл записан неверно
     */
    static Optional<DungeonRoute> load(String file) {
        Path path = Path.of(file);
        if (!Files.isRegularFile(path))
            return Optional.empty();
        try {
            return Optional.of(parse(Files.readString(path, StandardCharsets.UTF_8)));
        } catch (IOException e) {
            throw new IllegalArgumentException("Can't read the route " + file, e);
        }
    }

    /** @return куда идти дальше; пусто, если маршрут закончился */
    public Optional<Direction> next() {
        return position < steps.size() ? Optional.of(steps.get(position).side()) : Optional.empty();
    }

    /** @return true, если следующий шаг идёт через проход в стене */
    public boolean nextIsPassage() {
        return position < steps.size() && steps.get(position).kind() == Kind.PASSAGE;
    }

    /** @return true, если следующий шаг — поворот на месте */
    public boolean nextIsLook() {
        return position < steps.size() && steps.get(position).kind() == Kind.LOOK;
    }

    /** Шаг сделан. */
    public void moved() {
        position++;
    }

    /** @return сколько шагов в маршруте */
    public int size() {
        return steps.size();
    }

    private static Direction look(String token) {
        for (Direction direction : Direction.values())
            if (token.equals(LOOK_MARK + direction.name().charAt(0)))
                return direction;
        throw new IllegalArgumentException("Wrong route look: " + token);
    }

    private static Direction side(String from, String to) {
        int[] a = coordinates(from);
        int[] b = coordinates(to);
        for (Direction direction : Direction.values())
            if (a[0] + direction.dx() == b[0] && a[1] + direction.dy() == b[1])
                return direction;
        throw new IllegalArgumentException("The route cells aren't neighbours: " + from + " " + to);
    }

    private static int[] coordinates(String cell) {
        Matcher matcher = CELL.matcher(cell);
        if (!matcher.matches())
            throw new IllegalArgumentException("Wrong route cell: " + cell);
        return new int[]{matcher.group(1).charAt(0) - 'A', Integer.parseInt(matcher.group(2))};
    }

}
