package org.cleargram.integration;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Parses only public Telegram channel URL forms needed by the NG-016 classifier. */
final class TelegramPublicChannelUrlParser {

    enum Kind {
        EXTERNAL,
        UNSAFE,
        PUBLIC_USERNAME
    }

    static final class Result {
        private static final Result EXTERNAL = new Result(Kind.EXTERNAL, null);
        private static final Result UNSAFE = new Result(Kind.UNSAFE, null);

        final Kind kind;
        final String username;

        private Result(Kind kind, String username) {
            this.kind = kind;
            this.username = username;
        }
    }

    private TelegramPublicChannelUrlParser() {
    }

    static Result parse(String rawUrl, String runtimeLinkPrefix) {
        if (rawUrl == null || rawUrl.isEmpty()) {
            return Result.UNSAFE;
        }
        try {
            URI uri = toHttpUri(rawUrl);
            if (uri == null || uri.getUserInfo() != null) {
                return Result.UNSAFE;
            }
            String scheme = uri.getScheme();
            if (scheme == null || !("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme))) {
                return Result.UNSAFE;
            }
            String host = uri.getHost();
            if (host == null) {
                return Result.UNSAFE;
            }
            host = host.toLowerCase(Locale.US);
            String subdomainUsername = subdomainUsername(host);
            if (subdomainUsername != null) {
                return parseUsernamePath(subdomainUsername, pathSegments(uri), false);
            }
            if (!isConfirmedTelegramHost(host, runtimeLinkPrefix)) {
                return Result.EXTERNAL;
            }
            return parseHostPath(pathSegments(uri));
        } catch (IllegalArgumentException | URISyntaxException ignored) {
            return Result.UNSAFE;
        }
    }

    private static URI toHttpUri(String rawUrl) throws URISyntaxException {
        URI uri = new URI(rawUrl);
        if (uri.getScheme() != null) {
            return uri;
        }
        return new URI("https://" + rawUrl);
    }

    private static Result parseHostPath(List<String> segments) {
        if (segments.isEmpty()) {
            return Result.UNSAFE;
        }
        if ("s".equalsIgnoreCase(segments.get(0))) {
            if (segments.size() < 2) {
                return Result.UNSAFE;
            }
            return parseUsernamePath(segments.get(1), segments.subList(2, segments.size()), true);
        }
        return parseUsernamePath(segments.get(0), segments.subList(1, segments.size()), true);
    }

    private static Result parseUsernamePath(String username, List<String> remainder, boolean rejectReserved) {
        if (!isPublicUsername(username) || rejectReserved && isReservedPath(username)) {
            return Result.UNSAFE;
        }
        if (remainder.size() > 1 || remainder.size() == 1 && !isPostId(remainder.get(0))) {
            return Result.UNSAFE;
        }
        return new Result(Kind.PUBLIC_USERNAME, username);
    }

    private static List<String> pathSegments(URI uri) {
        String path = uri.getRawPath();
        if (path == null || path.isEmpty() || "/".equals(path)) {
            return new ArrayList<>();
        }
        if (!path.startsWith("/") || path.contains("//")) {
            throw new IllegalArgumentException();
        }
        String[] split = path.substring(1).split("/", -1);
        ArrayList<String> result = new ArrayList<>();
        for (int i = 0; i < split.length; i++) {
            if (split[i].isEmpty() && i == split.length - 1) {
                continue;
            }
            if (split[i].isEmpty() || split[i].indexOf('%') >= 0) {
                throw new IllegalArgumentException();
            }
            result.add(split[i]);
        }
        return result;
    }

    private static boolean isConfirmedTelegramHost(String host, String runtimeLinkPrefix) {
        return "t.me".equals(host)
                || "telegram.me".equals(host)
                || "telegram.dog".equals(host)
                || host.equals(normalizeRuntimeHost(runtimeLinkPrefix));
    }

    private static String subdomainUsername(String host) {
        if (!host.endsWith(".t.me") || host.length() <= ".t.me".length()) {
            return null;
        }
        String username = host.substring(0, host.length() - ".t.me".length());
        return username.indexOf('.') < 0 ? username : null;
    }

    private static String normalizeRuntimeHost(String runtimeLinkPrefix) {
        if (runtimeLinkPrefix == null || runtimeLinkPrefix.isEmpty()) {
            return "";
        }
        String value = runtimeLinkPrefix.trim();
        if (value.regionMatches(true, 0, "https://", 0, 8)) {
            value = value.substring(8);
        } else if (value.regionMatches(true, 0, "http://", 0, 7)) {
            value = value.substring(7);
        }
        while (value.endsWith("/")) {
            value = value.substring(0, value.length() - 1);
        }
        return value.indexOf('/') >= 0 || value.indexOf('@') >= 0 ? "" : value.toLowerCase(Locale.US);
    }

    private static boolean isPublicUsername(String username) {
        if (username == null || username.isEmpty()) {
            return false;
        }
        for (int i = 0; i < username.length(); i++) {
            char character = username.charAt(i);
            if (!(character >= 'a' && character <= 'z')
                    && !(character >= 'A' && character <= 'Z')
                    && !(character >= '0' && character <= '9')
                    && character != '_') {
                return false;
            }
        }
        return true;
    }

    private static boolean isReservedPath(String value) {
        String path = value.toLowerCase(Locale.US);
        return "c".equals(path) || "joinchat".equals(path) || "m".equals(path)
                || "share".equals(path) || "msg".equals(path) || "proxy".equals(path)
                || "socks".equals(path) || "iv".equals(path) || "resolve".equals(path)
                || "invoice".equals(path) || "nft".equals(path) || "bg".equals(path)
                || "login".equals(path) || "addstickers".equals(path) || "addemoji".equals(path)
                || "boost".equals(path) || "call".equals(path) || "contact".equals(path)
                || "folder".equals(path) || "addlist".equals(path);
    }

    private static boolean isPostId(String value) {
        if (value == null || value.isEmpty()) {
            return false;
        }
        for (int i = 0; i < value.length(); i++) {
            if (!Character.isDigit(value.charAt(i))) {
                return false;
            }
        }
        return true;
    }
}
