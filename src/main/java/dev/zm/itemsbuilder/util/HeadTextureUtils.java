package dev.zm.itemsbuilder.util;

import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.bukkit.Bukkit;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.profile.PlayerProfile;
import org.bukkit.profile.PlayerTextures;

public final class HeadTextureUtils {

    private static final Pattern URL_PATTERN = Pattern.compile("\"url\"\\s*:\\s*\"([^\"]+)\"");
    private static final ConcurrentHashMap<String, Optional<URL>> URL_CACHE = new ConcurrentHashMap<>();

    private HeadTextureUtils() {
    }

    public static boolean applyBase64Texture(SkullMeta meta, String base64) {
        if (meta == null || base64 == null || base64.isBlank()) {
            return false;
        }
        Optional<URL> skinUrl = URL_CACHE.computeIfAbsent(base64, HeadTextureUtils::parseSkinUrl);
        if (skinUrl.isEmpty()) {
            return false;
        }

        URL url = skinUrl.get();
        UUID uuid = UUID.nameUUIDFromBytes(url.toString().getBytes(StandardCharsets.UTF_8));
        PlayerProfile profile = Bukkit.createPlayerProfile(uuid);
        PlayerTextures textures = profile.getTextures();
        textures.setSkin(url);
        profile.setTextures(textures);
        meta.setOwnerProfile(profile);
        return true;
    }

    private static Optional<URL> parseSkinUrl(String raw) {
        try {
            String trimmed = raw.trim();
            if (trimmed.regionMatches(true, 0, "http", 0, 4)) {
                return Optional.of(new URL(trimmed));
            }

            // Base64-encoded JSON: {"textures":{"SKIN":{"url":"..."}}}
            String normalized = stripWhitespace(trimmed);
            byte[] decoded = Base64.getDecoder().decode(normalized);
            String json = new String(decoded, StandardCharsets.UTF_8);

            Matcher matcher = URL_PATTERN.matcher(json);
            if (!matcher.find()) {
                return Optional.empty();
            }
            String url = matcher.group(1).trim().replace("\\/", "/");
            if (url.isBlank() || !url.toLowerCase(Locale.ROOT).startsWith("http")) {
                return Optional.empty();
            }
            return Optional.of(new URL(url));
        } catch (Exception ignored) {
            return Optional.empty();
        }
    }

    private static String stripWhitespace(String input) {
        if (input == null || input.isEmpty()) {
            return "";
        }
        StringBuilder out = new StringBuilder(input.length());
        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            if (!Character.isWhitespace(c)) {
                out.append(c);
            }
        }
        return out.toString();
    }
}
