package com.combats.pages;

import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;

import com.codeborne.selenide.SelenideElement;
import com.combats.utils.GameControl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.codeborne.selenide.Selenide.*;
import static com.combats.utils.GameControl.status;
import static com.combats.utils.Properties.getUserLogin;
import static com.combats.utils.Utils.checkGameCanContinue;
import static com.combats.utils.Utils.parseHpPercent;
import static com.combats.utils.Utils.takeScreenshot;
import static com.combats.utils.Utils.waitAboutSomeSeconds;

/**
 * Подземелье ({@code dungeon<N>.pl}, снято 03.11.2026 в Грибнице Sun city): обход этажа, подбор вещей,
 * нападение на монстров, тайники и ниши.
 * <p>
 * Ходим не клавишами, а теми же вызовами {@code dung_link('path=m1', 'm1')}, что делает игра: ссылка шага
 * есть, только если туда можно пройти, и не важна раскладка клавиатуры. Каждый шаг проверяем: засчитываем,
 * только если сменилась мини-карта. Куда идти, решает {@link DungeonExplorer}. Когда этаж обойдён, бот
 * спускается по «Спуску», если видел его, а если нет — выходит из подземелья.
 */
public class DungeonsPage extends BasePage {

    private static final Logger log = LoggerFactory.getLogger(DungeonsPage.class);

    static final String FORWARD = "m1";
    static final String RIGHT = "m3";
    static final String BACK = "m5";
    static final String LEFT = "m7";
    private static final String TURN_LEFT = "rl";
    private static final String TURN_RIGHT = "rr";
    /** Клавиши тех же действий: запасной путь, если на странице нет функции dung_link. */
    private static final Map<String, String> KEYS = Map.of("path=" + FORWARD, "w", "path=" + BACK, "s",
            "path=" + LEFT, "a", "path=" + RIGHT, "d", "path=" + TURN_LEFT, "q", "path=" + TURN_RIGHT, "e", "getall=l", "z");

    /**
     * Объекты, которые бот открывает сам (по подписи). Остальные не трогаем: «Заросший проход» переносит
     * в другую часть этажа и сбивает счёт клеток, а от некоторых грибов персонаж подцепил болезнь.
     */
    private static final List<String> SAFE_OBJECTS = List.of("тайник", "Ниша", "Слизкое нечто", "Чудодейственная лужа");
    /** Так игра отвечает, если объект за стеной: ссылка на него есть, а открыть его отсюда нельзя (07.11.2026). */
    private static final String UNREACHABLE_OBJECT = "Недостижимая цель";
    /**
     * Так игра отвечает, если рядом монстры (08.11.2026: с B9 2-го этажа Грибницы видна ниша B10, а в A9 монстры).
     * Ниша не открыта, поэтому, как и с {@link #UNREACHABLE_OBJECT}, пробуем её снова с другой клетки.
     */
    private static final String MONSTERS_AROUND = "Вы опасаетесь";
    private static final int RECOVERED_HP_PERCENT = 80;
    private static final int MAX_FAILED_STEPS = 3;
    /**
     * Объект перехода на этаж ниже (снято 03.11.2026: «Спуск» → «Грибница - Этаж 2 - Вход»; 06.11.2026:
     * «Спуск в Глубины Грибницы» → этаж 3). «Спуск в нору» ведёт в комнату того же этажа.
     */
    private static final String DESCENT = "Спуск";
    private static final String NOT_A_DESCENT = "в нору";
    private static final int MAX_DESCENT_ATTEMPTS = 5;
    /** Маршруты этажей Грибницы: идём по маршруту, если обход начался со «Входа» этажа. */
    static final String FIRST_FLOOR_ROUTE = "maps/gribnitsa-floor1.txt";
    static final String SECOND_FLOOR_ROUTE = "maps/gribnitsa-floor2.txt";
    private static final Map<String, String> FLOOR_ROUTES = Map.of(
            "Грибница - Этаж 1 - Вход", FIRST_FLOOR_ROUTE,
            "Грибница - Этаж 2 - Вход", SECOND_FLOOR_ROUTE);
    /** Объекты, через которые маршрут переходит в другую клетку ({@code >} в файле маршрута). */
    private static final List<String> PASSAGES = List.of("Заросший проход", "Спуск в нору", "Подъем", "Хитиновый люк");
    private static final int MAX_LOADING_CHECKS = 30;
    /** Шаг идёт с таймером (полоска «wait3.gif»), берём с запасом. */
    private static final Duration STEP_TIMEOUT = Duration.ofSeconds(15);
    private static final Duration TURN_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration BATTLE_START_WAIT = Duration.ofSeconds(5);
    private static final Duration LEAVE_TIMEOUT = Duration.ofSeconds(30);

