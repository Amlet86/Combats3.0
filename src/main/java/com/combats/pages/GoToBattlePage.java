package com.combats.pages;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import com.codeborne.selenide.ElementsCollection;
import com.codeborne.selenide.SelenideElement;
import org.openqa.selenium.support.FindBy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.codeborne.selenide.Condition.visible;
import static com.codeborne.selenide.Selenide.*;
import static com.combats.utils.GameControl.status;
import static com.combats.utils.Properties.getUserLogin;
import static com.combats.utils.Utils.*;

/**
 * Страница «Поединки»: заявки на хаотичные бои и конфликты.
 */
public class GoToBattlePage extends BasePage {

    private static final Logger log = LoggerFactory.getLogger(GoToBattlePage.class);

    /**
     * Сколько раз за одно ожидание боя можно вернуться в «Поединки», если игра выбросила персонажа
     * со страницы заявок (например, в город). Если выбрасывает снова и снова, дальше не пытаемся:
     * ошибку обработает игровой цикл.
     */
    private static final int MAX_RETURNS_TO_APPLICATIONS = 5;

    /**
     * Разброс силы противников для своей заявки: вариант списка «Сила противников», в тексте которого
     * есть эта строка («±50%»). Выбираем по тексту, потому что значения вариантов (value) не снимали.
     */
    static final String OPPONENTS_STRENGTH = "50%";

    /** Заявка: (участники) (уровни) Бой начнется через N мин. — группа 1: участники, группа 2: минуты. */
    private static final Pattern APPLICATION =
            Pattern.compile("\\(([^()]*)\\)\\s*\\(\\d+-\\d+\\)\\s*Бой начн[её]тся через\\s*([\\d.,]+)\\s*мин");
    /** Шапка страницы, пока ждём свой бой: «Ваш бой начнется через 2.2 мин.». */
    private static final Pattern OWN_BATTLE = Pattern.compile("Ваш бой начн[её]тся через\\s*([\\d.,]+)\\s*мин");

    @FindBy(css = "td.menutop")
    private SelenideElement menutop;

    @FindBy(css = "input[value='Поединки']")
    private SelenideElement battles;

    @FindBy(xpath = "//*[.='Хаотичные']")
    private SelenideElement chaosBattle;

    @FindBy(xpath = "//*[.='Конфликты']")
    private SelenideElement conflictsBattle;

    @FindBy(css = "[value='Обновить']")
    private SelenideElement refreshBtn;

    /**
     * Кнопки «Принять участие в мясорубке». Имя у настоящей кнопки другое («confirm1555» 28.10.2026)
     * и может меняться, а рядом стоит скрытая ловушка {@code confirm1}: её onclick ставит в поле
     * {@code fb} единицу, видимо, метку бота. Поэтому ищем по тексту, а кнопку с {@code fb} не берём никогда.
     */
    private final ElementsCollection confirmBtns =
            $$("input[type=submit][value^='Принять участие']:not([onclick*='fb'])");

    @FindBy(xpath = "//*[.='Подать заявку на хаотичный бой']")
    private SelenideElement applicationChaos;

    @FindBy(css = "[name=open]")
    private SelenideElement open;

    /** Список «Сила противников» в форме своей заявки (±25% / ±50%). */
    @FindBy(css = "select[name=cena2]")
    private SelenideElement opponentsStrength;

    /**
     * Пары [значение радиокнопки, текст от неё до следующей радиокнопки]. Текст скриптов выбрасываем:
     * перед каждым участником стоит {@code <script>drwfl("Alkatas",…)</script>}, его скобки ломали разбор,
     * и 28.10.2026 бот не видел ни одной заявки и подавал свою.
     */
    private static final String READ_APPLICATIONS = "const radios = [...document.querySelectorAll('input[name=gocombat]')];"
            + "return radios.map((r, i) => {"
            + "  const range = document.createRange();"
            + "  range.setStartAfter(r);"
            + "  const next = radios[i + 1];"
            + "  if (next) range.setEndBefore(next);"
            + "  else if (r.form) range.setEndAfter(r.form);"
            + "  else range.setEnd(document.body, document.body.childNodes.length);"
            + "  const text = range.cloneContents();"
            + "  text.querySelectorAll('script, style').forEach(s => s.remove());"
            + "  return [r.value, text.textContent.replace(/\\s+/g, ' ')];"
            + "});";
    /** Сколько символов текста страницы писать в лог, когда вступить в заявку не получилось. */
    private static final int PAGE_TEXT_IN_LOG = 600;
    /** Сколько проверок подряд без подходящей чужой заявки нужно, чтобы подать свою. */
    static final int LOOKS_BEFORE_OWN_APPLICATION = 2;
    private static final Duration PAGE_LOAD_TIMEOUT = Duration.ofSeconds(10);
    private static final Path SNAPSHOTS_DIR =
            Paths.get(System.getProperty("user.home"), ".combats", "logs", "applications");
    private static final int SNAPSHOTS_TO_KEEP = 20;

