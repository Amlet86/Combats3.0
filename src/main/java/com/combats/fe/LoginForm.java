package com.combats.fe;

import com.combats.be.TopLevelLogic;
import com.combats.telegram.Bot;
import com.combats.utils.FileWorker;
import com.combats.utils.GameControl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.Objects;

import static com.combats.telegram.InitializerTelegramBot.startTelegramBot;
import static com.combats.utils.Properties.*;
import static com.combats.utils.Utils.getListHours;
import static com.combats.utils.Utils.getListMinutes;
import static javax.swing.SwingConstants.CENTER;

/**
 * Форма настроек бота: логин и пароль, режим игры, время окончания, данные Telegram-бота.
 * Поля логина, пароля и Telegram заполняются из файла, сохранённого при прошлом успешном входе.
 */
public class LoginForm extends JFrame {

    private static final Logger log = LoggerFactory.getLogger(LoginForm.class);

    public static Bot bot;

    private String[] loginAndPassword = FileWorker.readUserDataFile();
    private JTextField loginField = new JTextField(loginAndPassword[0], 20);
    private JPasswordField passwordField = new JPasswordField(loginAndPassword[1], 20);

    private String[] telegramBotNameAndToken = FileWorker.readUserDataFile();
    private JTextField telegramBotNameField = new JTextField(telegramBotNameAndToken[2], 30);
    private JTextField telegramBotTokenField = new JTextField(telegramBotNameAndToken[3], 30);

    private JRadioButton pvpRadio = new JRadioButton("PvP");

    private JCheckBox petCheck = new JCheckBox("Pet");
    private JCheckBox browserOffCheck = new JCheckBox("Browser off");

    private JComboBox endTimeHours = new JComboBox(getListHours());
    private JComboBox endTimeMinutes = new JComboBox(getListMinutes());

    /**
     * Собирает форму. Кнопка «Telegram» раскрывает поля бота, кнопка «Start» (или Enter)
     * закрывает форму и запускает игру.
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

        Box botNameBox = Box.createHorizontalBox();
        JLabel botNameLabel = new JLabel("Name:");
        botNameBox.add(botNameLabel);
        botNameBox.add(Box.createHorizontalStrut(6));
        botNameBox.add(telegramBotNameField);

        Box botTokenBox = Box.createHorizontalBox();
        JLabel botTokenLabel = new JLabel("Token:");
        botTokenBox.add(botTokenLabel);
        botTokenBox.add(Box.createHorizontalStrut(6));
        botTokenBox.add(telegramBotTokenField);

        botNameLabel.setPreferredSize(botTokenLabel.getPreferredSize());

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
                mainBox.add(botNameBox);
                mainBox.add(Box.createVerticalStrut(10));
                mainBox.add(botTokenBox);
                pack();
            }
        });

        this.getRootPane().setDefaultButton(startButton);

        startButton.addActionListener(new ButtonStartListener());
        startButton.addActionListener(e -> this.dispose());
    }

    /**
     * Обработчик кнопки «Start»: сохраняет настройки из формы и запускает игру в отдельном потоке.
     */
    class ButtonStartListener implements ActionListener {

        /**
         * Переносит значения формы в {@link com.combats.utils.Properties} и запускает поток игры.
         */
        public void actionPerformed(ActionEvent e) {
            setUserLogin(loginField.getText());
            setUserPassword(String.valueOf(passwordField.getPassword()));

            setTypeOfGame(pvpRadio.isSelected());
            setPet(petCheck.isSelected());
            setHeadless(browserOffCheck.isSelected());

            setEndTimeOfTheGame(Objects.requireNonNull(endTimeHours.getSelectedItem()), Objects.requireNonNull(endTimeMinutes.getSelectedItem()));

            setTelegramBotName(telegramBotNameField.getText());
            setTelegramBotToken(telegramBotTokenField.getText());

            StatusWindow statusWindow = new StatusWindow();
            Thread gameThread = new Thread(() -> playGame(statusWindow), "game");
            GameControl.prepare(gameThread);
            GameControl.setStatusListener(statusWindow::showStatus);
            statusWindow.setVisible(true);
            gameThread.start();
        }
    }

    /**
     * Играет вне потока Swing, потому что игра длится часами и не должна блокировать интерфейс.
     * Поток не демонический, поэтому JVM продолжает работать после закрытия формы.
     * По окончании показывает итог игры в окне статуса.
     */
    private static void playGame(StatusWindow statusWindow) {
        String result;
        try {
            result = new TopLevelLogic().game();
        } catch (Error | RuntimeException e) {
            log.error("The game was stopped by an error", e);
            result = "The game was stopped by an error: " + e;
        }
        log.info(result);
        statusWindow.showResult(result);
    }

}