    /** Скрипт снимка страницы для {@link DungeonView#from}; arguments[0] — логин персонажа. */
    static final String VIEW_SCRIPT = "const d = document;"
            + "const marks = [...d.querySelectorAll('img.DungMap_users')];"
            + "const own = marks.find(i => i.title === arguments[0]) || marks[0];"
            + "const code = own ? ((own.getAttribute('src') || '').match(/\\/d(\\d)\\.gif/) || [])[1] : null;"
            + "const links = [...d.querySelectorAll(\"a[href^='javascript:dung_link'], area[href^='javascript:dung_link']\")]"
            + "    .map(a => a.getAttribute('href') + '\\t' + (a.getAttribute('title') || a.textContent || '').trim());"
            + "const byId = id => { const e = d.getElementById(id); return e ? e.innerText : ''; };"
            + "const map = d.getElementById('DungMap');"
            + "const cells = map ? [...map.querySelectorAll('img:not(.DungMap_users)')]"
            + "    .map(i => i.getAttribute('src') + i.getAttribute('style')).join() : '';"
            + "return { path: location.pathname, room: byId('roomname'), facingCode: code,"
            + "    text: d.body ? d.body.innerText : '', map: cells, message: byId('error'), hp: byId('HP'), links: links };";
    /**
     * Вызов как у самой игры (её обработчик клавиш, снято 03.11.2026): для шагов вторым аргументом идёт
     * «m1»…, тогда игра сама проверяет, можно ли туда идти, и ставит шаг в очередь, если не кончился таймер.
     */
    private static final String DUNG_LINK = "if (typeof dung_link !== 'function') return false;"
            + "dung_link(arguments[0], arguments[1] || undefined); return true;";
    /** Монстры в досягаемости на карте объектов: ссылка есть только у них, см. {@link #ATTACK}. */
    private static final String MONSTERS_IN_REACH = "map#ObjectsMap area[href='javascript:void(0)'],"
            + " map[name=ObjectsMap] area[href='javascript:void(0)']";
    private static final String MONSTERS_IN_REACH_SCRIPT = "return [...document.querySelectorAll(\""
            + MONSTERS_IN_REACH + "\")].map(a => a.title);";
    /**
     * Нападение, как клавиша f: {@code show.autoAttack()}. Но без монстра рядом autoAttack использует
     * ближайший объект, а это может быть «Заросший проход» или ядовитые корни. Поэтому вызываем, только если
     * монстр в досягаемости: у такого монстра на карте объектов ссылка {@code javascript:void(0)}, у дальних
     * ссылки нет. Переменная игры с монстром ({@code autoAttackUnit}) снаружи не видна. Проверено вживую
     * 03.11.2026: вызов начал бой.
     */
    private static final String ATTACK = "const near = [...document.querySelectorAll(\"" + MONSTERS_IN_REACH + "\")]"
            + "    .filter(a => !arguments[0].some(name => (a.title || '').includes(name)));"
            + "if (!near.length || typeof show === 'undefined' || typeof show.autoAttack !== 'function') return false;"
            + "show.autoAttack(); return true;";
    /** Видна ли в верхнем документе хоть одна кнопка экрана боя. */
    private static final String BATTLE_SCRIPT = "return [...document.querySelectorAll("
            + "'[action=commit], [action=refresh], [action=exit], [action=gameover]')]"
            + ".some(b => b.offsetWidth > 0 && getComputedStyle(b).visibility !== 'hidden');";

