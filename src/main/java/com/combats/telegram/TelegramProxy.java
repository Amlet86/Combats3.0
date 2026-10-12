package com.combats.telegram;

import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;

/**
 * Прокси для соединений с Telegram. Действует только на Telegram, браузер игры идёт напрямую.
 */
final class TelegramProxy {

    private TelegramProxy() {
    }

    /**
     * Разбирает адрес прокси из формы.
     *
     * @param text «socks5://host:port», «http://host:port» или пусто — без прокси
     * @return прокси или {@link Proxy#NO_PROXY}
     * @throws IllegalArgumentException если адрес записан неправильно
     */
    static Proxy parse(String text) {
        if (text == null || text.isBlank())
            return Proxy.NO_PROXY;
        URI uri;
        try {
            uri = new URI(text.trim());
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException("Wrong proxy address: " + text, e);
        }
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        Proxy.Type type = switch (scheme) {
            case "socks", "socks5" -> Proxy.Type.SOCKS;
            case "http", "https" -> Proxy.Type.HTTP;
            default -> throw new IllegalArgumentException(
                    "Proxy must start with socks5:// or http://, but it is: " + text);
        };
        if (uri.getHost() == null || uri.getPort() < 0)
            throw new IllegalArgumentException("Proxy must contain host and port, e.g. socks5://127.0.0.1:1080: " + text);
        if (uri.getUserInfo() != null)
            throw new IllegalArgumentException("Proxy with login and password isn't supported yet: " + uri.getHost());
        return new Proxy(type, new InetSocketAddress(uri.getHost(), uri.getPort()));
    }

}
