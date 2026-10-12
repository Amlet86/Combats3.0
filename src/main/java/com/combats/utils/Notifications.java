package com.combats.utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Уведомления пользователю о ходе игры: итоги боёв, ошибки, окончание игры.
 * Код игры не знает, куда они уходят: получателя (Telegram) задаёт {@link #setNotifier}.
 * Пока получатель не задан, уведомления только пишутся в лог.
 */
public class Notifications {

    private static final Logger log = LoggerFactory.getLogger(Notifications.class);

    /**
     * Получатель уведомлений. Методы вызываются из потока игры и не должны бросать исключения.
     */
    public interface Notifier {

        /** Отправляет текст. */
        void message(String text);

        /** Отправляет картинку PNG с подписью. */
        void photo(String caption, byte[] png);
    }

    private static final Notifier LOG_ONLY = new Notifier() {
        @Override
        public void message(String text) {
        }

        @Override
        public void photo(String caption, byte[] png) {
        }
    };

    private static volatile Notifier notifier = LOG_ONLY;

    /**
     * Задаёт получателя уведомлений; null — только лог.
     */
    public static void setNotifier(Notifier newNotifier) {
        notifier = newNotifier == null ? LOG_ONLY : newNotifier;
    }

    /**
     * Отправляет текстовое уведомление.
     */
    public static void message(String text) {
        log.info("Notification: {}", text);
        notifier.message(text);
    }

    /**
     * Отправляет скриншот с подписью; без скриншота (браузер не запущен) отправляет только подпись.
     */
    public static void photo(String caption, byte[] png) {
        if (png == null) {
            message(caption);
            return;
        }
        log.info("Notification with a screenshot: {}", caption);
        notifier.photo(caption, png);
    }

}
