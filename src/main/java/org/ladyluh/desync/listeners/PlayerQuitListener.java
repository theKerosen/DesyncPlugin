package org.ladyluh.desync.listeners;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.ladyluh.desync.Desync;
import org.ladyluh.desync.managers.CooldownManager;
import org.ladyluh.desync.events.EventService;

/**
 * Listens for players leaving the server to perform necessary cleanup of
 * player-specific desync event data (cooldowns, active tasks, etc.).
 */
public class PlayerQuitListener implements Listener {

    private final Desync plugin;
    private final CooldownManager cooldownManager;
    private final EventService eventService;

    public PlayerQuitListener(Desync plugin, CooldownManager cooldownManager, EventService eventService) {
        this.plugin = plugin;
        this.cooldownManager = cooldownManager;
        this.eventService = eventService;
    }

    /**
     * Handles the PlayerQuitEvent. Cleans up cooldowns and active event tasks for the leaving player.
     *
     * @param event The PlayerQuitEvent.
     */
    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        plugin.getPluginLogger().debug("PlayerQuitEvent for {}. Initiating cleanup.", player.getName());


        if (cooldownManager != null) {
            cooldownManager.removePlayer(player.getUniqueId());
            plugin.getPluginLogger().debug("Cleaned up cooldowns for {}", player.getName());
        }


        if (eventService != null) {
            eventService.handlePlayerQuit(player.getUniqueId());
            plugin.getPluginLogger().debug("Cleaned up event tasks for {}", player.getName());
        }

        plugin.getPluginLogger().debug("Cleanup complete for {}", player.getName());
    }
}