    /** Сколько проверок подряд не нашли подходящую чужую заявку. */
    private int looksWithoutApplication;

    /** Заявка, в которую вступили на прошлом шаге; на следующем шаге проверяем, что получилось. */
    private ChaosApplication lastJoined;
    /** Заявки, в которые вступить не получилось: в них больше не пробуем, пока ждём этот бой. */
    private final Set<String> rejected = new HashSet<>();

    /**
     * Попадает в PvP-бой: вступает в чужую заявку с ближайшим стартом или подаёт свою
     * и обновляет страницу, пока бой не начнётся. Пока ждёт, показывает в статусе, сколько
     * минут осталось до начала боя.
     * <p>
     * Пока ждём, игра иногда выбрасывает персонажа со страницы заявок, например в город. Тогда
     * возвращаемся в «Поединки» → «Хаотичные» и ждём дальше: заявка либо ещё стоит, либо её
     * подадим заново.
     */
    public BattlePage enterToPvPBattle() {
        status("Looking for a battle");
        humanClick(battles);
        if (chaosBattle.isDisplayed()) {
            humanClick(chaosBattle);
            int returns = 0;
            while (true) {
                checkGameCanContinue();
                if (refreshBtn.isDisplayed()) {
                    waitOnApplicationsPage();
                    continue;
                }
                if (battleStarted())
                    break;
                // страница могла ещё грузиться после «Обновить»: даём ей время, прежде чем решать
                waitAboutSomeSeconds(3);
                if (refreshBtn.isDisplayed() || battleStarted())
                    continue;
                if (++returns > MAX_RETURNS_TO_APPLICATIONS) {
                    log.warn("Kicked out of the applications page {} times, stop going back", MAX_RETURNS_TO_APPLICATIONS);
                    break;
                }
                log.info("Kicked out of the applications page to {}, go back to «Поединки»", gameFramePath());
                status("Kicked out of the applications page, going back");
                humanClick(battles);
                humanClick(chaosBattle);
            }
        }
        if (conflictsBattle.isDisplayed()) {
            humanClick(conflictsBattle);
        }
        return page(BattlePage.class);
    }

    /**
     * Один шаг ожидания на странице хаотичных заявок: показывает время до начала боя, вступает
     * в заявку или подаёт свою, нажимает «Обновить» и ждёт.
     * <p>
     * После того как бот вступил в заявку или подал свою, шаг заканчивается: страница
     * перезагружается не сразу, и пока на экране старая, на ней ещё видна ссылка «Подать заявку».
     * Раньше бот тут же жал её, а к клику страница уже обновлялась, и ссылки не было:
     * «Element not found {//*[.='Подать заявку на хаотичный бой']}», хотя заявка уже подана.
     */
    private void waitOnApplicationsPage() {
        waitForPageLoaded();
        String pageText = $("body").getText();
        String minutes = minutesToStart(pageText, getUserLogin());
        status("Waiting for a chaotic battle to start" + (minutes == null ? "" : ": " + minutes + " min left"));
        boolean waiting = isWaitingForOwnBattle(pageText);
        checkLastJoin(waiting, pageText);
        // уже стоим в заявке: вступать в другую или подавать свою не нужно
        if (!waiting) {
            if (joinApplication(pageText)) {
                looksWithoutApplication = 0;
                waitAboutSomeSeconds(5);
                return;
            }
            if (shouldSubmitOwn(++looksWithoutApplication)) {
                savePageSnapshot();
                if (submitApplication()) {
                    waitAboutSomeSeconds(5);
                    return;
                }
            } else
                log.info("No application to join yet, look again after refreshing the page");
        }
        if (refreshBtn.isDisplayed())
            refreshBtn.click();
        waitAboutSomeSeconds(10);
    }

