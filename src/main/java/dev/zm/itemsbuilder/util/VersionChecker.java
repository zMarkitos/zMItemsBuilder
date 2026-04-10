package dev.zm.itemsbuilder.util;

import dev.zm.itemsbuilder.zMItemsBuilder;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

public final class VersionChecker {

    private static final String API_URL = "https://api.spigotmc.org/legacy/update.php?resource=%s";
    private static final String RESOURCE_ID = "134087";
    private static final String VERSION_PAGE_URL = "https://www.spigotmc.org/resources/zmitemsbuilder-advanced-custom-items-system.%s/";
    private static final String NOTIFICATION_PERMISSION = "zmitemsbuilder.update";

    private final zMItemsBuilder plugin;
    private final HttpClient httpClient;
    private final Set<UUID> notifiedPlayers = ConcurrentHashMap.newKeySet();

    private volatile CompletableFuture<UpdateResult> updateFuture = CompletableFuture
            .completedFuture(UpdateResult.disabled());

    private volatile UpdateResult cachedResult = UpdateResult.disabled();

    public VersionChecker(zMItemsBuilder plugin) {
        this.plugin = plugin;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    public void refresh() {
        notifiedPlayers.clear();

        if (!plugin.settings().updateSettings().enabled()) {
            this.cachedResult = UpdateResult.disabled();
            this.updateFuture = CompletableFuture.completedFuture(this.cachedResult);
            return;
        }

        String currentVersion = sanitizeVersion(plugin.getDescription().getVersion());

        CompletableFuture<UpdateResult> future = checkAsync(currentVersion);
        this.updateFuture = future;

        future.thenAccept(result -> {
            if (this.updateFuture == future) {
                this.cachedResult = result;
                if (result.updateAvailable()) {
                    Bukkit.getScheduler().runTask(plugin, () -> notifyOnlinePlayers(result));
                }
            }
        });
    }

    public void notifyPlayer(Player player) {
        if (!plugin.settings().updateSettings().enabled())
            return;
        if (!canReceiveNotifications(player))
            return;

        UpdateResult result = cachedResult;

        if (result.updateAvailable()) {
            sendNotification(player, result);
            return;
        }

        CompletableFuture<UpdateResult> future = updateFuture;
        if (future == null)
            return;

        future.thenAccept(updateResult -> {
            if (this.updateFuture != future)
                return;
            if (!updateResult.updateAvailable())
                return;

            Bukkit.getScheduler().runTask(plugin, () -> {
                if (this.updateFuture != future)
                    return;
                if (player.isOnline() && canReceiveNotifications(player)) {
                    sendNotification(player, updateResult);
                }
            });
        });
    }

    private void notifyOnlinePlayers(UpdateResult result) {
        if (!result.updateAvailable() || !plugin.settings().updateSettings().enabled())
            return;

        for (Player player : Bukkit.getOnlinePlayers()) {
            if (canReceiveNotifications(player)) {
                sendNotification(player, result);
            }
        }
    }

    private CompletableFuture<UpdateResult> checkAsync(String currentVersion) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(String.format(API_URL, RESOURCE_ID)))
                        .header("User-Agent", "zMItemsBuilder/" + currentVersion)
                        .timeout(Duration.ofSeconds(10))
                        .GET()
                        .build();

                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() != 200) {
                    plugin.getLogger().warning("Spigot update check failed: HTTP " + response.statusCode());
                    return UpdateResult.disabled();
                }

                String latestVersion = sanitizeVersion(response.body().trim());

                if (latestVersion.isEmpty() || !isNewerVersion(latestVersion, currentVersion)) {
                    return UpdateResult.disabled();
                }

                return new UpdateResult(
                        true,
                        currentVersion,
                        latestVersion,
                        String.format(VERSION_PAGE_URL, RESOURCE_ID));

            } catch (Exception e) {
                plugin.getLogger().warning("Spigot update check failed: " + e.getMessage());
            }

            return UpdateResult.disabled();
        });
    }

    private boolean isNewerVersion(String latest, String current) {
        return compareVersions(latest, current) > 0;
    }

    private int compareVersions(String left, String right) {
        List<Integer> leftParts = parseVersionParts(left);
        List<Integer> rightParts = parseVersionParts(right);
        int size = Math.max(leftParts.size(), rightParts.size());

        for (int i = 0; i < size; i++) {
            int l = i < leftParts.size() ? leftParts.get(i) : 0;
            int r = i < rightParts.size() ? rightParts.get(i) : 0;

            if (l != r) {
                return Integer.compare(l, r);
            }
        }
        return 0;
    }

    private List<Integer> parseVersionParts(String version) {
        List<Integer> parts = new ArrayList<>();

        if (version == null || version.isBlank())
            return parts;

        for (String part : version.split("[^0-9]+")) {
            if (part.isBlank())
                continue;

            try {
                parts.add(Integer.parseInt(part));
            } catch (NumberFormatException ignored) {
                parts.add(0);
            }
        }
        return parts;
    }

    private String sanitizeVersion(String version) {
        if (version == null)
            return "";
        return version.replaceAll("[^0-9.]", "");
    }

    private void sendNotification(Player player, UpdateResult result) {
        if (!notifiedPlayers.add(player.getUniqueId()))
            return;

        Map<String, String> placeholders = result.placeholders();

        Component title = plugin.language().rawMessage("messages.update-title", placeholders);
        Component subtitle = plugin.language().rawMessage("messages.update-subtitle", placeholders);

        player.showTitle(Title.title(
                title,
                subtitle,
                Title.Times.times(Duration.ofSeconds(1), Duration.ofSeconds(4), Duration.ofSeconds(1))));

        player.sendMessage(plugin.language().message("update-available", placeholders));
        player.sendMessage(plugin.language().message("update-link", placeholders));
    }

    private boolean canReceiveNotifications(Player player) {
        return player.isOp() || player.hasPermission(NOTIFICATION_PERMISSION);
    }

    public record UpdateResult(
            boolean updateAvailable,
            String currentVersion,
            String latestVersion,
            String versionPageUrl) {
        public static UpdateResult disabled() {
            return new UpdateResult(false, "", "", "");
        }

        public Map<String, String> placeholders() {
            return Map.of(
                    "current_version", currentVersion,
                    "latest_version", latestVersion,
                    "version_url", versionPageUrl);
        }
    }
}