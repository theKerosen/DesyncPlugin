package org.ladyluh.desync.managers;

import org.bukkit.entity.Player;
import org.ladyluh.desync.Desync;
import org.slf4j.Logger;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class CooldownManager {

    private final Desync plugin;
    private final Logger logger;
    private final Map<UUID, Map<String, Long>> cooldowns = new ConcurrentHashMap<>();
    private final Map<UUID, Long> globalCooldowns = new ConcurrentHashMap<>();
    private ConfigurationManager configManager;
    private long defaultEventCooldownMs;
    private long defaultGlobalCooldownMs;


    public CooldownManager(Desync plugin) {
        this.plugin = plugin;
        this.logger = plugin.getPluginLogger();

        this.defaultEventCooldownMs = 30 * 1000L;
        this.defaultGlobalCooldownMs = 5 * 1000L;
    }

    /**
     * Called by ConfigurationManager after config is loaded or reloaded.
     */
    public void reloadSettings() {
        this.configManager = plugin.getConfigurationManager();


        this.defaultEventCooldownMs = configManager.getDefaultEventCooldownMs();
        this.defaultGlobalCooldownMs = configManager.getDefaultGlobalCooldownMs();
        logger.debug("CooldownManager settings reloaded. Default event: {}ms, Default global: {}ms", defaultEventCooldownMs, defaultGlobalCooldownMs);


    }


    /**
     * Checks if a player is currently on cooldown for a specific event key.
     *
     * @param player   The player.
     * @param eventKey The key for the event type.
     * @return True if the player is on cooldown, false otherwise.
     */
    public boolean isOnEventCooldown(Player player, String eventKey) {
        Map<String, Long> playerCooldowns = cooldowns.get(player.getUniqueId());
        if (playerCooldowns == null) {
            return false;
        }
        Long expiryTime = playerCooldowns.get(eventKey.toLowerCase());
        if (expiryTime == null) {
            return false;
        }
        return System.currentTimeMillis() < expiryTime;
    }

    /**
     * Applies the configured cooldown for a specific event key to a player.
     * Retrieves the cooldown duration from the ConfigurationManager.
     *
     * @param player   The player.
     * @param eventKey The key for the event type.
     */
    public void applyEventCooldown(Player player, String eventKey) {

        long durationMs = configManager.getEventCooldownMs(eventKey);

        if (durationMs <= 0) {
            logger.debug("Skipping event cooldown '{}' for {} (duration {}ms <= 0).", eventKey.toLowerCase(), player.getName(), durationMs);
            return;
        }

        long expiryTime = System.currentTimeMillis() + durationMs;
        cooldowns.computeIfAbsent(player.getUniqueId(), k -> new ConcurrentHashMap<>())
                .put(eventKey.toLowerCase(), expiryTime);
        logger.debug("Applied event cooldown '{}' ({}) for {}", eventKey.toLowerCase(), durationMs, player.getName());
    }

    /**
     * Applies a specific cooldown duration for an event key to a player.
     * (This version might be less needed if cooldowns are config-driven, but keep for flexibility or force triggers)
     *
     * @param player     The player.
     * @param eventKey   The key for the event type.
     * @param durationMs The cooldown duration in milliseconds.
     * UNUSED FOR NOW.
     */
    public void applyEventCooldown(Player player, String eventKey, long durationMs) {
        if (durationMs <= 0) {
            logger.debug("Skipping manual event cooldown '{}' for {} (duration {}ms <= 0).", eventKey.toLowerCase(), player.getName(), durationMs);
            return;
        }
        long expiryTime = System.currentTimeMillis() + durationMs;
        cooldowns.computeIfAbsent(player.getUniqueId(), k -> new ConcurrentHashMap<>())
                .put(eventKey.toLowerCase(), expiryTime);
        logger.debug("Applied manual event cooldown '{}' ({}) for {}", eventKey.toLowerCase(), durationMs, player.getName());
    }


    /**
     * Checks if a player is currently on the global cooldown (applied after *any* event).
     *
     * @param player The player.
     * @return True if the player is on global cooldown, false otherwise.
     */
    public boolean isOnGlobalCooldown(Player player) {
        Long expiryTime = globalCooldowns.get(player.getUniqueId());
        if (expiryTime == null) {
            return false;
        }
        return System.currentTimeMillis() < expiryTime;
    }

    /**
     * Applies the default global cooldown to a player.
     * This should typically be called after an event is successfully triggered.
     * Retrieves the duration from the ConfigurationManager.
     *
     * @param player The player.
     */
    public void applyGlobalCooldown(Player player) {
        long durationMs = configManager.getDefaultGlobalCooldownMs();

        if (durationMs <= 0) {
            logger.debug("Skipping global cooldown for {} (duration {}ms <= 0).", player.getName(), durationMs);
            return;
        }

        long expiryTime = System.currentTimeMillis() + durationMs;
        globalCooldowns.put(player.getUniqueId(), expiryTime);
        logger.debug("Applied global cooldown ({}) for {}", durationMs, player.getName());
    }


    /**
     * Clears the cooldown for a specific event key for a player.
     *
     * @param playerUuid The UUID of the player.
     * @param eventKey   The key for the event type.
     */
    public void clearEventCooldown(UUID playerUuid, String eventKey) {
        Map<String, Long> playerCooldowns = cooldowns.get(playerUuid);
        if (playerCooldowns != null) {
            playerCooldowns.remove(eventKey.toLowerCase());
            logger.debug("Cleared event cooldown '{}' for {}", eventKey.toLowerCase(), playerUuid);
        }
    }

    /**
     * Clears all cooldowns (event and global) for a player.
     *
     * @param playerUuid The UUID of the player.
     */
    public void clearCooldowns(UUID playerUuid) {
        cooldowns.remove(playerUuid);
        globalCooldowns.remove(playerUuid);
        logger.debug("Cleared all cooldowns for {}", playerUuid);
    }

    /**
     * Removes all cooldowns for all players.
     */
    public void clearAllCooldowns() {
        cooldowns.clear();
        globalCooldowns.clear();
        logger.debug("Cleared all cooldowns for all players.");
    }

    /**
     * Cleanup method for players who leave.
     *
     * @param playerUuid The UUID of the player who left.
     */
    public void removePlayer(UUID playerUuid) {
        cooldowns.remove(playerUuid);
        globalCooldowns.remove(playerUuid);
        logger.debug("Removed cooldown data for disconnected player {}", playerUuid);
    }


}