    /**
     * Свою заявку подаём, только если чужой не нашли {@link #LOOKS_BEFORE_OWN_APPLICATION} раза подряд,
     * с обновлением страницы между проверками. 28.10.2026 бот подал свою заявку через секунду после
     * открытия страницы, когда чужая уже была: «Обновить» и «Подать заявку» стоят на странице выше
     * списка заявок и появляются раньше него, и бот решил, что заявок нет.
     *
     * @param looks сколько раз подряд не нашли заявку, включая эту проверку
     */
    static boolean shouldSubmitOwn(int looks) {
        return looks >= LOOKS_BEFORE_OWN_APPLICATION;
    }

    /**
     * Ждёт, пока страница во фрейме загрузится целиком, но не дольше {@link #PAGE_LOAD_TIMEOUT}:
     * если не дождались, работаем с тем, что есть.
     */
    private void waitForPageLoaded() {
        Instant deadline = Instant.now().plus(PAGE_LOAD_TIMEOUT);
        while (Instant.now().isBefore(deadline)) {
            try {
                if ("complete".equals(executeJavaScript("return document.readyState;")))
                    return;
            } catch (RuntimeException e) {
                // фрейм как раз перезагружается
            }
            sleep(500);
        }
        log.warn("The applications page hasn't loaded in {} seconds", PAGE_LOAD_TIMEOUT.toSeconds());
    }

