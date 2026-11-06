package com.combats.pages;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Что видно на странице подземелья ({@code dungeon2.pl}): комната, куда смотрит персонаж, ссылки
 * действий {@code dung_link('…')} и сообщение игры. Снимается одним скриптом {@link DungeonsPage#VIEW_SCRIPT},
 * разбирается здесь, чтобы разбор можно было проверить тестами.
 *
 * @param path    путь страницы в основном фрейме («/dungeon2.pl»)
 * @param room    «Грибница - Этаж 1 - Логово»
 * @param facing  куда смотрит персонаж; null, если не нашли ни значка на мини-карте, ни текста
 * @param links   аргументы ссылок {@code dung_link} с их подписями
 * @param map     содержимое мини-карты: меняется после каждого шага
 * @param message сообщение игры в {@code div#error} («Вы подняли …», «Нельзя открыть тайник …»)
 * @param text    весь текст страницы
 * @param hp      «2967/2967» или null
 */
public record DungeonView(String path, String room, Direction facing, List<Link> links, String map, String message,
                          String text, String hp) {

    /** Ссылка действия: аргумент {@code dung_link} («path=m1», «useobj=358554083», «getall=l») и подпись. */
    public record Link(String action, String title) {
    }

    /**
     * Персонажи для разговора: на карте объектов они выглядят как монстры в досягаемости, но вместо боя
     * открывают разговор (снято 06.11.2026: Мастер Фунгус на 2-м этаже Грибницы, действие {@code dialog=…}).
     */
    static final List<String> PEACEFUL = List.of("Мастер Фунгус", "Мудрый Гусениц");

    private static final Pattern DUNG_LINK = Pattern.compile("dung_link\\('([^']*)'");
    private static final Pattern FLOOR = Pattern.compile("Этаж\\s*(\\d+)");
    /** «Вы перейдете вперед (отмена)»: шаг с таймером ещё не закончился. */
    private static final String PENDING_MOVE = "Вы перейдете";

    /**
     * @param raw ответ {@link DungeonsPage#VIEW_SCRIPT}: path, room, facingCode, text, map, message, hp и
     *            links — список строк «href\tподпись»
     */
    static DungeonView from(Map<?, ?> raw) {
        String text = string(raw.get("text"));
        Direction facing = Direction.fromCode(string(raw.get("facingCode")));
        if (facing == null)
            facing = Direction.fromText(text);
        List<Link> links = new ArrayList<>();
        if (raw.get("links") instanceof List<?> list) {
            for (Object item : list) {
                Link link = parseLink(string(item));
                if (link != null)
                    links.add(link);
            }
        }
        String hp = string(raw.get("hp"));
        return new DungeonView(string(raw.get("path")), string(raw.get("room")).trim(), facing, links,
                string(raw.get("map")), string(raw.get("message")).trim(), text, hp.isBlank() ? null : hp.trim());
    }

    /**
     * @param hrefAndTitle «javascript:dung_link('path=m1','m1')\tГрибница - Этаж 1 - Логово»
     * @return ссылка или null, если это не {@code dung_link}
     */
    static Link parseLink(String hrefAndTitle) {
        String[] parts = hrefAndTitle.split("\t", 2);
        Matcher matcher = DUNG_LINK.matcher(parts[0]);
        if (!matcher.find() || matcher.group(1).isBlank())
            return null;
        return new Link(matcher.group(1), parts.length > 1 ? parts[1].trim() : "");
    }

    /** @return true, если это персонаж для разговора, а не монстр */
    static boolean isPeaceful(String title) {
        return PEACEFUL.stream().anyMatch(title::contains);
    }

    /** @return true, если это страница подземелья */
    public boolean inDungeon() {
        return isMapPage(path);
    }

    /**
     * Карта подземелья — {@code dungeon<N>.pl}, номер меняется (видели dungeon0 и dungeon2, 03.11.2026).
     * {@code dungeon.pl} без номера — страница входа, это не карта.
     */
    static boolean isMapPage(String path) {
        return path != null && path.matches("/dungeon\\d+\\.pl");
    }

    /** @return аргументы ссылок шагов и поворотов: «m1», «m3», «rl»… */
    public Set<String> pathLinks() {
        Set<String> result = new LinkedHashSet<>();
        for (Link link : links)
            if (link.action().startsWith("path="))
                result.add(link.action().substring("path=".length()));
        return result;
    }

    /** @return true, если на полу лежат вещи («Подобрать все») */
    public boolean hasLoot() {
        return links.stream().anyMatch(link -> link.action().startsWith("getall="));
    }

    /** @return объекты, которые можно использовать: тайники, ниши, проходы ({@code useobj=…}) */
    public List<Link> objects() {
        return links.stream().filter(link -> link.action().startsWith("useobj=")).toList();
    }

    /** @return true, пока идёт шаг с таймером */
    public boolean moving() {
        return text.contains(PENDING_MOVE);
    }

    /** @return номер этажа из названия комнаты или 0, если его там нет */
    public int floor() {
        Matcher matcher = FLOOR.matcher(room);
        return matcher.find() ? Integer.parseInt(matcher.group(1)) : 0;
    }

    /** @return по чему видно, что персонаж сдвинулся: комната и мини-карта */
    public String place() {
        return room + "|" + map;
    }

    private static String string(Object value) {
        return value == null ? "" : value.toString();
    }

}
