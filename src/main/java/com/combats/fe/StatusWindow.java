package com.combats.fe;

import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

import javax.swing.*;
import javax.swing.border.EmptyBorder;

import com.combats.utils.GameControl;

/**
 * Окно статуса на время игры: показывает, чем занят бот, и даёт остановить игру кнопкой «Stop».
 * После окончания игры показывает итог, а кнопка превращается в «Close».
 */
class StatusWindow extends JFrame {

    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss");

    private final JLabel statusLabel = new JLabel("Starting...");
    private final JButton button = new JButton("Stop");
    private boolean gameOver;

    /**
     * Собирает окно. Игру останавливает через {@link GameControl#requestStop()}.
     */
    StatusWindow() {
        super("CombatsBot");
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        setResizable(false);

        statusLabel.setBorder(new EmptyBorder(0, 0, 10, 0));
        statusLabel.setAlignmentX(CENTER_ALIGNMENT);
        button.setAlignmentX(CENTER_ALIGNMENT);
        button.addActionListener(e -> onButton());
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                onButton();
            }
        });

        Box mainBox = Box.createVerticalBox();
        mainBox.setBorder(new EmptyBorder(12, 12, 12, 12));
        mainBox.add(statusLabel);
        mainBox.add(button);
        setContentPane(mainBox);
        setSize(420, 110);
        setLocationRelativeTo(null);
    }

    /**
     * Показывает текущее действие бота. Можно вызывать из любого потока.
     */
    void showStatus(String status) {
        // после «Stop» оставляем на экране «Stopping...», а не последнее действие игры
        if (GameControl.isStopRequested())
            return;
        String text = LocalTime.now().format(TIME_FORMAT) + "  " + status;
        SwingUtilities.invokeLater(() -> statusLabel.setText(text));
    }

    /**
     * Показывает итог игры и меняет «Stop» на «Close». Можно вызывать из любого потока.
     */
    void showResult(String result) {
        SwingUtilities.invokeLater(() -> {
            gameOver = true;
            statusLabel.setText("<html>" + escapeHtml(result) + "</html>");
            statusLabel.setToolTipText(result);
            button.setText("Close");
            button.setEnabled(true);
        });
    }

    /**
     * «Stop» (или крестик окна) во время игры просит её остановиться; после игры закрывает окно.
     */
    private void onButton() {
        if (gameOver) {
            dispose();
            return;
        }
        if (!button.isEnabled())
            return;
        button.setEnabled(false);
        statusLabel.setText("Stopping... the browser will be closed");
        GameControl.requestStop();
    }

    /**
     * Экранирует текст для показа в JLabel с HTML-переносом строк.
     */
    private static String escapeHtml(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

}
