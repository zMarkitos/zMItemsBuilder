package dev.zm.itemsbuilder.listener;

import dev.zm.itemsbuilder.zMItemsBuilder;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

public final class UpdateNotificationListener implements Listener {

    private final zMItemsBuilder plugin;

    public UpdateNotificationListener(zMItemsBuilder plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        if (plugin.versionChecker() != null) {
            plugin.versionChecker().notifyPlayer(event.getPlayer());
        }
    }
}
