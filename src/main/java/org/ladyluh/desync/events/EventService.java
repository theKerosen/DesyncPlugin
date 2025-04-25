package org.ladyluh.desync.events;

import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.ladyluh.desync.Desync;
import org.ladyluh.desync.events.impl.*;
import org.ladyluh.desync.managers.ConfigurationManager;
import org.ladyluh.desync.managers.CooldownManager;
import org.slf4j.Logger;

import java.util.*;
import java.util.stream.Collectors;


/**
 * Manages the registration and execution of all player-specific desync events.
 */
public class EventService {

    private final Desync plugin;
    private final Logger logger;
    private final CooldownManager cooldownManager;
    private final Map<String, PlayerDesyncEvent> registeredEvents = new HashMap<>();
    private ConfigurationManager configManager;

    public EventService(@NotNull Desync plugin, @NotNull CooldownManager cooldownManager) {
        this.plugin = plugin;
        this.logger = plugin.getPluginLogger();
        this.cooldownManager = cooldownManager;

        registerDefaultEvents();
    }

    /**
     * Called by ConfigurationManager after config is loaded or reloaded.
     */
    public void reloadSettings() {
        this.configManager = plugin.getConfigurationManager();


        logger.debug("EventService settings reloaded (no specific settings managed here).");
    }


    /**
     * Registers a PlayerDesyncEvent implementation.
     *
     * @param event The event implementation to register.
     */
    public void registerEvent(@NotNull PlayerDesyncEvent event) {
        String key = event.getKey().toLowerCase();
        if (registeredEvents.containsKey(key)) {
            logger.warn("Duplicate event key registered: '{}'. Overwriting.", key);
        }
        registeredEvents.put(key, event);
        logger.debug("Registered event: '{}' ({})", key, event.getClass().getSimpleName());
    }

    /**
     * Registers all the default event types provided by the plugin.
     */
    private void registerDefaultEvents() {
        logger.info("Registering default desync event types...");
        registerEvent(new PeripheralParticleEvent());
        registerEvent(new FootstepEvent());
        registerEvent(new AnimalStareEvent());
        registerEvent(new GhostBlockEvent());
        registerEvent(new MisplacedSoundEvent());
        registerEvent(new BlockFlickerEvent());
        registerEvent(new StalkerSpawnEvent());
        registerEvent(new VisualBlockInteractEvent());
        registerEvent(new FakeChatMessageEvent());
        registerEvent(new InventoryShiftEvent());
        registerEvent(new FakeDamageEvent());
        registerEvent(new ActionBarEvent());
        registerEvent(new FakeWindowBreakSoundEvent());
        registerEvent(new FakeItemDurabilityEvent());
        registerEvent(new AnimalFollowEvent());
        registerEvent(new VisualStructureEvent());
        registerEvent(new PersistentParticleEvent());
        registerEvent(new BlockVanishEvent());
        registerEvent(new FakePlayerJoinEvent());

        logger.info("Finished registering {} event types.", registeredEvents.size());
    }

    /**
     * Gets a registered event by its key.
     *
     * @param key The event key (case-insensitive).
     * @return The PlayerDesyncEvent instance, or null if not found.
     */
    public PlayerDesyncEvent getEventByKey(@NotNull String key) {
        return registeredEvents.get(key.toLowerCase());
    }

    /**
     * Gets a collection of all registered event types.
     *
     * @return A collection of PlayerDesyncEvent instances.
     */
    public Collection<PlayerDesyncEvent> getAllEvents() {
        return registeredEvents.values();
    }

    /**
     * Gets a list of event keys that are currently triggerable for a player,
     * considering only the event's canTrigger() check (not cooldowns).
     * This is primarily for the scheduler to pick from eligible types.
     *
     * @param player The player to check triggerability for.
     * @return A list of event keys for events that can currently trigger.
     */
    public List<String> getCurrentlyTriggerableEventKeys(@NotNull Player player) {

        return registeredEvents.values().stream()
                .filter(event -> {
                    try {
                        return event.canTrigger(player);
                    } catch (Exception e) {

                        logger.error("Error during canTrigger check for event {} for player {}", event.getKey(), player.getName(), e);
                        return false;
                    }
                })
                .map(PlayerDesyncEvent::getKey)
                .collect(Collectors.toList());
    }