    /**
     * Разговор с персонажем игры: варианты ответа это ссылки {@code main.pl?move_dialog=…} (снято 04.11.2026,
     * «Внезапный Гоблин» ГоГо после боя в Грибнице). Сам текст {@code move_dialog} есть и в скриптах страницы,
     * поэтому ищем элементы. Возвращает заголовок разговора или null, если разговора нет.
     */
    private static final String DIALOG_SCRIPT = "if (!document.querySelector("
            + "\"a[href*='move_dialog='], input[name=move_dialog]\")) return null;"
            + "const title = document.querySelector('h3');"
            + "return title ? title.innerText.trim() : 'unknown';";

    /** Где персонаж: в подземелье, в бою, в другом месте; LOADING — страница ещё грузится. */
    enum Place { DUNGEON, BATTLE, OTHER, LOADING }

    /** Обход живёт весь поход, через бои: после боя продолжаем с той же клетки. */
    private static final DungeonExplorer explorer = new DungeonExplorer();
    private static final Set<String> usedObjects = new HashSet<>();
    /** Объекты вместе с клетками, откуда игра их не открыла: с другой клетки пробуем снова. */
    private static final Set<String> unreachableObjects = new HashSet<>();
    private static int floor;
    /**
     * Где персонаж стоял, когда бот последний раз смотрел на карту. Если после боя он в другом месте, значит,
     * погиб: после смерти игра переносит ко входу на этаж (03.11.2026), и счёт клеток надо начать заново.
     */
    private static String lastPlace;
    private static int descentAttempts;
    /** Целевой маршрут по этажу; null, если идём обходом. */
    private static DungeonRoute route;

    /**
     * Клетка и взгляд, при которых нападение не привело к бою: так больше не нападаем, чтобы не стоять на месте.
     * Взгляд важен: в другую сторону монстр может оказаться перед персонажем.
     */
    private String attackedAt;

    /**
     * Начало нового похода: обход и список открытых тайников с чистого листа.
     */
    static void startRun() {
        explorer.reset();
        usedObjects.clear();
        unreachableObjects.clear();
        floor = 0;
        lastPlace = null;
        descentAttempts = 0;
        route = null;
    }

    /** @return true, если персонаж сейчас на карте подземелья */
    public static boolean inDungeon() {
        return where() == Place.DUNGEON;
    }

    /**
     * Смотрит, где персонаж. Экран боя рисуется в верхнем документе, подземелье — в основном фрейме.
     * После вызова активен основной фрейм (если он есть).
     */
    static Place where() {
        try {
            switchTo().defaultContent();
            if (Boolean.TRUE.equals(executeJavaScript(BATTLE_SCRIPT)))
                return Place.BATTLE;
            SelenideElement frame = $(GAME_FRAME_LOCATOR);
            if (!frame.exists())
                return Place.LOADING;
            switchTo().frame(frame);
            String path = executeJavaScript("return location.pathname;");
            if (path == null || path.isBlank())
                return Place.LOADING;
            if (GoToBattlePage.isBattlePage(path))
                return Place.BATTLE;
            return DungeonView.isMapPage(path) ? Place.DUNGEON : Place.OTHER;
        } catch (RuntimeException e) {
            // страница перезагружается: фрейм пропал между проверками
            log.debug("Can't check the place, the page is loading", e);
            return Place.LOADING;
        }
    }

