package org.ladyluh.desync.scheduling;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import org.ladyluh.desync.Desync;
import org.ladyluh.desync.events.EventService;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import org.ladyluh.desync.managers.CooldownManager;


public class EventScheduler implements Runnable {

    private final Desync plugin;
    private final EventService eventService;
    private final CooldownManager cooldownManager;
    private BukkitTask task;


    private static final long TASK_INTERVAL_TICKS = 20L * 5;
    private static final double BASE_EVENT_PROBABILITY = 0.02;


    public EventScheduler(Desync plugin, EventService eventService) {
        this.plugin = plugin;
        this.eventService = eventService;
        this.cooldownManager = plugin.getCooldownManager();
    }

    /**
     * Starts the main event scheduling task.
     */
    public void start() {
        if (task != null && !task.isCancelled()) {
            plugin.getPluginLogger().warn("EventScheduler task already running!");
            return;
        }

        task = Bukkit.getScheduler().runTaskTimer(plugin, this, TASK_INTERVAL_TICKS, TASK_INTERVAL_TICKS);
        plugin.getPluginLogger().info("EventScheduler started (running every {} ticks).", TASK_INTERVAL_TICKS);
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

        if (cooldownManager == null) {
            plugin.getPluginLogger().error("CooldownManager is null in EventScheduler run()!");
            return;
        }

        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!isPlayerEligible(player)) {
                plugin.getPluginLogger().debug("Skipping {} - not eligible.", player.getName());
                continue;
            }

            if (cooldownManager.isOnGlobalCooldown(player)) {
                plugin.getPluginLogger().debug("Skipping {} due to global cooldown.", player.getName());
                continue;
            }

            double chance = calculateEventChance(player);
            plugin.getPluginLogger().debug("Calculated chance for {}: {}", player.getName(), chance);

            if (ThreadLocalRandom.current().nextDouble() < chance) {
                plugin.getPluginLogger().debug("Chance roll successful for {}.", player.getName());
                triggerRandomEvent(player);
            } else {
                plugin.getPluginLogger().debug("Chance roll failed for {}.", player.getName());
            }
        }
    }

    /**
     * Checks basic eligibility for a player to receive horror events.
     *
     * @param player The player to check.
     * @return True if the player is eligible, false otherwise.
     */
    private boolean isPlayerEligible(Player player) {

        if (player.getGameMode() != org.bukkit.GameMode.SURVIVAL && player.getGameMode() != org.bukkit.GameMode.ADVENTURE) {
            return false;
        }
        return !player.isDead();
    }

    /**
     * Calculates the dynamic chance for an event based on environmental factors.
     *
     * @param player The player.
     * @return The calculated probability (0.0 to 1.0).
     */
    private double calculateEventChance(Player player) {
        double currentChance = BASE_EVENT_PROBABILITY;
        Location loc = player.getLocation();
        World world = player.getWorld();

        try {

            int lightLevel = loc.getBlock().getLightLevel();
            if (lightLevel < 5) {
                currentChance *= 2.0;

            } else if (lightLevel < 8) {
                currentChance *= 1.4;

            }
        } catch (Exception e) {
            plugin.getPluginLogger().error("Error checking light level for {}", player.getName(), e);

        }


        double yLevel = loc.getY();
        double seaLevel = world.getEnvironment() == World.Environment.NORMAL ? world.getSeaLevel() : 63;
        if (yLevel < seaLevel - 10) {
            currentChance *= 1.5;

        } else if (yLevel < seaLevel - 5) {
            currentChance *= 1.2;

        }


        double isolationRadius = 64.0;
        if (isPlayerIsolated(player, isolationRadius)) {
            currentChance *= 1.75;

        }


        long time = world.getTime();
        boolean isNight = time > 13000 && time < 23000;
        if (isNight) {
            currentChance *= 1.6;

        }


        if (world.getEnvironment() == World.Environment.NETHER) {
            currentChance *= 1.3;

        } else if (world.getEnvironment() == World.Environment.THE_END) {
            currentChance *= 1.5;

        }


        double maxChance = 0.35;
        currentChance = Math.min(currentChance, maxChance);


        if (currentChance > BASE_EVENT_PROBABILITY) {
            plugin.getPluginLogger().debug("Calculated chance for {}: {} (Light: {}, Y: {}, Isolated: {}, Time: {}, Dim: {})",
                    player.getName(),
                    currentChance,
                    loc.getBlock().getLightLevel(),
                    (int) yLevel,
                    isPlayerIsolated(player, isolationRadius),
                    time,
                    world.getEnvironment().name());
        }


        return currentChance;
    }

    /**
     * Checks if the player is isolated (no other players within radius).
     */
    private boolean isPlayerIsolated(Player player, double radius) {

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
    private void triggerRandomEvent(Player player) {


        List<String> eligibleEventKeys = eventService.getCurrentlyTriggerableEventKeys(player);

        if (eligibleEventKeys.isEmpty()) {
            plugin.getPluginLogger().debug("No eligible event types found for {} after canTrigger checks.", player.getName());
            return;
        }


        ThreadLocalRandom random = ThreadLocalRandom.current();
        List<String> shuffledKeys = new java.util.ArrayList<>(eligibleEventKeys);
        java.util.Collections.shuffle(shuffledKeys, random);

        boolean eventTriggered = false;
        for (String eventKey : shuffledKeys) {

            if (eventService.triggerEvent(player, eventKey, false)) {
                eventTriggered = true;

                break;
            } else {
                plugin.getPluginLogger().debug("Event '{}' skipped for {} by EventService (cooldown/canTrigger handled internally).", eventKey, player.getName());
            }
        }

        if (!eventTriggered) {
            plugin.getPluginLogger().debug("No event was triggered for {} after checking {} eligible types (all on cooldown?).", player.getName(), eligibleEventKeys.size());
        }

    }


}