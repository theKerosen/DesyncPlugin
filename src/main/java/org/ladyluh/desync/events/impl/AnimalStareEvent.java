package org.ladyluh.desync.events.impl;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.events.PacketContainer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;
import org.ladyluh.desync.Desync;
import org.ladyluh.desync.events.PlayerDesyncEvent;
import org.slf4j.Logger;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Makes nearby passive animals stare intently at the player.
 * Stops all staring if the player looks back at one of them.
 */
public class AnimalStareEvent implements PlayerDesyncEvent {

    public static final Map<UUID, List<StareData>> activeStaresByPlayer = new ConcurrentHashMap<>();
    private static final String KEY = "animal_stare";
    private static final String DESCRIPTION = "Nearby animals stare intently at you.";
    private static final long DEFAULT_COOLDOWN_MS = 45 * 1000L;
    private static final double SEARCH_RADIUS = 64.0;
    private static final List<EntityType> VALID_ANIMAL_TYPES_UNIQUE = List.of(
            EntityType.COW, EntityType.SHEEP, EntityType.PIG, EntityType.CHICKEN, EntityType.RABBIT,
            EntityType.GOAT, EntityType.FOX, EntityType.WOLF, EntityType.HORSE, EntityType.DONKEY,
            EntityType.MULE, EntityType.LLAMA
    );

    /**
     * Static helper to check if a specific mob is currently involved in an active stare task.
     * Called by canTrigger() and other event types' canTrigger().
     *
     * @param mobUuid The UUID of the mob to check.
     * @return True if the mob is currently staring, false otherwise.
     */
    public static boolean isMobStaring(@NotNull UUID mobUuid) {

        return activeStaresByPlayer.values().stream()
                .flatMap(Collection::stream)
                .anyMatch(stare -> stare.mobUuid().equals(mobUuid));
    }

    /**
     * Central cleanup for all stares targeting a specific player.
     * This static helper is called by the PlayerQuitListener or gaze aversion logic.
     * It removes the player's entry from the map and cancels all their stare tasks.
     */
    public static void cancelAllPlayerStares(@NotNull UUID playerUUID, @NotNull Logger logger) {
        logger.debug("Attempting stare cancellation for player {}", playerUUID);

        List<StareData> tasksToCancel = activeStaresByPlayer.remove(playerUUID);

        if (tasksToCancel != null && !tasksToCancel.isEmpty()) {
            logger.info("Globally cancelling {} animal stare tasks for player {}", tasksToCancel.size(), playerUUID);
            for (StareData data : tasksToCancel) {


                if (data.task() != null && !data.task().isCancelled()) {
                    data.task().cancel();

                    Mob mobForLog = (Mob) Bukkit.getEntity(data.mobUuid());
                    logger.debug("Cancelled task for mob {} ({}) stare during global cancel.", data.mobUuid(), (mobForLog != null ? mobForLog.getType() : "Unknown"));
                } else {
                    Mob mobForLog = (Mob) Bukkit.getEntity(data.mobUuid());
                    logger.debug("Task for mob {} ({}) was already cancelled/null during global cancel.", data.mobUuid(), (mobForLog != null ? mobForLog.getType() : "Unknown"));
                }


                Mob mob = (Mob) Bukkit.getEntity(data.mobUuid());
                if (mob != null && mob.isValid() && !mob.isDead() && (data.task() == null || data.task().isCancelled())) {
                    logger.debug("Force-restoring AI for mob {} ({}).", data.mobUuid(), mob.getType());
                    mob.setAI(data.originalAiState());
                }
            }
        } else {
            logger.debug("No active stare tasks found for player {} during global cancel.", playerUUID);
        }
    }

    @Override
    public String getKey() {
        return KEY;
    }

    @Override
    public String getDescription() {
        return DESCRIPTION;
    }

    @Override
    public long getDefaultCooldownMs() {
        return DEFAULT_COOLDOWN_MS;
    }

    @Override
    public boolean canTrigger(@NotNull Player player) {
        Logger logger = Desync.getInstance().getPluginLogger();
        boolean foundCandidate = false;

        try {
            for (Entity entity : player.getNearbyEntities(SEARCH_RADIUS, SEARCH_RADIUS, SEARCH_RADIUS)) {
                if (VALID_ANIMAL_TYPES_UNIQUE.contains(entity.getType()) && entity instanceof Mob mob && mob.isValid() && !mob.isDead()) {
                    foundCandidate = true;
                    UUID mobUuid = mob.getUniqueId();


                    if (isMobStaring(mobUuid)) {

                        continue;
                    }

                    if (AnimalFollowEvent.isMobFollowing(mobUuid)) {

                        continue;
                    }


                    logger.debug("AnimalStare canTrigger for {}: Found available candidate mob {} ({})", player.getName(), mob.getType(), mobUuid);
                    return true;
                }
            }
        } catch (Exception e) {
            logger.error("Error during AnimalStare canTrigger check for {}", player.getName(), e);
            return false;
        }


        if (foundCandidate) {

            logger.debug("AnimalStare canTrigger for {}: Candidates found within range, but all are currently staring or following.", player.getName());
        } else {

            logger.debug("AnimalStare canTrigger for {}: No eligible animal candidates found within range ({}).", player.getName(), SEARCH_RADIUS);
        }

        return false;
    }