    /**
     * Обходит этаж, пока не начнётся бой или поход не закончится.
     *
     * @return страница боя, если начался бой; null, если персонаж больше не в подземелье
     * (этаж обойдён и бот вышел, персонаж погиб, игра выкинула в город)
     */
    public BattlePage walkOnTheDungeon() {
        status("Walking in the dungeon");
        int failedSteps = 0;
        int loadingChecks = 0;
        boolean firstLook = true;
        while (true) {
            checkGameCanContinue();
            Place place = where();
            if (place == Place.BATTLE)
                return page(BattlePage.class);
            if (place == Place.LOADING && ++loadingChecks < MAX_LOADING_CHECKS) {
                sleep(1000);
                continue;
            }
            if (place != Place.DUNGEON) {
                if (place == Place.OTHER)
                    askHumanIfCharacterTalks();
                log.info("Not in the dungeon any more ({})", place);
                return null;
            }
            loadingChecks = 0;
            DungeonView view = view();
            if (view.moving()) {
                sleep(1000);
                continue;
            }
            if (view.floor() != floor) {
                log.info("Explore the floor {}: {}", view.floor(), view.room());
                explorer.reset();
                route = routeFrom(view);
                floor = view.floor();
                descentAttempts = 0;
            } else if (firstLook && lastPlace != null && !lastPlace.equals(view.place())) {
                log.info("After the battle the character is in another place (dead?): explore the floor over from {}",
                        view.room());
                boolean hadRoute = route != null;
                explorer.reset();
                route = routeFrom(view);
                // человек мог увести персонажа с маршрута во время паузы
                if (hadRoute && route == null)
                    explorer.markIncomplete();
            }
            firstLook = false;
            // по этой строке строится карта этажа: комната и настоящие стены клетки
            if (view.facing() != null && !view.place().equals(lastPlace))
                log.info("Cell: {} | open {} | objects {}", view.room(),
                        DungeonExplorer.openSides(view.facing(), view.pathLinks()),
                        view.objects().stream().map(DungeonView.Link::title).toList());
            lastPlace = view.place();
            if (restIfWounded(view))
                continue;
            if (view.hasLoot()) {
                pickUp(view);
                continue;
            }
            if (!attackSpot(view).equals(attackedAt) && attack())
                return page(BattlePage.class);
            if (useObject(view))
                continue;
            if (view.facing() == null)
                throw new IllegalStateException("Can't see where the character looks in the dungeon: " + view.room());
            if (descentLink(view).isPresent()) {
                if (!explorer.knowsDescent())
                    log.info("The descent to the next floor is seen in {}", view.room());
                // запоминаем последнее место: маршрут кончается там, откуда виден «Спуск», идти никуда не надо
                explorer.sawDescent(view.facing());
            }
            if (route != null && route.nextIsLook()) {
                // объекты видны и открываются, только когда они перед персонажем: следующий круг откроет тайник
                turnTo(view.facing(), route.next().orElseThrow());
                route.moved();
                continue;
            }
            Optional<Direction> next = nextSide(view);
            if (next.isEmpty()) {
                if (descend(view))
                    continue;
                // если «Спуск» не сработал, выход потратит поход: пусть решает человек
                if (!explorer.isComplete() || explorer.knowsDescent())
                    askHumanAboutUnfinishedFloor(view.room());
                leaveDungeon();
                return null;
            }
            boolean passage = route != null && route.nextIsPassage();
            if (route == null && heldByPeaceful(view, next.get()))
                continue;
            if (passage ? usePassage(view, next.get()) : step(view, next.get())) {
                explorer.moved(next.get());
                if (route != null)
                    route.moved();
                failedSteps = 0;
            } else {
                // монстр мог подойти, пока шёл шаг: на следующем круге нападаем снова
                attackedAt = null;
                boolean closed = ++failedSteps >= MAX_FAILED_STEPS;
                reportFailedStep(next.get(), view.room(), closed);
                if (closed && where() == Place.DUNGEON) {
                    askHumanIfWayIsHeld(next.get(), view.room());
                    explorer.blocked(next.get());
                    failedSteps = 0;
                }
            }
        }
    }