    /**
     * Attempts to trigger a specific event type for a player.
     * Checks if the event exists, if the player is on cooldown (global or event-specific),
     * and if the event's pre-conditions (canTrigger) are met.
     * Applies cooldowns if triggered.
     *
     * @param player   The player to trigger the event for.
     * @param eventKey The key of the event to trigger.
     * @param force    If true, bypasses cooldown and canTrigger checks.
     * @return True if the event was triggered, false otherwise.
     */
    public boolean triggerEvent(@NotNull Player player, @NotNull String eventKey, boolean force) {

        if (configManager == null) {
            logger.error("ConfigurationManager is null in EventService.triggerEvent! Cannot trigger events.");
            return false;
        }

        PlayerDesyncEvent event = getEventByKey(eventKey);

        if (event == null) {
            logger.warn("Attempted to trigger unknown event key: '{}' for {}", eventKey, player.getName());
            return false;
        }


        if (!force && cooldownManager.isOnGlobalCooldown(player)) {
            logger.debug("Skipping event '{}' for {} due to global cooldown.", eventKey, player.getName());
            return false;
        }


        if (!force && cooldownManager.isOnEventCooldown(player, event.getKey())) {
            logger.debug("Skipping event '{}' for {} due to event cooldown.", eventKey, player.getName());
            return false;
        }


        if (!force) {
            try {
                if (!event.canTrigger(player)) {
                    logger.debug("Skipping event '{}' for {} as canTrigger check failed.", event.getKey(), player.getName());
                    return false;
                }
            } catch (Exception e) {

                logger.error("Error during canTrigger check for event {} for player {} before triggering", event.getKey(), player.getName(), e);
                return false;
            }
        }


        try {
            logger.info("Triggering event '{}' for {}", event.getKey(), player.getName());

            event.trigger(player, plugin);


            cooldownManager.applyEventCooldown(player, event.getKey());
            cooldownManager.applyGlobalCooldown(player);

            return true;
        } catch (Exception e) {
            logger.error("Error triggering event '{}' for {}", event.getKey(), player.getName(), e);

            return false;
        }
    }

    /**
     * Attempts to trigger a specific event type for a player, respecting cooldowns and pre-conditions.
     *
     * @param player   The player to trigger the event for.
     * @param eventKey The key of the event to trigger.
     * @return True if the event was triggered, false otherwise.
     * UNUSED FOR NOW.
     */
    public boolean triggerEvent(@NotNull Player player, @NotNull String eventKey) {
        return triggerEvent(player, eventKey, false);
    }

    /**
     * Provides a list of triggerable event keys for use in commands/messages.
     *
     * @return A list of event keys.
     */
    public Collection<String> getTriggerableEventKeys() {
        return registeredEvents.keySet();
    }

    /**
     * Cleanup method called on plugin disable.
     * Cancels all active tasks associated with events (like stare tasks).
     */
    public void cleanup() {
        logger.info("Cleaning up EventService resources...");


        new ArrayList<>(AnimalStareEvent.activeStaresByPlayer.keySet()).forEach(playerUUID -> AnimalStareEvent.cancelAllPlayerStares(playerUUID, logger));


        new ArrayList<>(StalkerSpawnEvent.activeStalkers.keySet()).forEach(playerUUID -> StalkerSpawnEvent.cancelStalkerForPlayer(playerUUID, logger));


        new ArrayList<>(AnimalFollowEvent.activeFollowsByPlayer.keySet()).forEach(playerUUID -> AnimalFollowEvent.cancelAllPlayerFollows(playerUUID, logger));


        logger.debug("EventService cleanup complete.");
    }

    /**
     * Helper to clean up resources associated with a specific player when they quit.
     * Called by the PlayerQuitListener.
     *
     * @param playerUUID The UUID of the player who quit.
     */
    public void handlePlayerQuit(@NotNull UUID playerUUID) {
        logger.debug("Handling player quit for {} in EventService.", playerUUID);

        AnimalStareEvent.cancelAllPlayerStares(playerUUID, logger);
        StalkerSpawnEvent.cancelStalkerForPlayer(playerUUID, logger);
        AnimalFollowEvent.cancelAllPlayerFollows(playerUUID, logger);


    }
}