    /**
     * Triggers the animal stare event for the given player.
     * Finds nearby eligible animals and makes them stare.
     *
     * @param player The player to be stared at.
     * @param plugin The main plugin instance.
     */
    @Override
    public void trigger(@NotNull Player player, @NotNull Desync plugin) {
        Logger logger = plugin.getPluginLogger();


        List<Mob> nearbyAnimals = player.getNearbyEntities(SEARCH_RADIUS, SEARCH_RADIUS, SEARCH_RADIUS)
                .stream()
                .filter(entity -> VALID_ANIMAL_TYPES_UNIQUE.contains(entity.getType()) && entity instanceof Mob)
                .map(entity -> (Mob) entity)
                .filter(Mob::isValid)
                .filter(mob -> !mob.isDead())

                .filter(mob -> {
                    UUID mobUuid = mob.getUniqueId();
                    boolean isFollowing = AnimalFollowEvent.isMobFollowing(mobUuid);
                    boolean isStaring = isMobStaring(mobUuid);
                    if (isFollowing) {
                        logger.debug("AnimalStare trigger filter for {}: Skipping mob {} ({}): already following.", player.getName(), mob.getType(), mobUuid);
                    }
                    if (isStaring) {
                        logger.debug("AnimalStare trigger filter for {}: Skipping mob {} ({}): already staring.", player.getName(), mob.getType(), mobUuid);
                    }
                    return !isFollowing && !isStaring;
                })
                .toList();


        if (nearbyAnimals.isEmpty()) {
            logger.debug("AnimalStare requested for {}, but no eligible non-staring, non-following animals found during trigger (after filter).", player.getName());
            return;
        }


        logger.info("Triggering AnimalStare for {} animals near {}", nearbyAnimals.size(), player.getName());


        List<StareData> playerStares = activeStaresByPlayer.computeIfAbsent(player.getUniqueId(), k -> Collections.synchronizedList(new ArrayList<>()));


        for (Mob targetMob : nearbyAnimals) {
            UUID mobUuid = targetMob.getUniqueId();
            boolean originalAiState = targetMob.hasAI();
            targetMob.setAI(false);

            logger.debug("Starting stare task for {} ({}) -> {}", targetMob.getType(), mobUuid, player.getName());

            long stareDurationMinTicks = 40;
            long stareDurationMaxTicks = 100;
            long stareDurationTicks = ThreadLocalRandom.current().nextLong(stareDurationMinTicks, stareDurationMaxTicks + 1);


            BukkitTask task = startIndividualStareTask(plugin, targetMob, player, originalAiState, stareDurationTicks);

            if (task != null) {
                StareData stareData = new StareData(mobUuid, player.getUniqueId(), task, originalAiState);
                playerStares.add(stareData);
                logger.debug("Added stare task for {} ({}) to active stares for player {}", targetMob.getType(), mobUuid, player.getName());
            } else {
                logger.warn("Failed to start stare task for {}. Resetting AI.", mobUuid);

                if (targetMob.isValid()) targetMob.setAI(originalAiState);
            }
        }
    }


