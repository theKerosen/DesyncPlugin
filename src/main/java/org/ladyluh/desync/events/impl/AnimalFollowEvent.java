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
 * Makes nearby passive animals appear to follow the player when the player is not looking at them.
 * Stops the follow when the player looks towards them or after a duration.
 */
public class AnimalFollowEvent implements PlayerDesyncEvent {

    public static final Map<UUID, List<FollowData>> activeFollowsByPlayer = new ConcurrentHashMap<>();
    private static final String KEY = "animal_follow";
    private static final String DESCRIPTION = "Nearby animals seem to follow you when your back is turned.";
    private static final long DEFAULT_COOLDOWN_MS = 60 * 1000L;
    private static final double SEARCH_RADIUS = 32.0;
    private static final List<EntityType> VALID_ANIMAL_TYPES_UNIQUE = List.of(
            EntityType.COW, EntityType.SHEEP, EntityType.PIG, EntityType.CHICKEN, EntityType.RABBIT,
            EntityType.GOAT, EntityType.FOX, EntityType.WOLF, EntityType.HORSE, EntityType.DONKEY,
            EntityType.MULE, EntityType.LLAMA

    );
    private static final double GAZE_THRESHOLD_DEGREES = 70.0;
    private static final double FOLLOW_STEP_DISTANCE_MIN = 0.3;
    private static final double FOLLOW_STEP_DISTANCE_MAX = 0.5;
    private static final int FOLLOW_INTERVAL_TICKS = 5;
    private static final long MAX_FOLLOW_DURATION_TICKS = 20 * 20;

    /**
     * Static helper to check if a specific mob is currently involved in an active follow task.
     * Called by canTrigger() and other event types' canTrigger().
     *
     * @param mobUuid The UUID of the mob to check.
     * @return True if the mob is currently following, false otherwise.
     */
    public static boolean isMobFollowing(@NotNull UUID mobUuid) {

        return activeFollowsByPlayer.values().stream()
                .flatMap(Collection::stream)
                .anyMatch(follow -> follow.mobUuid().equals(mobUuid));
    }