    /**
     * Сохраняет страницу заявок перед подачей своей заявки в ~/.combats/logs/applications, хранит последние
     * {@link #SNAPSHOTS_TO_KEEP}. Если бот снова подаст свою заявку при чужой, по снимку будет видно почему.
     */
    private static void savePageSnapshot() {
        try {
            String html = executeJavaScript("return document.documentElement.outerHTML;");
            Files.createDirectories(SNAPSHOTS_DIR);
            Path file = SNAPSHOTS_DIR.resolve("applications-"
                    + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss")) + ".html");
            Files.writeString(file, html == null ? "" : html, StandardCharsets.UTF_8);
            log.info("The applications page is saved to {}", file);
            try (Stream<Path> snapshots = Files.list(SNAPSHOTS_DIR)) {
                List<Path> old = snapshots.sorted(Comparator.reverseOrder()).skip(SNAPSHOTS_TO_KEEP).toList();
                for (Path path : old)
                    Files.deleteIfExists(path);
            }
        } catch (IOException | RuntimeException e) {
            log.warn("Can't save the applications page", e);
        }
    }

    /**
     * Проверяет, получилось ли вступить в заявку на прошлом шаге. Если персонаж не в заявке,
     * эту заявку больше не выбираем, а пробуем другие. Раньше после неудачи бот сразу подавал свою,
     * хотя рядом были другие подходящие заявки. Текст страницы пишем в лог: там должна быть причина.
     */
    private void checkLastJoin(boolean waiting, String pageText) {
        if (lastJoined == null)
            return;
        if (!waiting) {
            rejected.add(lastJoined.radioValue());
            log.warn("Haven't got into the application {}, try another one. The page: {}", lastJoined,
                    pageText.length() > PAGE_TEXT_IN_LOG ? pageText.substring(0, PAGE_TEXT_IN_LOG) + "…" : pageText);
        }
        lastJoined = null;
    }

    /**
     * Вступает в подходящую чужую заявку, см. {@link ChaosApplication#choose}.
     *
     * @return true, если нажали «Принять участие»
     */
    private boolean joinApplication(String pageText) {
        List<ChaosApplication> applications = readApplications();
        Integer ownLevel = ChaosApplication.ownLevel(pageText, getUserLogin());
        Optional<ChaosApplication> chosen = ChaosApplication.choose(applications, ownLevel, rejected);
        if (chosen.isEmpty()) {
            log.info("No application to join (level {}, tried without success {}): {}", ownLevel, rejected, applications);
            return false;
        }
        // кнопок «Принять участие» на странице две, над списком и под ним, и первая бывает скрыта
        SelenideElement confirm = confirmBtns.findBy(visible);
        if (!confirm.isDisplayed()) {
            log.warn("The button «Принять участие» isn't visible, can't join {}", chosen.get());
            return false;
        }
        SelenideElement radio = $("[name=gocombat][value='" + chosen.get().radioValue() + "']");
        if (!radio.isDisplayed()) {
            log.warn("The radio button of the application {} isn't visible", chosen.get());
            rejected.add(chosen.get().radioValue());
            return false;
        }
        radio.click();
        humanClick(confirm);
        lastJoined = chosen.get();
        log.info("Joined a chaotic battle application {} of {}", chosen.get(), applications);
        return true;
    }

    /**
     * Читает чужие заявки: у каждой радиокнопки {@code gocombat} берёт текст до следующей радиокнопки
     * (у последней до конца формы). Так не важно, как именно размечена строка заявки.
     */
    private static List<ChaosApplication> readApplications() {
        List<ChaosApplication> applications = new ArrayList<>();
        try {
            List<?> rows = executeJavaScript(READ_APPLICATIONS);
            if (rows == null)
                return applications;
            for (Object row : rows) {
                List<?> cells = (List<?>) row;
                ChaosApplication.parse(String.valueOf(cells.get(0)), String.valueOf(cells.get(1)))
                        .ifPresent(applications::add);
            }
        } catch (RuntimeException e) {
            log.warn("Can't read the applications", e);
        }
        return applications;
    }

    /**
     * Подаёт свою заявку на хаотичный бой с разбросом силы противников ±50%.
     * Если такого варианта в списке нет, заявку всё равно подаём, с тем, что выбрано по умолчанию.
     *
     * @return true, если нажали «Подать заявку»
     */
    private boolean submitApplication() {
        if (!applicationChaos.isDisplayed())
            return false;
        applicationChaos.click();
        String strength = chooseOpponentsStrength();
        open.click();
        log.info("Submitted a chaotic battle application, opponents' strength {}", strength);
        return true;
    }

    /**
     * Выбирает в списке «Сила противников» вариант с {@link #OPPONENTS_STRENGTH}.
     *
     * @return текст выбранного варианта (как на странице)
     */
    private String chooseOpponentsStrength() {
        try {
            opponentsStrength.selectOptionContainingText(OPPONENTS_STRENGTH);
        } catch (RuntimeException e) {
            log.warn("Can't choose «{}» in the opponents' strength list, keep the default", OPPONENTS_STRENGTH, e);
        }
        try {
            return opponentsStrength.getSelectedOptionText();
        } catch (RuntimeException e) {
            return "unknown";
        }
    }

    /**
     * Персонаж уже в заявке: в шапке страницы «Ожидаем начала группового боя».
     */
    static boolean isWaitingForOwnBattle(String pageText) {
        return pageText != null && pageText.contains("Ожидаем начала");
    }

    /**
     * Бой начался, если основной фрейм перешёл на battle*.pl или в верхнем документе видна «Вперёд».
     * Второй признак нужен, потому что при старте боя игра иногда перезагружает весь верхний
     * документ, и адрес фрейма прочитать не получается.
     */
    private static boolean battleStarted() {
        if (isBattlePage(gameFramePath()))
            return true;
        try {
            Boolean commitVisible = executeJavaScript(
                    "const b = top.document.querySelector('button[action=commit]');"
                            + "return !!b && b.offsetWidth > 0 && getComputedStyle(b).visibility !== 'hidden';");
            return Boolean.TRUE.equals(commitVisible);
        } catch (RuntimeException e) {
            return false;
        }
    }

    /**
     * @return путь страницы в основном фрейме игры («/zayavka.pl», «/main.pl», «/battle4.pl») или пустая
     * строка, если фрейм сейчас перезагружается
     */
    private static String gameFramePath() {
        try {
            String path = executeJavaScript("return location.pathname;");
            return path == null ? "" : path;
        } catch (RuntimeException e) {
            return "";
        }
    }

    /**
     * Бой начался: основной фрейм переходит на battle.pl, battle2.pl, battle3.pl, battle4.pl…
     */
    static boolean isBattlePage(String path) {
        return path != null && path.matches("/battle\\d*\\.pl");
    }

    /**
     * Сколько минут осталось до начала боя, по тексту страницы заявок. Берём из своей заявки,
     * сразу после списка участников: «23:42 (ЧУ-Ж-ОЙ [8], …, Amlett [8]) (7-9) Бой начнется через 2.2 мин., …».
     * Если свою заявку найти не удалось, берём строку в шапке «Ваш бой начнется через 2.2 мин.».
     *
     * @return минуты как на странице («2.2») или null, если времени на странице нет
     */
    static String minutesToStart(String pageText, String login) {
        if (pageText == null)
            return null;
        if (login != null && !login.isBlank()) {
            Matcher application = APPLICATION.matcher(pageText);
            while (application.find())
                if (application.group(1).contains(login.trim() + " ["))
                    return application.group(2);
        }
        Matcher own = OWN_BATTLE.matcher(pageText);
        return own.find() ? own.group(1) : null;
    }

}
