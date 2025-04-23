package org.ladyluh.desync.managers;

import org.bukkit.entity.Player;
import org.ladyluh.desync.Desync;
import org.slf4j.Logger;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class CooldownManager {

    private final Logger logger;

    // Map: Player UUID -> Map: Event Key -> Cooldown Expiry Time (System.currentTimeMillis())
    private final Map<UUID, Map<String, Long>> cooldowns = new ConcurrentHashMap<>();

    // Map: Player UUID -> Global Cooldown Expiry Time (System.currentTimeMillis())
    private final Map<UUID, Long> globalCooldowns = new ConcurrentHashMap<>();

    // Default cooldown durations (in milliseconds)
    private static final long DEFAULT_EVENT_COOLDOWN_MS = 30 * 1000L; // 30 seconds
    private static final long DEFAULT_GLOBAL_COOLDOWN_MS = 5 * 1000L; // 5 seconds

    public CooldownManager(Desync plugin) {
        this.logger = plugin.getPluginLogger();
    }

    /**
     * Checks if a player is currently on cooldown for a specific event key.
     * @param player The player.
     * @param eventKey The key for the event type.
     * @return True if the player is on cooldown, false otherwise.
     */
    public boolean isOnEventCooldown(Player player, String eventKey) {
        Map<String, Long> playerCooldowns = cooldowns.get(player.getUniqueId());
        if (playerCooldowns == null) {
            return false; // No cooldowns for this player yet
        }
        Long expiryTime = playerCooldowns.get(eventKey.toLowerCase());
        if (expiryTime == null) {
            return false; // No cooldown for this specific event yet
        }
        return System.currentTimeMillis() < expiryTime;
    }

    /**
     * Applies the default cooldown for a specific event key to a player.
     * @param player The player.
     * @param eventKey The key for the event type.
     */
    public void applyEventCooldown(Player player, String eventKey) {
        applyEventCooldown(player, eventKey, DEFAULT_EVENT_COOLDOWN_MS);
    }

    /**
     * Applies a specific cooldown duration for an event key to a player.
     * @param player The player.
     * @param eventKey The key for the event type.
     * @param durationMs The cooldown duration in milliseconds.
     */
    public void applyEventCooldown(Player player, String eventKey, long durationMs) {
        long expiryTime = System.currentTimeMillis() + durationMs;
        cooldowns.computeIfAbsent(player.getUniqueId(), k -> new ConcurrentHashMap<>())
                .put(eventKey.toLowerCase(), expiryTime);
        logger.debug("Applied event cooldown '{}' ({}) for {}", eventKey.toLowerCase(), durationMs, player.getName());
    }

    /**
     * Checks if a player is currently on the global cooldown (applied after *any* event).
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
     * @param player The player.
     */
    public void applyGlobalCooldown(Player player) {
        long expiryTime = System.currentTimeMillis() + DEFAULT_GLOBAL_COOLDOWN_MS;
        globalCooldowns.put(player.getUniqueId(), expiryTime);
        logger.debug("Applied global cooldown ({}) for {}", DEFAULT_GLOBAL_COOLDOWN_MS, player.getName());
    }


    /**
     * Clears the cooldown for a specific event key for a player.
     * @param playerUuid The UUID of the player.
     * @param eventKey The key for the event type.
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
     * @param playerUuid The UUID of the player who left.
     */
    public void removePlayer(UUID playerUuid) {
        cooldowns.remove(playerUuid);
        globalCooldowns.remove(playerUuid);
        logger.debug("Removed cooldown data for disconnected player {}", playerUuid);
    }
}