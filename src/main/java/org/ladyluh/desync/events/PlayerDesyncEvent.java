package org.ladyluh.desync.events;

import org.bukkit.entity.Player;
import org.ladyluh.desync.Desync;

/**
 * Represents a single, player-specific desync event.
 * Implementing classes should encapsulate the logic for triggering that specific effect.
 */
public interface PlayerDesyncEvent {

    /**
     * Gets a unique key identifying this type of event.
     * Used for configuration, commands, and cooldowns.
     * @return The unique event key (lowercase, no spaces).
     */
    String getKey();

    /**
     * Gets a brief, human-readable description of this event type.
     * @return The event description.
     */
    String getDescription();

    /**
     * Gets the default cooldown duration for this event type in milliseconds.
     * This can be overridden by the CooldownManager if needed, but provides a default.
     * @return The default cooldown duration in milliseconds.
     */
    long getDefaultCooldownMs();

    /**
     * Checks if this event type can potentially be triggered for the given player
     * under the current conditions (e.g., requires nearby entities, specific block types).
     * This is a quick pre-check *before* cooldowns or chance calculations.
     * @param player The player to check.
     * @return True if the event is potentially triggerable, false otherwise.
     */
    boolean canTrigger(Player player);

    /**
     * Triggers the specific desync event effect for the given player.
     * This method should contain the ProtocolLib packet sending or other game manipulation logic.
     * @param player The player to trigger the event for.
     * @param plugin The main plugin instance, providing access to ProtocolManager, Logger, etc.
     */
    void trigger(Player player, Desync plugin);
}