    /**
     * @return маршрут, если персонаж стоит в его начале; на остальных этажах и с других клеток маршрута нет
     */
    private static DungeonRoute routeFrom(DungeonView view) {
        String file = FLOOR_ROUTES.get(view.room());
        if (file == null)
            return null;
        DungeonRoute loaded = DungeonRoute.load(file).orElse(null);
        if (loaded != null)
            log.info("Follow the route {}: {} steps", file, loaded.size());
        else
            log.warn("There is no route file {}, explore the floor by itself", file);
        return loaded;
    }

    /**
     * @return куда идти: по маршруту, а если его нет, решает обход. Пусто, если идти больше некуда
     */
    private static Optional<Direction> nextSide(DungeonView view) {
        Set<Direction> open = DungeonExplorer.openSides(view.facing(), view.pathLinks());
        if (route != null) {
            Optional<Direction> side = route.next();
            if (side.isEmpty() || route.nextIsPassage() || open.contains(side.get()))
                return side;
            dropRoute("there is no way " + side.get() + " from " + view.room());
        }
        return explorer.next(view.facing(), open);
    }

    /**
     * Переход через объект по маршруту ({@link #PASSAGES}): объект виден, только когда смотришь на него, а его
     * номер в каждом походе свой, поэтому ищем по названию (снято 05.11.2026 на J3 и J2).
     *
     * @return true, если персонаж оказался в другом месте
     */
    private boolean usePassage(DungeonView view, Direction side) {
        if (!turnTo(view.facing(), side))
            return false;
        DungeonView before = view();
        Optional<DungeonView.Link> passage = before.objects().stream()
                .filter(object -> PASSAGES.stream().anyMatch(object.title()::contains)).findFirst();
        if (passage.isEmpty())
            return false;
        status("Going through the passage");
        act(passage.get().action());
        return waitFor(v -> !v.place().equals(before.place()), STEP_TIMEOUT);
    }

    /** Маршрут разошёлся с игрой: дальше идём обходом, он считает клетки и без маршрута. */
    private static void dropRoute(String reason) {
        if (route == null)
            return;
        log.warn("Leave the route: {}", reason);
        route = null;
        explorer.markIncomplete();
    }

    /**
     * Пишет, что ответила игра на неудачный шаг. Если сторона закрыта окончательно, сохраняет страницу:
     * 04.11.2026 в «Грибном коридоре» шаг на запад не проходил в каждом походе, а причины в логе не было.
     */
    private static void reportFailedStep(Direction side, String room, boolean closed) {
        if (where() != Place.DUNGEON)
            return;
        log.warn("The step {} from {} hasn't worked, the game says: «{}»", side, room, view().message());
        if (closed) {
            log.warn("Can't go {} from {}", side, room);
            GoToBattlePage.savePageSnapshot("dungeon-blocked");
        }
    }

    /**
     * Шаг не проходит, хотя ссылка на него есть. Если это шаг маршрута (клетка на карте точно есть) или рядом
     * монстр (персонажи для разговора не в счёт, {@link DungeonView#isPeaceful}), это не стена: зовёт человека и ждёт /resume, маршрут при этом остаётся. Иначе ничего не делает,
     * и сторона считается закрытой.
     *
     * @throws IllegalStateException если путь занят, а позвать некого (нет Telegram)
     */
    private static void askHumanIfWayIsHeld(Direction side, String room) {
        List<String> near = executeJavaScript(MONSTERS_IN_REACH_SCRIPT);
        List<String> monsters = near.stream().filter(title -> !DungeonView.isPeaceful(title)).toList();
        if (route == null && monsters.isEmpty())
            return;
        String problem = "The step " + side + " from " + room + " doesn't work, monsters in reach: " + monsters;
        log.warn(problem);
        GameControl.pauseForHuman("The bot needs you. " + problem
                + ". Please connect and clear the way, then send /resume.", takeScreenshot());
        throw new IllegalStateException(problem + ", and there is no one to call");
    }

