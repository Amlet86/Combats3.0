package com.combats.fe;

import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.Objects;

import javax.swing.*;

import com.combats.telegram.TelegramService;
import net.miginfocom.swing.MigLayout;

import static com.combats.utils.Properties.*;
import static com.combats.utils.Utils.getListHours;
import static com.combats.utils.Utils.getListMinutes;

/**
 * Форма настроек бота: логин и пароль, режим игры, время окончания, настройки Telegram-бота.
 * Поля логина, пароля и Telegram заполняются из файла, сохранённого при прошлом успешном входе
 * (его читает {@link com.combats.Start} при запуске программы).
 * <p>
 * Раскладка — MigLayout: две колонки, подписи справа, поля растягиваются.
 */
public class LoginForm extends JFrame {

    /** Две колонки: подписи прижаты вправо, поля занимают остальное место. */
    private static final String TWO_COLUMNS = "[right][grow, fill]";

    private final JTextField loginField = new JTextField(getUserLogin(), 20);
    private final JPasswordField passwordField = new JPasswordField(getUserPassword(), 20);

    private final JRadioButton pvpRadio = new JRadioButton("PvP", true);
    private final JRadioButton dungeonRadio = new JRadioButton("Dungeon");
    private final JCheckBox petCheck = new JCheckBox("Pet");
    private final JCheckBox browserOffCheck = new JCheckBox("Browser off");

    private final JComboBox<String> endTimeHours = new JComboBox<>(getListHours());
    private final JComboBox<String> endTimeMinutes = new JComboBox<>(getListMinutes());

    private final JTextField telegramOwnerField = new JTextField(getTelegramOwner(), 20);
    private final JTextField telegramBotTokenField = new JTextField(getTelegramBotToken(), 20);

    /**
     * Собирает форму. Поля Telegram видны всегда: если Owner или Token пустые, игра идёт без Telegram.
     * Кнопка «Start» (или Enter, или /play в Telegram) закрывает форму и запускает игру.
     */
    public LoginForm() {
        super("CombatsBot");
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setResizable(false);

        ButtonGroup typeOfGame = new ButtonGroup();
        typeOfGame.add(pvpRadio);
        typeOfGame.add(dungeonRadio);

        telegramOwnerField.setToolTipText("Your Telegram username: only this user can control the bot. "
                + "Leave Owner or Token empty to play without Telegram");
        telegramBotTokenField.setToolTipText("Bot token from @BotFather. "
                + "Leave Owner or Token empty to play without Telegram");

        JButton startButton = new JButton("Start");
        startButton.addActionListener(e -> startGame());

        JPanel form = new JPanel(new MigLayout("wrap 2", TWO_COLUMNS));
        form.add(new JLabel("Login:"));
        form.add(loginField);
        form.add(new JLabel("Password:"));
        form.add(passwordField);
        form.add(pair(pvpRadio, dungeonRadio), "skip");
        form.add(pair(petCheck, browserOffCheck), "skip");
        form.add(new JLabel("End time:"));
        form.add(endTimeHours, "split 4, growx 0");
        form.add(new JLabel("hours"));
        form.add(endTimeMinutes, "growx 0");
        form.add(new JLabel("minutes"));
        form.add(new JLabel("Telegram (optional)"), "span, center, gaptop 10");
        form.add(new JLabel("Owner:"));
        form.add(telegramOwnerField);
        form.add(new JLabel("Token:"));
        form.add(telegramBotTokenField);
        form.add(startButton, "span, right, gaptop 10, growx 0");

        setContentPane(form);
        getRootPane().setDefaultButton(startButton);
        pack();
        setLocationRelativeTo(null);

        // /play в Telegram, пока форма открыта, нажимает «Start», чтобы игра шла с настройками из формы
        GameLauncher.setFormStart(startButton::doClick);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosed(WindowEvent e) {
                GameLauncher.setFormStart(null);
            }
        });
    }

    /**
     * Два переключателя рядом, прижатые влево. Первая колонка у пар одинаковой ширины, поэтому
     * «Dungeon» и «Browser off» стоят друг под другом.
     */
    private static JPanel pair(JComponent first, JComponent second) {
        JPanel panel = new JPanel(new MigLayout("insets 0", "[80!][]"));
        panel.add(first);
        panel.add(second);
        return panel;
    }

    /**
     * Переносит значения формы в {@link com.combats.utils.Properties}, (пере)запускает Telegram-бота,
     * закрывает форму и запускает игру.
     */
    private void startGame() {
        setUserLogin(loginField.getText());
        setUserPassword(String.valueOf(passwordField.getPassword()));

        setTypeOfGame(pvpRadio.isSelected());
        setPet(petCheck.isSelected());
        setHeadless(browserOffCheck.isSelected());

        setEndTimeOfTheGame(Objects.requireNonNull(endTimeHours.getSelectedItem()),
                Objects.requireNonNull(endTimeMinutes.getSelectedItem()));

        setTelegramOwner(telegramOwnerField.getText());
        setTelegramBotToken(telegramBotTokenField.getText());
        TelegramService.startInBackground(GameLauncher::playFromTelegram);

        GameLauncher.setFormStart(null);
        dispose();
        GameLauncher.start();
    }

}