    /**
     * Helper for starting individual stare task & returning it.
     */
    private BukkitTask startIndividualStareTask(@NotNull Desync plugin, @NotNull Mob targetMob, @NotNull Player player, boolean originalAiState, long stareDurationTicks) {
        Logger logger = plugin.getPluginLogger();
        ProtocolManager protocolManager = plugin.getProtocolManager();
        UUID mobUuid = targetMob.getUniqueId();
        UUID playerUUID = player.getUniqueId();

        if (!player.isOnline() || !targetMob.isValid() || targetMob.isDead()) {
            logger.warn("Invalid target ({}) before stare task start for mob {}.", "Mob Invalid", mobUuid);
            if (targetMob.isValid()) targetMob.setAI(originalAiState);
            return null;
        }

        final int GAZE_CHECK_RANGE_BLOCKS = 36;
        final int GAZE_CHECK_RANGE_SQ = GAZE_CHECK_RANGE_BLOCKS * GAZE_CHECK_RANGE_BLOCKS;

        return new BukkitRunnable() {
            private long ticksElapsed = 0;

            @Override
            public void run() {
                Player currentPlayer = Bukkit.getPlayer(playerUUID);
                Mob currentMob = (Mob) Bukkit.getEntity(mobUuid);


                if (this.isCancelled() || currentPlayer == null || !currentPlayer.isOnline() || currentMob == null || !currentMob.isValid() || currentMob.isDead() || ticksElapsed >= stareDurationTicks) {
                    String reason = (this.isCancelled() ? "Cancelled" : (currentPlayer == null || !currentPlayer.isOnline() ? "Target Offline" : (currentMob == null || !currentMob.isValid() || currentMob.isDead() ? "Mob Invalid/Dead" : "TimerExpired")));
                    logger.debug("AnimalStare (ID {}) despawning: {}", mobUuid, reason);
                    cleanupStare(playerUUID, mobUuid, logger, originalAiState, this);
                    return;
                }


                if (currentPlayer.getWorld().equals(currentMob.getWorld()) &&
                        currentPlayer.getLocation().distanceSquared(currentMob.getLocation()) <= GAZE_CHECK_RANGE_SQ) {
                    try {

                        Entity targetedEntity = currentPlayer.getTargetEntity(GAZE_CHECK_RANGE_BLOCKS, false);


                        if (currentMob.equals(targetedEntity)) {
                            logger.debug("Gaze Aversion Detected: {} looked back at {} ({})", currentPlayer.getName(), currentMob.getType(), mobUuid);

                            cancelAllPlayerStares(playerUUID, logger);

                            return;
                        }
                    } catch (IllegalStateException e) {


                        logger.debug("Illegal state during getTargetEntity for {}: {}", currentPlayer.getName(), e.getMessage());
                    }
                }


                if (!this.isCancelled() && currentMob.isValid()) {
                    try {
                        Location animalEyeLoc = currentMob.getEyeLocation();

                        Location playerTargetPos = currentPlayer.getLocation().add(0, currentPlayer.getHeight() * 0.8, 0);
                        Vector direction = playerTargetPos.toVector().subtract(animalEyeLoc.toVector());


                        float yaw = (float) (Math.toDegrees(Math.atan2(direction.getZ(), direction.getX())) - 90);
                        float pitch = (float) Math.toDegrees(-Math.atan2(direction.getY(), Math.sqrt(direction.getX() * direction.getX() + direction.getZ() * direction.getZ())));


                        PacketContainer entityLookPacket = protocolManager.createPacket(PacketType.Play.Server.ENTITY_LOOK);
                        entityLookPacket.getIntegers().write(0, currentMob.getEntityId());
                        entityLookPacket.getBytes().write(0, (byte) (yaw * 256.0F / 360.0F));
                        entityLookPacket.getBytes().write(1, (byte) (pitch * 256.0F / 360.0F));
                        entityLookPacket.getBooleans().write(0, currentMob.isOnGround());
                        protocolManager.sendServerPacket(currentPlayer, entityLookPacket);


                        PacketContainer headRotationPacket = protocolManager.createPacket(PacketType.Play.Server.ENTITY_HEAD_ROTATION);
                        headRotationPacket.getIntegers().write(0, currentMob.getEntityId());
                        headRotationPacket.getBytes().write(0, (byte) (yaw * 256.0F / 360.0F));
                        protocolManager.sendServerPacket(currentPlayer, headRotationPacket);

                        ticksElapsed++;
                    } catch (Exception e) {
                        logger.error("Failed to send stare rotation packets for mob {} (UUID: {}) to player {}",
                                currentMob.getType(), mobUuid, currentPlayer.getName(), e);


                    }
                }
            }


            /** Helper method to clean up this specific stare task state. */
            private void cleanupStare(@NotNull UUID playerUuid, @NotNull UUID mobUuid, @NotNull Logger logger, boolean originalAiState, @NotNull BukkitRunnable taskToCancel) {
                if (!taskToCancel.isCancelled()) {
                    taskToCancel.cancel();
                }


                List<StareData> playerStares = activeStaresByPlayer.get(playerUuid);
                if (playerStares != null) {

                    boolean removed = playerStares.removeIf(data -> data.mobUuid().equals(mobUuid));
                    if (removed && playerStares.isEmpty()) {
                        activeStaresByPlayer.remove(playerUuid);
                        logger.debug("Removed last stare for player {}. Removing player entry from map.", playerUuid);
                    }
                    logger.debug("Cleaned up stare task for mob {} ({}) for player {} (Reason: {}). Remaining stares for player: {}",
                            (Bukkit.getEntity(mobUuid) != null ? Objects.requireNonNull(Bukkit.getEntity(mobUuid)).getType() : "Unknown"), mobUuid, playerUuid, (taskToCancel.isCancelled() ? "Cancelled" : "Ended"), playerStares.size());
                } else {
                    logger.debug("Stare task cleanup called for mob {} ({}) for player {}, but list was already missing. Reason: {}.",
                            (Bukkit.getEntity(mobUuid) != null ? Objects.requireNonNull(Bukkit.getEntity(mobUuid)).getType() : "Unknown"), mobUuid, playerUuid, (taskToCancel.isCancelled() ? "Cancelled" : "Ended"));
                }


                Mob mob = (Mob) Bukkit.getEntity(mobUuid);
                if (mob != null && mob.isValid() && !mob.isDead()) {
                    logger.debug("Force-restoring AI for {} ({}) after stare ended.", mob.getType(), mobUuid);
                    mob.setAI(originalAiState);

                } else {
                    logger.debug("Skipping AI restore for mob {} ({}) - mob invalid or dead.", (mob != null ? Objects.requireNonNull(Bukkit.getEntity(mobUuid)).getType() : "Unknown"), mobUuid);
                }
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    public record StareData(UUID mobUuid, UUID targetPlayerUuid, BukkitTask task, boolean originalAiState) {
    }
}