    /**
     * Мастера Фунгуса проходим не останавливаясь и не взаимодействуя с ним (решение Андрея, 06.11.2026): он стоит
     * в тупике, шаг к нему не проходит, а нападение открывает разговор. Смотрим в нужную сторону и, если он
     * перед персонажем, больше туда не идём.
     *
     * @return true, если эта сторона занята персонажем для разговора
     */
    private boolean heldByPeaceful(DungeonView view, Direction side) {
        if (!turnTo(view.facing(), side))
            return false;
        List<String> near = executeJavaScript(MONSTERS_IN_REACH_SCRIPT);
        Optional<String> peaceful = near.stream().filter(DungeonView::isPeaceful).findFirst();
        if (peaceful.isEmpty())
            return false;
        log.info("«{}» stands {}, pass by", peaceful.get(), side);
        explorer.avoid(side);
        return true;
    }

    /**
     * Идти некуда, но этаж пройден не весь или «Спуск» не сработал: зовёт человека, а не тратит поход
     * выходом из подземелья.
     *
     * @throws IllegalStateException если позвать некого (нет Telegram)
     */
    private static void askHumanAboutUnfinishedFloor(String room) {
        String problem = "Can't finish the floor " + floor + ": " + explorer.visitedCells()
                + " cells, nowhere to go from " + room;
        log.warn(problem);
        GoToBattlePage.savePageSnapshot("dungeon-unfinished");
        GameControl.pauseForHuman("The bot needs you. " + problem
                + ". Please connect, walk on or leave the dungeon yourself, then send /resume.", takeScreenshot());
        throw new IllegalStateException(problem + ", and there is no one to call");
    }

    private static String attackSpot(DungeonView view) {
        return view.place() + "|" + view.facing();
    }

    /**
     * Если персонаж игры завёл разговор, зовёт человека и ждёт /resume. Это проверка, что играет человек:
     * бот на вопросы не отвечает, страницу не трогает и не перезагружает (решение Андрея, 04.11.2026).
     * Активен должен быть основной фрейм.
     *
     * @throws IllegalStateException если разговор есть, а позвать некого (нет Telegram)
     */
    static void askHumanIfCharacterTalks() {
        String title = executeJavaScript(DIALOG_SCRIPT);
        if (title == null)
            return;
        log.info("A character is talking to the player: «{}»", title);
        GoToBattlePage.savePageSnapshot("character-dialog");
        GameControl.pauseForHuman("The bot needs you: a character is asking questions («" + title
                + "»). Please connect and answer yourself, then send /resume.", takeScreenshot());
        throw new IllegalStateException("A character is asking questions («" + title + "»), and there is no one to call");
    }

    /** @return снимок страницы подземелья; активен должен быть основной фрейм */
    private static DungeonView view() {
        Map<?, ?> raw = executeJavaScript(VIEW_SCRIPT, getUserLogin());
        return DungeonView.from(raw == null ? Map.of() : raw);
    }

    /**
     * Поворачивается в нужную сторону и делает шаг вперёд.
     *
     * @return true, если персонаж перешёл в соседнюю клетку
     */
    private boolean step(DungeonView view, Direction side) {
        if (!turnTo(view.facing(), side))
            return false;
        DungeonView before = view();
        if (!before.pathLinks().contains(FORWARD))
            return false;
        status("Walking in the dungeon: " + side.name().toLowerCase() + ", " + explorer.visitedCells() + " cells");
        act("path=" + FORWARD);
        return waitFor(v -> !v.place().equals(before.place()), STEP_TIMEOUT);
    }

    /**
     * Поворачивается лицом в нужную сторону.
     *
     * @return true, если повернулся
     */
    private boolean turnTo(Direction facing, Direction side) {
        while (facing != side) {
            boolean left = side == facing.left();
            Direction expected = left ? facing.left() : facing.right();
            act("path=" + (left ? TURN_LEFT : TURN_RIGHT));
            if (!waitFor(v -> v.facing() == expected, TURN_TIMEOUT)) {
                log.warn("The character hasn't turned to the {}", expected);
                return false;
            }
            facing = expected;
        }
        return true;
    }

