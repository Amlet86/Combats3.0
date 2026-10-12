package com.combats.fe;

import com.combats.telegram.TelegramService;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.Objects;

import static com.combats.utils.Properties.*;
import static com.combats.utils.Utils.getListHours;
import static com.combats.utils.Utils.getListMinutes;
import static javax.swing.SwingConstants.CENTER;

/**
 * Форма настроек бота: логин и пароль, режим игры, время окончания, настройки Telegram-бота.
 * Поля логина, пароля и Telegram заполняются из файла, сохранённого при прошлом успешном входе
 * (его читает {@link com.combats.Start} при запуске программы).
 */
public class LoginForm extends JFrame {

    private JTextField loginField = new JTextField(getUserLogin(), 20);
    private JPasswordField passwordField = new JPasswordField(getUserPassword(), 20);

    private JTextField telegramOwnerField = new JTextField(getTelegramOwner(), 30);
    private JTextField telegramBotTokenField = new JTextField(getTelegramBotToken(), 30);
    private JTextField telegramProxyField = new JTextField(getTelegramProxy(), 30);

    private JRadioButton pvpRadio = new JRadioButton("PvP");

    private JCheckBox petCheck = new JCheckBox("Pet");
    private JCheckBox browserOffCheck = new JCheckBox("Browser off");

    private JComboBox endTimeHours = new JComboBox(getListHours());
    private JComboBox endTimeMinutes = new JComboBox(getListMinutes());

