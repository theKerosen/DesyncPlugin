package org.ladyluh.desync.scheduling;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.NotNull;
import org.ladyluh.desync.Desync;
import org.ladyluh.desync.events.EventService;
import org.ladyluh.desync.managers.ConfigurationManager;
import org.ladyluh.desync.managers.CooldownManager;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;


public class EventScheduler implements Runnable {

    private final Desync plugin;
    private final EventService eventService;
    private final CooldownManager cooldownManager;
    private final ConfigurationManager configManager;
    private BukkitTask task;


    private long schedulerIntervalTicks;
    private double baseEventProbability;
    private double chanceMultiplierDarkness;
    private double chanceMultiplierDimness;
    private double chanceMultiplierUndergroundDeep;
    private double chanceMultiplierUndergroundShallow;
    private double chanceMultiplierIsolated;
    private double chanceMultiplierNight;
    private double chanceMultiplierNether;
    private double chanceMultiplierEnd;
    private double maxCalculatedChance;


    public EventScheduler(@NotNull Desync plugin, @NotNull EventService eventService, @NotNull ConfigurationManager configManager) {
        this.plugin = plugin;
        this.eventService = eventService;
        this.cooldownManager = plugin.getCooldownManager();
        this.configManager = configManager;

    }

    /**
     * Called by ConfigurationManager after config is loaded or reloaded.
     */
    public void reloadSettings() {


        this.schedulerIntervalTicks = configManager.getSchedulerIntervalTicks();
        this.baseEventProbability = configManager.getBaseEventProbability();
        this.chanceMultiplierDarkness = configManager.getChanceMultiplierDarkness();
        this.chanceMultiplierDimness = configManager.getChanceMultiplierDimness();
        this.chanceMultiplierUndergroundDeep = configManager.getChanceMultiplierUndergroundDeep();
        this.chanceMultiplierUndergroundShallow = configManager.getChanceMultiplierUndergroundShallow();
        this.chanceMultiplierIsolated = configManager.getChanceMultiplierIsolated();
        this.chanceMultiplierNight = configManager.getChanceMultiplierNight();
        this.chanceMultiplierNether = configManager.getChanceMultiplierNether();
        this.chanceMultiplierEnd = configManager.getChanceMultiplierEnd();
        this.maxCalculatedChance = configManager.getMaxCalculatedChance();

        plugin.getPluginLogger().debug("EventScheduler settings reloaded. Interval: {}t, BaseChance: {}", schedulerIntervalTicks, baseEventProbability);


        if (task != null && !task.isCancelled()) {
            plugin.getPluginLogger().info("Scheduler interval changed, restarting task.");
            task.cancel();
            start();
        }

    }


    /**
     * Starts the main event scheduling task.
     * Uses the currently configured interval.
     */
    public void start() {
        if (task != null && !task.isCancelled()) {
            plugin.getPluginLogger().warn("EventScheduler task already running!");
            return;
        }


        task = Bukkit.getScheduler().runTaskTimer(plugin, this, schedulerIntervalTicks, schedulerIntervalTicks);
        plugin.getPluginLogger().info("EventScheduler started (running every {} ticks).", schedulerIntervalTicks);
    }