    /**
     * Этаж обойдён: если на нём видели «Спуск», идёт к нему и спускается на этаж ниже. Спуск может не
     * сработать (нужен ключ, задание), поэтому пробуем не больше {@link #MAX_DESCENT_ATTEMPTS} раз за этаж.
     *
     * @return true, если сделал что-то на пути к спуску; false, если спуститься нельзя и пора выходить
     */
    private boolean descend(DungeonView view) {
        if (!explorer.knowsDescent() || descentAttempts >= MAX_DESCENT_ATTEMPTS)
            return false;
        if (!explorer.atDescent()) {
            Optional<Direction> side = explorer.towardsDescent();
            if (side.isEmpty())
                return false;
            status("Going to the descent");
            if (step(view, side.get()))
                explorer.moved(side.get());
            else
                descentAttempts++;
            return true;
        }
        if (!turnTo(view.facing(), explorer.descentFacing())) {
            descentAttempts++;
            return true;
        }
        Optional<DungeonView.Link> descent = descentLink(view());
        descentAttempts++;
        if (descent.isEmpty()) {
            log.warn("The descent isn't seen where it was");
            return true;
        }
        log.info("The floor {} is explored: {} cells, go down", floor, explorer.visitedCells());
        status("Going down to the next floor");
        act(descent.get().action());
        int floorBefore = floor;
        if (!waitFor(v -> v.floor() != floorBefore, STEP_TIMEOUT))
            log.warn("The descent hasn't worked: {}", where() == Place.DUNGEON ? view().message() : "");
        return true;
    }

    private static Optional<DungeonView.Link> descentLink(DungeonView view) {
        return view.objects().stream()
                .filter(object -> object.title().contains(DESCENT) && !object.title().contains(NOT_A_DESCENT))
                .findFirst();
    }

    /**
     * Ждёт, пока страница подземелья придёт в нужное состояние. Пока идёт шаг с таймером, ждём дальше.
     *
     * @return true, если дождались; false по таймауту или если персонаж ушёл с карты (бой)
     */
    private boolean waitFor(Predicate<DungeonView> condition, Duration timeout) {
        Instant deadline = Instant.now().plus(timeout);
        while (Instant.now().isBefore(deadline)) {
            sleep(500);
            Place place = where();
            if (place == Place.BATTLE || place == Place.OTHER)
                return false;
            if (place == Place.LOADING)
                continue;
            DungeonView view = view();
            if (!view.moving() && condition.test(view))
                return true;
        }
        return false;
    }

    /**
     * Действие подземелья тем же вызовом, что и ссылка на странице. Если функции нет, жмём клавишу.
     */
    private void act(String action) {
        waitAboutSomeSeconds(1);
        String move = action.matches("path=m\\d") ? action.substring("path=".length()) : null;
        if (Boolean.TRUE.equals(executeJavaScript(DUNG_LINK, action, move)))
            return;
        String key = KEYS.get(action);
        if (key == null)
            throw new IllegalStateException("There is no dung_link on the page for «" + action + "»");
        humanMoveOnTheMap(key);
    }

    /**
     * Подбирает всё, что лежит на полу («Подобрать все»).
     */
    private void pickUp(DungeonView view) {
        status("Picking up the loot");
        view.links().stream().filter(link -> link.action().startsWith("getall=")).findFirst()
                .ifPresent(link -> act(link.action()));
        waitAboutSomeSeconds(2);
        if (where() == Place.DUNGEON)
            log.info("Loot: {}", view().message());
    }