    /**
     * Собирает форму. Кнопка «Telegram» раскрывает поля бота, кнопка «Start» (или Enter, или /play
     * в Telegram) закрывает форму и запускает игру.
     */
    public LoginForm() {
        super("CombatsBot");
        setBounds(800, 500, 450, 300);
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setResizable(false);

        Box loginBox = Box.createHorizontalBox();
        JLabel loginLabel = new JLabel("Login:");
        loginBox.add(loginLabel);
        loginBox.add(Box.createHorizontalStrut(6));
        loginBox.add(loginField);

        Box passwordBox = Box.createHorizontalBox();
        JLabel passwordLabel = new JLabel("Password:");
        passwordBox.add(passwordLabel);
        passwordBox.add(Box.createHorizontalStrut(6));
        passwordBox.add(passwordField);

        loginLabel.setPreferredSize(passwordLabel.getPreferredSize());

        Box typeOfBattleBox = Box.createHorizontalBox();
        ButtonGroup bGroup = new ButtonGroup();
        bGroup.add(pvpRadio);
        JRadioButton dungeonRadio = new JRadioButton("Dungeon");
        bGroup.add(dungeonRadio);
        typeOfBattleBox.add(pvpRadio);
        pvpRadio.setSelected(true);
        typeOfBattleBox.add(Box.createHorizontalStrut(20));
        typeOfBattleBox.add(dungeonRadio);

        Box petAndBrowserBox = Box.createHorizontalBox();
        petAndBrowserBox.add(petCheck);
        petAndBrowserBox.add(Box.createHorizontalStrut(23));
        petAndBrowserBox.add(browserOffCheck);

        Box endTimeOfTheGameBox = Box.createHorizontalBox();
        endTimeOfTheGameBox.add(Box.createHorizontalStrut(25));
        endTimeOfTheGameBox.add(new JLabel("End time:"));
        endTimeOfTheGameBox.add(Box.createHorizontalStrut(5));
        endTimeOfTheGameBox.add(endTimeHours);
        endTimeOfTheGameBox.add(new JLabel("hours"));
        endTimeOfTheGameBox.add(Box.createHorizontalStrut(5));
        endTimeOfTheGameBox.add(endTimeMinutes);
        endTimeOfTheGameBox.add(new JLabel("minutes"));
        endTimeOfTheGameBox.add(Box.createHorizontalStrut(25));

        Box buttonsBox = Box.createHorizontalBox();
        buttonsBox.add(Box.createHorizontalGlue());
        JButton telegramButton = new JButton("Telegram");
        buttonsBox.add(telegramButton);
        buttonsBox.add(Box.createHorizontalStrut(140));
        JButton startButton = new JButton("Start");
        buttonsBox.add(startButton);

        JLabel telegramLabel = new JLabel("Telegram");
        telegramLabel.setHorizontalAlignment(CENTER);

        JLabel ownerLabel = new JLabel("Owner:");
        JLabel botTokenLabel = new JLabel("Token:");
        JLabel proxyLabel = new JLabel("Proxy:");
        telegramOwnerField.setToolTipText("Your Telegram username: only this user can control the bot");
        telegramBotTokenField.setToolTipText("Bot token from @BotFather");
        telegramProxyField.setToolTipText("For example socks5://127.0.0.1:1080 or http://127.0.0.1:8080; empty — no proxy");
        Box ownerBox = telegramFieldBox(ownerLabel, telegramOwnerField);
        Box botTokenBox = telegramFieldBox(botTokenLabel, telegramBotTokenField);
        Box proxyBox = telegramFieldBox(proxyLabel, telegramProxyField);
        ownerLabel.setPreferredSize(botTokenLabel.getPreferredSize());
        proxyLabel.setPreferredSize(botTokenLabel.getPreferredSize());

        Box mainBox = Box.createVerticalBox();
        mainBox.setBorder(new EmptyBorder(12, 12, 12, 12));
        mainBox.add(loginBox);
        mainBox.add(Box.createVerticalStrut(10));
        mainBox.add(passwordBox);
        mainBox.add(Box.createVerticalStrut(10));
        mainBox.add(typeOfBattleBox);
        mainBox.add(Box.createVerticalStrut(5));
        mainBox.add(petAndBrowserBox);
        mainBox.add(Box.createVerticalStrut(10));
        mainBox.add(endTimeOfTheGameBox);
        mainBox.add(Box.createVerticalStrut(10));
        mainBox.add(buttonsBox);
        setContentPane(mainBox);
        pack();

        telegramButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                buttonsBox.remove(telegramButton);
                mainBox.add(Box.createVerticalStrut(10));
                mainBox.add(telegramLabel, BorderLayout.CENTER);
                mainBox.add(Box.createVerticalStrut(10));
                mainBox.add(ownerBox);
                mainBox.add(Box.createVerticalStrut(10));
                mainBox.add(botTokenBox);
                mainBox.add(Box.createVerticalStrut(10));
                mainBox.add(proxyBox);
                pack();
            }
        });

        this.getRootPane().setDefaultButton(startButton);

        startButton.addActionListener(new ButtonStartListener());

        // /play в Telegram, пока форма открыта, нажимает «Start», чтобы игра шла с настройками из формы
        GameLauncher.setFormStart(startButton::doClick);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosed(WindowEvent e) {
                GameLauncher.setFormStart(null);
            }
        });
    }

    private static Box telegramFieldBox(JLabel label, JTextField field) {
        Box box = Box.createHorizontalBox();
        box.add(label);
        box.add(Box.createHorizontalStrut(6));
        box.add(field);
        return box;
    }

    /**
     * Обработчик кнопки «Start»: сохраняет настройки из формы, (пере)запускает Telegram-бота,
     * закрывает форму и запускает игру.
     */
    class ButtonStartListener implements ActionListener {

        /**
         * Переносит значения формы в {@link com.combats.utils.Properties} и запускает игру.
         */
        public void actionPerformed(ActionEvent e) {
            setUserLogin(loginField.getText());
            setUserPassword(String.valueOf(passwordField.getPassword()));

            setTypeOfGame(pvpRadio.isSelected());
            setPet(petCheck.isSelected());
            setHeadless(browserOffCheck.isSelected());

            setEndTimeOfTheGame(Objects.requireNonNull(endTimeHours.getSelectedItem()), Objects.requireNonNull(endTimeMinutes.getSelectedItem()));

            setTelegramOwner(telegramOwnerField.getText());
            setTelegramBotToken(telegramBotTokenField.getText());
            setTelegramProxy(telegramProxyField.getText());
            TelegramService.startInBackground(GameLauncher::playFromTelegram);

            GameLauncher.setFormStart(null);
            dispose();
            GameLauncher.start();
        }
    }

}