    /**
     * Stops the main event scheduling task.
     */
    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
            plugin.getPluginLogger().info("EventScheduler stopped.");
        }
    }

    /**
     * The main loop executed by the Bukkit scheduler.
     */
    @Override
    public void run() {

        if (cooldownManager == null || eventService == null || configManager == null) {
            plugin.getPluginLogger().error("Manager or Service is null in EventScheduler run()!");
            stop();
            return;
        }

        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!isPlayerEligible(player)) {

                continue;
            }

            if (cooldownManager.isOnGlobalCooldown(player)) {

                continue;
            }

            double chance = calculateEventChance(player);


            if (ThreadLocalRandom.current().nextDouble() < chance) {
                plugin.getPluginLogger().debug("Chance roll successful for {}. Attempting to trigger event.", player.getName());
                triggerRandomEvent(player);
            }
        }
    }

    /**
     * Checks basic eligibility for a player to receive horror events.
     *
     * @param player The player to check.
     * @return True if the player is eligible, false otherwise.
     */
    private boolean isPlayerEligible(@NotNull Player player) {

        if (player.getGameMode() != org.bukkit.GameMode.SURVIVAL && player.getGameMode() != org.bukkit.GameMode.ADVENTURE) {
            return false;
        }
        return !player.isDead();
    }

    /**
     * Calculates the dynamic chance for an event based on environmental factors,
     * using values from the ConfigurationManager.
     *
     * @param player The player.
     * @return The calculated probability (0.0 to 1.0).
     */
    private double calculateEventChance(@NotNull Player player) {
        double currentChance = baseEventProbability;
        Location loc = player.getLocation();
        World world = player.getWorld();

        try {

            int lightLevel = loc.getBlock().getLightLevel();
            if (lightLevel < 5) {
                currentChance *= chanceMultiplierDarkness;
            } else if (lightLevel < 8) {
                currentChance *= chanceMultiplierDimness;
            }
        } catch (Exception e) {
            plugin.getPluginLogger().error("Error checking light level for {}", player.getName(), e);

        }


        double yLevel = loc.getY();
        double seaLevel = world.getEnvironment() == World.Environment.NORMAL ? world.getSeaLevel() : 63;
        if (yLevel < seaLevel - 10) {
            currentChance *= chanceMultiplierUndergroundDeep;
        } else if (yLevel < seaLevel - 5) {
            currentChance *= chanceMultiplierUndergroundShallow;
        }


        double isolationRadius = 64.0;
        if (isPlayerIsolated(player, isolationRadius)) {
            currentChance *= chanceMultiplierIsolated;
        }


        long time = world.getTime();
        boolean isNight = time > 13000 && time < 23000;
        if (isNight) {
            currentChance *= chanceMultiplierNight;
        }


        if (world.getEnvironment() == World.Environment.NETHER) {
            currentChance *= chanceMultiplierNether;
        } else if (world.getEnvironment() == World.Environment.THE_END) {
            currentChance *= chanceMultiplierEnd;
        }


        currentChance = Math.min(currentChance, maxCalculatedChance);


        if (currentChance > baseEventProbability * 1.1) {
            plugin.getPluginLogger().debug("Calculated chance for {}: {} (Factors: Light {}, Y {}, Isolated {}, Time {})",
                    player.getName(),
                    currentChance,
                    loc.getBlock().getLightLevel(),
                    (int) yLevel,
                    isPlayerIsolated(player, isolationRadius),
                    time);
        }


        return currentChance;
    }

    /**
     * Checks if the player is isolated (no other players within radius).
     */
    private boolean isPlayerIsolated(@NotNull Player player, double radius) {

        if (Bukkit.getOnlinePlayers().size() <= 1) {
            return true;
        }

        double radiusSquared = radius * radius;
        World world = player.getWorld();


        for (Player otherPlayer : Bukkit.getOnlinePlayers()) {

            if (otherPlayer.equals(player) || !otherPlayer.getWorld().equals(world)) {
                continue;
            }


            if (player.getLocation().distanceSquared(otherPlayer.getLocation()) <= radiusSquared) {
                return false;
            }
        }
        return true;
    }

    /**
     * Selects and triggers a random horror event for the player using the EventService.
     * The EventService handles cooldown and canTrigger checks before execution.
     *
     * @param player The player to trigger the event for.
     */
    private void triggerRandomEvent(@NotNull Player player) {


        List<String> eligibleEventKeys = eventService.getCurrentlyTriggerableEventKeys(player);

        if (eligibleEventKeys.isEmpty()) {
            plugin.getPluginLogger().debug("No eligible event types found for {} after canTrigger checks.", player.getName());
            return;
        }


        ThreadLocalRandom random = ThreadLocalRandom.current();
        List<String> shuffledKeys = new ArrayList<>(eligibleEventKeys);
        Collections.shuffle(shuffledKeys, random);

        for (String eventKey : shuffledKeys) {

            if (eventService.triggerEvent(player, eventKey, false)) {

                break;
            }

            plugin.getPluginLogger().debug("No event was triggered for {} after checking {} eligible types (all on cooldown?).", player.getName(), eligibleEventKeys.size());
        }
    }

}