    /**
     * Нападает на монстра рядом, если он есть ({@link #ATTACK}).
     *
     * @return true, если начался бой
     */
    private boolean attack() {
        waitAboutSomeSeconds(1);
        if (!Boolean.TRUE.equals(executeJavaScript(ATTACK, DungeonView.PEACEFUL)))
            return false;
        status("Attacking the monsters");
        String spot = attackSpot(view());
        Instant deadline = Instant.now().plus(BATTLE_START_WAIT);
        while (Instant.now().isBefore(deadline)) {
            if (where() == Place.BATTLE) {
                log.info("A battle with the monsters has begun");
                return true;
            }
            sleep(500);
        }
        String message = where() == Place.DUNGEON ? view().message() : "";
        log.info("The attack hasn't started a battle, don't attack here again, the game says: «{}»", message);
        attackedAt = spot;
        return false;
    }

    /**
     * Открывает один безопасный объект ({@link #SAFE_OBJECTS}). У дальних объектов и у монстров ссылки
     * {@code useobj} нет (снято 03.11.2026), но у ниш за стеной она есть. Каждый объект пробуем один раз за
     * поход, чтобы не застрять, если игра отказывает («Нельзя открыть тайник, пока кто-то из монстров ошивается
     * поблизости»). Исключение {@link #UNREACHABLE_OBJECT}: на 2-м этаже Грибницы ниши с ключами видны из-за
     * стены с C10 и G2, а открываются с B10 и H3, поэтому такой объект пробуем снова с другой клетки.
     * Так же поступаем, если открыть не дали монстры поблизости ({@link #MONSTERS_AROUND}).
     *
     * @return true, если что-то открыли
     */
    private boolean useObject(DungeonView view) {
        for (DungeonView.Link object : view.objects()) {
            String objectAtPlace = object.action() + "|" + view.place();
            if (usedObjects.contains(object.action()) || unreachableObjects.contains(objectAtPlace)
                    || SAFE_OBJECTS.stream().noneMatch(object.title()::contains))
                continue;
            usedObjects.add(object.action());
            status("Opening «" + object.title() + "»");
            act(object.action());
            waitAboutSomeSeconds(2);
            if (where() == Place.DUNGEON) {
                String answer = view().message();
                log.info("«{}»: {}", object.title(), answer);
                if (tryFromAnotherCell(answer)) {
                    usedObjects.remove(object.action());
                    unreachableObjects.add(objectAtPlace);
                }
            }
            return true;
        }
        return false;
    }

    /**
     * @return true, если объект не открылся и его стоит попробовать с другой клетки: он за стеной или рядом монстры
     */
    static boolean tryFromAnotherCell(String answer) {
        return answer.contains(UNREACHABLE_OBJECT) || answer.contains(MONSTERS_AROUND);
    }

    /**
     * Если HP мало, ждёт на месте, а не нападает. Монстры могут напасть сами, это заметит следующий круг.
     *
     * @return true, если ждали
     */
    private boolean restIfWounded(DungeonView view) {
        Integer hpPercent = view.hp() == null ? null : parseHpPercent(view.hp());
        if (hpPercent == null || hpPercent >= RECOVERED_HP_PERCENT)
            return false;
        status("Waiting for HP recovery in the dungeon: " + hpPercent + "%");
        waitAboutSomeSeconds(10);
        return true;
    }

    /**
     * Этаж обойдён: выходит из подземелья («Выйти» спрашивает подтверждение).
     */
    private void leaveDungeon() {
        log.info("The floor {} is explored: {} cells, leave the dungeon", floor, explorer.visitedCells());
        status("Leaving the dungeon");
        SelenideElement exit = $("a[href*='?out=']");
        if (!exit.isDisplayed())
            throw new IllegalStateException("The floor is explored, but there is no «Выйти» link");
        acceptNextConfirm();
        humanJsClick(exit);
        Instant deadline = Instant.now().plus(LEAVE_TIMEOUT);
        while (where() == Place.DUNGEON && Instant.now().isBefore(deadline))
            sleep(1000);
    }

}