    /**
     * Static helper to cancel all follow tasks targeting a specific player.
     * Called by PlayerQuitListener or EventService cleanup.
     */
    public static void cancelAllPlayerFollows(@NotNull UUID playerUUID, @NotNull Logger logger) {
        logger.debug("Attempting follow cancellation for player {}", playerUUID);

        List<FollowData> followsToCancel = activeFollowsByPlayer.remove(playerUUID);

        if (followsToCancel != null && !followsToCancel.isEmpty()) {
            logger.info("Globally cancelling {} animal follow tasks for player {}", followsToCancel.size(), playerUUID);
            for (FollowData data : followsToCancel) {


                if (data.task() != null && !data.task().isCancelled()) {
                    data.task().cancel();

                    Mob mobForLog = (Mob) Bukkit.getEntity(data.mobUuid());
                    logger.debug("Cancelled task for mob {} ({}) follow during global cancel.", data.mobUuid(), (mobForLog != null ? mobForLog.getType() : "Unknown"));
                } else {
                    Mob mobForLog = (Mob) Bukkit.getEntity(data.mobUuid());
                    logger.debug("Task for mob {} ({}) was already cancelled/null during global cancel.", data.mobUuid(), (mobForLog != null ? mobForLog.getType() : "Unknown"));
                }


                Mob mob = (Mob) Bukkit.getEntity(data.mobUuid());
                if (mob != null && mob.isValid() && !mob.isDead() && (data.task() == null || data.task().isCancelled())) {
                    logger.debug("Force-restoring AI for mob {} ({}).", data.mobUuid(), mob.getType());
                    mob.setAI(data.originalAiState());

                    try {
                        ProtocolManager protocolManager = Desync.getInstance().getProtocolManager();
                        PacketContainer teleportPacket = protocolManager.createPacket(PacketType.Play.Server.ENTITY_TELEPORT);
                        teleportPacket.getIntegers().write(0, mob.getEntityId());
                        teleportPacket.getDoubles()
                                .write(0, mob.getLocation().getX())
                                .write(1, mob.getLocation().getY())
                                .write(2, mob.getLocation().getZ());
                        teleportPacket.getBytes()
                                .write(0, (byte) (mob.getLocation().getYaw() * 256.0F / 360.0F))
                                .write(1, (byte) (mob.getLocation().getPitch() * 256.0F / 360.0F));
                        teleportPacket.getBooleans().write(0, mob.isOnGround());

                        Player targetPlayer = Bukkit.getPlayer(playerUUID);
                        if (targetPlayer != null && targetPlayer.isOnline()) {
                            protocolManager.sendServerPacket(targetPlayer, teleportPacket);
                            logger.debug("Sent final sync teleport for mob {} ({}) to player {}", mob.getType(), data.mobUuid(), targetPlayer.getName());
                        } else {

                            logger.debug("Skipping final sync teleport for mob {} ({}) - player {} offline.", mob.getType(), data.mobUuid(), playerUUID);
                        }

                    } catch (Exception e) {
                        logger.error("Failed to send final sync teleport packet for mob {} ({}) to player {}", mob.getType(), data.mobUuid(), playerUUID, e);
                    }
                }
            }
        } else {
            logger.debug("No active follow tasks found for player {} during cancelAllPlayerFollows.", playerUUID);
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


                    if (isMobFollowing(mobUuid)) {

                        continue;
                    }

                    if (AnimalStareEvent.isMobStaring(mobUuid)) {

                        continue;
                    }


                    logger.debug("AnimalFollow canTrigger for {}: Found available candidate mob {} ({})", player.getName(), mob.getType(), mobUuid);
                    return true;
                }
            }
        } catch (Exception e) {
            logger.error("Error during AnimalFollow canTrigger check for {}", player.getName(), e);
            return false;
        }


        if (foundCandidate) {

            logger.debug("AnimalFollow canTrigger for {}: Candidates found within range, but all are currently staring or following.", player.getName());
        } else {

            logger.debug("AnimalFollow canTrigger for {}: No eligible animal candidates found within range ({}).", player.getName(), SEARCH_RADIUS);
        }


        return false;
    }

    /**
     * Triggers the animal follow event for the given player.
     * Finds nearby eligible animals and makes them appear to follow when not looked at.
     *
     * @param player The player to be followed.
     * @param plugin The main plugin instance.
     */
    @Override
    public void trigger(@NotNull Player player, @NotNull Desync plugin) {
        Logger logger = plugin.getPluginLogger();
        ThreadLocalRandom random = ThreadLocalRandom.current();


        List<Mob> nearbyAnimals = player.getNearbyEntities(SEARCH_RADIUS, SEARCH_RADIUS, SEARCH_RADIUS)
                .stream()
                .filter(entity -> VALID_ANIMAL_TYPES_UNIQUE.contains(entity.getType()) && entity instanceof Mob)
                .map(entity -> (Mob) entity)
                .filter(Mob::isValid)
                .filter(mob -> !mob.isDead())

                .filter(mob -> {
                    UUID mobUuid = mob.getUniqueId();
                    boolean isFollowing = isMobFollowing(mobUuid);
                    boolean isStaring = AnimalStareEvent.isMobStaring(mobUuid);
                    if (isFollowing) {
                        logger.debug("AnimalFollow trigger filter for {}: Skipping mob {} ({}): already following.", player.getName(), mob.getType(), mobUuid);
                    }
                    if (isStaring) {
                        logger.debug("AnimalFollow trigger filter for {}: Skipping mob {} ({}): already staring.", player.getName(), mob.getType(), mobUuid);
                    }
                    return !isFollowing && !isStaring;
                })
                .toList();


        if (nearbyAnimals.isEmpty()) {
            logger.debug("AnimalFollow requested for {}, but no eligible non-following, non-staring animals found during trigger (after filter).", player.getName());
            return;
        }


        int maxFollowingAnimals = Math.min(nearbyAnimals.size(), random.nextInt(1, 3));
        List<Mob> selectedAnimals = new ArrayList<>(nearbyAnimals);
        Collections.shuffle(selectedAnimals, random);
        if (maxFollowingAnimals < selectedAnimals.size()) {
            selectedAnimals = selectedAnimals.subList(0, maxFollowingAnimals);
        }


        if (selectedAnimals.isEmpty()) {
            logger.debug("AnimalFollow requested for {}, but no animals were selected from {} candidates for following (after selection limit).", player.getName(), nearbyAnimals.size());
            return;
        }

        logger.info("Triggering AnimalFollow for {} animals near {}", selectedAnimals.size(), player.getName());


        List<FollowData> playerFollows = activeFollowsByPlayer.computeIfAbsent(player.getUniqueId(), k -> Collections.synchronizedList(new ArrayList<>()));


        for (Mob targetMob : selectedAnimals) {
            UUID mobUuid = targetMob.getUniqueId();
            boolean originalAiState = targetMob.hasAI();
            targetMob.setAI(false);
            Location initialServerLocation = targetMob.getLocation().clone();

            logger.debug("Starting follow task for {} ({}) -> {}", targetMob.getType(), mobUuid, player.getName());


            BukkitTask task = startIndividualFollowTask(plugin, targetMob, player, originalAiState, initialServerLocation);

            if (task != null) {
                FollowData followData = new FollowData(mobUuid, player.getUniqueId(), task, originalAiState, initialServerLocation);
                playerFollows.add(followData);
                logger.debug("Added follow task for {} ({}) to active follows for player {}", targetMob.getType(), mobUuid, player.getName());
            } else {
                logger.warn("Failed to start follow task for {}. Resetting AI.", mobUuid);

                if (targetMob.isValid()) targetMob.setAI(originalAiState);
            }
        }
    }

    /**
     * Helper for starting individual follow task & returning it.
     */
    private BukkitTask startIndividualFollowTask(@NotNull Desync plugin, @NotNull Mob targetMob, @NotNull Player player, boolean originalAiState, @NotNull Location initialServerLocation) {
        Logger logger = plugin.getPluginLogger();
        ProtocolManager protocolManager = plugin.getProtocolManager();
        UUID mobUuid = targetMob.getUniqueId();
        UUID playerUUID = player.getUniqueId();

        if (!player.isOnline() || !targetMob.isValid() || targetMob.isDead()) {
            logger.warn("Invalid target ({}) before follow task start for mob {}.", "Mob Invalid", mobUuid);
            if (targetMob.isValid()) targetMob.setAI(originalAiState);
            return null;
        }

        final double GAZE_CHECK_THRESHOLD_COS = Math.cos(Math.toRadians(GAZE_THRESHOLD_DEGREES));
        ThreadLocalRandom random = ThreadLocalRandom.current();


        return new BukkitRunnable() {
            private long ticksElapsed = 0;


            private Location clientsideLocation = initialServerLocation.clone();

            @Override
            public void run() {
                Player currentPlayer = Bukkit.getPlayer(playerUUID);
                Mob currentMob = (Mob) Bukkit.getEntity(mobUuid);


                if (this.isCancelled() || currentPlayer == null || !currentPlayer.isOnline() || currentMob == null || !currentMob.isValid() || currentMob.isDead() || ticksElapsed >= MAX_FOLLOW_DURATION_TICKS) {
                    String reason = (this.isCancelled() ? "Cancelled" : (currentPlayer == null || !currentPlayer.isOnline() ? "Target Offline" : (currentMob == null || !currentMob.isValid() || currentMob.isDead() ? "Mob Invalid/Dead" : "Max Lifespan")));
                    logger.debug("AnimalFollow (ID {}) despawning: {}", mobUuid, reason);

                    cleanupFollow(playerUUID, mobUuid, logger, originalAiState, this);
                    return;
                }


                boolean isLookingAway;
                if (currentPlayer.getWorld().equals(currentMob.getWorld())) {
                    try {
                        Vector playerLookDir = currentPlayer.getEyeLocation().getDirection().normalize();

                        Vector dirToMob = currentMob.getLocation().add(0, 1.0, 0).toVector()
                                .subtract(currentPlayer.getEyeLocation().toVector()).normalize();

                        if (dirToMob.lengthSquared() > 0.01) {
                            double dot = playerLookDir.dot(dirToMob);


                            if (dot <= GAZE_CHECK_THRESHOLD_COS) {
                                isLookingAway = true;
                            } else {
                                logger.debug("Gaze Aversion Detected: {} looked at {} ({}) - Stopping follow.", currentPlayer.getName(), currentMob.getType(), mobUuid);

                                cleanupFollow(playerUUID, mobUuid, logger, originalAiState, this);
                                return;
                            }
                        } else {


                            isLookingAway = false;


                            if (clientsideLocation.distanceSquared(currentPlayer.getLocation()) < 2.0 * 2.0) {
                                logger.debug("Player {} too close to mob {} ({}) clientside - Stopping follow.", currentPlayer.getName(), currentMob.getType(), mobUuid);
                                cleanupFollow(playerUUID, mobUuid, logger, originalAiState, this);
                                return;
                            }
                        }
                    } catch (Exception e) {
                        logger.warn("Error during AnimalFollow gaze check for {} on {} (Mob: {}): {}", currentPlayer.getName(), currentMob.getType(), mobUuid, e.getMessage());

                        isLookingAway = false;
                    }
                } else {

                    isLookingAway = true;
                }


                if (isLookingAway && ticksElapsed % FOLLOW_INTERVAL_TICKS == 0) {
                    try {


                        Vector dirToPlayer = currentPlayer.getLocation().add(0, 0.5, 0).toVector()
                                .subtract(clientsideLocation.toVector());
                        double distanceToPlayer = dirToPlayer.length();
                        if (distanceToPlayer < FOLLOW_STEP_DISTANCE_MIN * 1.5) {

                            logger.debug("Mob {} ({}) clientside too close to player {} (dist: {}) - skipping follow step.", currentMob.getType(), mobUuid, currentPlayer.getName(), distanceToPlayer);

                        } else {
                            dirToPlayer.normalize();


                            double stepDistance = random.nextDouble() * (FOLLOW_STEP_DISTANCE_MAX - FOLLOW_STEP_DISTANCE_MIN) + FOLLOW_STEP_DISTANCE_MIN;

                            stepDistance = Math.min(stepDistance, distanceToPlayer * 0.8);


                            Location newClientsideLoc = clientsideLocation.clone().add(dirToPlayer.multiply(stepDistance));


                            PacketContainer teleportPacket = protocolManager.createPacket(PacketType.Play.Server.ENTITY_TELEPORT);
                            teleportPacket.getIntegers().write(0, currentMob.getEntityId());
                            teleportPacket.getDoubles()
                                    .write(0, newClientsideLoc.getX())
                                    .write(1, newClientsideLoc.getY())
                                    .write(2, newClientsideLoc.getZ());

                            Vector lookDirForMob = currentPlayer.getLocation().add(0, currentPlayer.getHeight() * 0.8, 0).toVector().subtract(newClientsideLoc.toVector());
                            float yaw = (float) (Math.toDegrees(Math.atan2(lookDirForMob.getZ(), lookDirForMob.getX())) - 90);
                            float pitch = (float) Math.toDegrees(-Math.atan2(lookDirForMob.getY(), Math.sqrt(lookDirForMob.getX() * lookDirForMob.getX() + lookDirForMob.getZ() * lookDirForMob.getZ())));

                            teleportPacket.getBytes()
                                    .write(0, (byte) (yaw * 256.0F / 360.0F))
                                    .write(1, (byte) (pitch * 256.0F / 360.0F));
                            teleportPacket.getBooleans().write(0, true);

                            protocolManager.sendServerPacket(currentPlayer, teleportPacket);
                            logger.debug("Mob {} ({}) stepped towards {} ({} blocks) at clientside pos {}",
                                    currentMob.getType(), mobUuid, currentPlayer.getName(), stepDistance, newClientsideLoc.toVector());


                            clientsideLocation = newClientsideLoc;
                        }


                        if (currentMob.isValid()) {
                            Location mobClientsideEyeLoc = clientsideLocation.clone().add(0, 1.0, 0);
                            Vector lookDirForMob = currentPlayer.getLocation().add(0, currentPlayer.getHeight() * 0.8, 0).toVector().subtract(mobClientsideEyeLoc.toVector());

                            float yaw = (float) (Math.toDegrees(Math.atan2(lookDirForMob.getZ(), lookDirForMob.getX())) - 90);
                            float pitch = (float) Math.toDegrees(-Math.atan2(lookDirForMob.getY(), Math.sqrt(lookDirForMob.getX() * lookDirForMob.getX() + lookDirForMob.getZ() * lookDirForMob.getZ())));


                            PacketContainer entityLookPacket = protocolManager.createPacket(PacketType.Play.Server.ENTITY_LOOK);
                            entityLookPacket.getIntegers().write(0, currentMob.getEntityId());
                            entityLookPacket.getBytes().write(0, (byte) (yaw * 256.0F / 360.0F)).write(1, (byte) (pitch * 256.0F / 360.0F));
                            entityLookPacket.getBooleans().write(0, true);
                            protocolManager.sendServerPacket(currentPlayer, entityLookPacket);

                        }


                    } catch (Exception e) {
                        logger.error("Failed during AnimalFollow task for mob {} (UUID: {}) following player {}",
                                currentMob.getType(), mobUuid, currentPlayer.getName(), e);

                        cleanupFollow(playerUUID, mobUuid, logger, originalAiState, this);
                        return;
                    }
                }


                ticksElapsed++;
            }


            /** Helper method to clean up this specific follow task state. */
            private void cleanupFollow(@NotNull UUID playerUuid, @NotNull UUID mobUuid, @NotNull Logger logger, boolean originalAiState, @NotNull BukkitRunnable taskToCancel) {
                if (!taskToCancel.isCancelled()) {
                    taskToCancel.cancel();
                }


                List<FollowData> playerFollows = activeFollowsByPlayer.get(playerUuid);
                if (playerFollows != null) {

                    boolean removed = playerFollows.removeIf(data -> data.mobUuid().equals(mobUuid));
                    if (removed && playerFollows.isEmpty()) {
                        activeFollowsByPlayer.remove(playerUuid);
                        logger.debug("Removed last follow for player {}. Removing player entry from map.", playerUuid);
                    }
                    logger.debug("Cleaned up follow task for mob {} ({}) for player {} (Reason: {}). Remaining follows for player: {}",
                            (Bukkit.getEntity(mobUuid) != null ? Objects.requireNonNull(Bukkit.getEntity(mobUuid)).getType() : "Unknown"), mobUuid, playerUuid, (taskToCancel.isCancelled() ? "Cancelled" : "Ended"), playerFollows.size());
                } else {
                    logger.debug("Follow task cleanup called for mob {} ({}) for player {}, but list was already missing. Reason: {}.",
                            (Bukkit.getEntity(mobUuid) != null ? Objects.requireNonNull(Bukkit.getEntity(mobUuid)).getType() : "Unknown"), mobUuid, playerUuid, (taskToCancel.isCancelled() ? "Cancelled" : "Ended"));
                }


                Mob mob = (Mob) Bukkit.getEntity(mobUuid);
                if (mob != null && mob.isValid() && !mob.isDead()) {
                    logger.debug("Force-restoring AI for {} ({}) after follow ended.", mob.getType(), mobUuid);
                    mob.setAI(originalAiState);

                    try {
                        ProtocolManager protocolManager = Desync.getInstance().getProtocolManager();
                        PacketContainer teleportPacket = protocolManager.createPacket(PacketType.Play.Server.ENTITY_TELEPORT);
                        teleportPacket.getIntegers().write(0, mob.getEntityId());
                        teleportPacket.getDoubles()
                                .write(0, mob.getLocation().getX())
                                .write(1, mob.getLocation().getY())
                                .write(2, mob.getLocation().getZ());
                        teleportPacket.getBytes()
                                .write(0, (byte) (mob.getLocation().getYaw() * 256.0F / 360.0F))
                                .write(1, (byte) (mob.getLocation().getPitch() * 256.0F / 360.0F));
                        teleportPacket.getBooleans().write(0, mob.isOnGround());

                        Player targetPlayer = Bukkit.getPlayer(playerUuid);
                        if (targetPlayer != null && targetPlayer.isOnline()) {
                            protocolManager.sendServerPacket(targetPlayer, teleportPacket);
                            logger.debug("Sent final sync teleport for mob {} ({}) to player {}", mob.getType(), mobUuid, targetPlayer.getName());
                        } else {

                            logger.debug("Skipping final sync teleport for mob {} ({}) - player {} offline.", mob.getType(), mobUuid, playerUuid);
                        }

                    } catch (Exception e) {
                        logger.error("Failed to send final sync teleport packet for mob {} ({}) to player {}", mob.getType(), mobUuid, playerUuid, e);
                    }


                } else {
                    logger.debug("Skipping AI restore/sync for mob {} ({}) - mob is invalid, null or dead.", (mob != null ? Objects.requireNonNull(Bukkit.getEntity(mobUuid)).getType() : "Unknown"), mobUuid);
                }
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    public record FollowData(UUID mobUuid, UUID targetPlayerUuid, BukkitTask task, boolean originalAiState,
                             Location initialServerLocation) {
    }
}