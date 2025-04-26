package org.ladyluh.desync.managers;

import org.bukkit.configuration.file.FileConfiguration;
import org.ladyluh.desync.Desync;
import org.slf4j.Logger;

import java.util.List;

/**
 * Manages the plugin's configuration settings loaded from config.yml.
 */
public class ConfigurationManager {

    private final Desync plugin;
    private final Logger logger;
    private FileConfiguration config;


    private long schedulerIntervalTicks = 20L * 5;
    private double baseEventProbability = 0.02;


    private double chanceMultiplierDarkness = 2.0;
    private double chanceMultiplierDimness = 1.4;
    private double chanceMultiplierUndergroundDeep = 1.5;
    private double chanceMultiplierUndergroundShallow = 1.2;
    private double chanceMultiplierIsolated = 1.75;
    private double chanceMultiplierNight = 1.6;
    private double chanceMultiplierNether = 1.3;
    private double chanceMultiplierEnd = 1.5;
    private double maxCalculatedChance = 0.35;


    private long defaultEventCooldownMs = 30 * 1000L;
    private long defaultGlobalCooldownMs = 5 * 1000L;


    public ConfigurationManager(Desync plugin) {
        this.plugin = plugin;
        this.logger = plugin.getPluginLogger();
        loadConfig();
    }

    /**
     * Loads the configuration from config.yml.
     */
    public void loadConfig() {
        plugin.saveDefaultConfig();
        config = plugin.getConfig();

        logger.debug("Loading configuration...");

        try {

            schedulerIntervalTicks = config.getLong("scheduler.interval-ticks", schedulerIntervalTicks);
            baseEventProbability = config.getDouble("scheduler.base-event-probability", baseEventProbability);


            chanceMultiplierDarkness = config.getDouble("chances.multipliers.darkness", chanceMultiplierDarkness);
            chanceMultiplierDimness = config.getDouble("chances.multipliers.dimness", chanceMultiplierDimness);
            chanceMultiplierUndergroundDeep = config.getDouble("chances.multipliers.underground-deep", chanceMultiplierUndergroundDeep);
            chanceMultiplierUndergroundShallow = config.getDouble("chances.multipliers.underground-shallow", chanceMultiplierUndergroundShallow);
            chanceMultiplierIsolated = config.getDouble("chances.multipliers.isolated", chanceMultiplierIsolated);
            chanceMultiplierNight = config.getDouble("chances.multipliers.night", chanceMultiplierNight);
            chanceMultiplierNether = config.getDouble("chances.multipliers.nether", chanceMultiplierNether);
            chanceMultiplierEnd = config.getDouble("chances.multipliers.the-end", chanceMultiplierEnd);
            maxCalculatedChance = config.getDouble("chances.max-calculated-chance", maxCalculatedChance);

            maxCalculatedChance = Math.max(0.0, Math.min(1.0, maxCalculatedChance));

            baseEventProbability = Math.max(0.0, Math.min(1.0, baseEventProbability));


            defaultEventCooldownMs = config.getLong("cooldowns.default-event-ms", defaultEventCooldownMs);
            defaultGlobalCooldownMs = config.getLong("cooldowns.default-global-ms", defaultGlobalCooldownMs);

            defaultEventCooldownMs = Math.max(0L, defaultEventCooldownMs);
            defaultGlobalCooldownMs = Math.max(0L, defaultGlobalCooldownMs);


            logger.debug("Configuration loaded successfully.");
        } catch (Exception e) {
            logger.error("Failed to load configuration! Using default settings.", e);

        }
    }

    /**
     * Reloads the configuration from the plugin's config.yml file.
     */
    public void reloadConfig() {
        plugin.reloadConfig();
        loadConfig();
        logger.debug("Configuration reloaded.");

        plugin.getEventScheduler().reloadSettings();
        plugin.getCooldownManager().reloadSettings();
        plugin.getEventService().reloadSettings();

    }


    public long getSchedulerIntervalTicks() {
        return schedulerIntervalTicks;
    }

    public double getBaseEventProbability() {
        return baseEventProbability;
    }

    public double getChanceMultiplierDarkness() {
        return chanceMultiplierDarkness;
    }

    public double getChanceMultiplierDimness() {
        return chanceMultiplierDimness;
    }

    public double getChanceMultiplierUndergroundDeep() {
        return chanceMultiplierUndergroundDeep;
    }

    public double getChanceMultiplierUndergroundShallow() {
        return chanceMultiplierUndergroundShallow;
    }

    public double getChanceMultiplierIsolated() {
        return chanceMultiplierIsolated;
    }

    public double getChanceMultiplierNight() {
        return chanceMultiplierNight;
    }

    public double getChanceMultiplierNether() {
        return chanceMultiplierNether;
    }

    public double getChanceMultiplierEnd() {
        return chanceMultiplierEnd;
    }

    public double getMaxCalculatedChance() {
        return maxCalculatedChance;
    }

    public long getDefaultEventCooldownMs() {
        return defaultEventCooldownMs;
    }

    public long getDefaultGlobalCooldownMs() {
        return defaultGlobalCooldownMs;
    }


    /**
     * Gets the configured cooldown for a specific event key, falling back to default.
     *
     * @param eventKey The key of the event.
     * @return The configured cooldown in milliseconds or the default event cooldown.
     */
    public long getEventCooldownMs(String eventKey) {

        return config.getLong("events." + eventKey.toLowerCase() + ".cooldown-ms", defaultEventCooldownMs);
    }

    /**
     * Gets a list of strings from the configuration for a specific event setting.
     *
     * @param eventKey    The key of the event.
     * @param settingName The name of the list setting (e.g., "messages").
     * @param defaultList A default list to return if the setting is not found or not a list.
     * @return The configured list of strings or the default list.
     * UNUSED FOR NOW.
     */
    public List<String> getEventStringList(String eventKey, String settingName, List<String> defaultList) {
        return config.getStringList("events." + eventKey.toLowerCase() + "." + settingName);

    }


    /**
     * Provides access to the underlying FileConfiguration object for direct reading by event implementations.
     * Use with caution; prefer adding specific getter methods to ConfigurationManager when possible.
     *
     * @return The FileConfiguration object.
     */
    public FileConfiguration getConfig() {
        return config;
    }
}