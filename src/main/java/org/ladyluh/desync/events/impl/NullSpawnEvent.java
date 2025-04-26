package org.ladyluh.desync.events.impl;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.wrappers.*;
import com.comphenix.protocol.wrappers.EnumWrappers.NativeGameMode;
import com.comphenix.protocol.wrappers.EnumWrappers.PlayerInfoAction;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;
import org.ladyluh.desync.Desync;
import org.ladyluh.desync.events.PlayerDesyncEvent;
import org.ladyluh.desync.utils.EffectUtils;
import org.ladyluh.desync.utils.SkinUtils;
import org.slf4j.Logger;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Spawns a temporary fake player entity ("Null") right behind the target player.
 * Plays footstep sounds to get the player's attention.
 * Attempts to despawn shortly after the player turns towards it or looks at it.
 */
public class NullSpawnEvent implements PlayerDesyncEvent {

    public static final Map<UUID, NullData> activeNulls = new ConcurrentHashMap<>();
    private static final String KEY = "null_spawn";
    private static final String DESCRIPTION = "A creepy figure briefly appears directly behind you.";
    private static final long DEFAULT_COOLDOWN_MS = 10 * 60 * 1000L;

    private static final double NULL_SPAWN_DISTANCE = 1.2;
    private static final double NULL_VIEW_ANGLE_THRESHOLD = 90.0;
    private static final long NULL_VIEW_DESPAWN_TICKS = 5;
    private static final long NULL_MAX_LIFESPAN_TICKS = 20 * 20;
    private static final double NULL_ISOLATION_RADIUS_OTHERS = 50.0;

    private static final int NULL_SOUND_INTERVAL_TICKS = 8;


    private static final AtomicInteger fakeEntityIdCounter = StalkerSpawnEvent.fakeEntityIdCounter;

    /**
     * Static helper to cancel a Null specifically by target player UUID.
     * Called by PlayerQuitListener or EventService cleanup.
     */
    public static void cancelNullForPlayer(@NotNull UUID playerUUID, @NotNull Logger logger) {
        logger.debug("Attempting Null cancellation for player {}", playerUUID);

        NullData removedData = activeNulls.remove(playerUUID);

        if (removedData != null) {
            logger.debug("Cancelling active Null ID {} for player {}", removedData.entityId(), playerUUID);

            if (removedData.task() != null && !removedData.task().isCancelled()) {
                removedData.task().cancel();
                logger.debug("Cancelled Bukkit task for Null ID {}.", removedData.entityId());
            } else {
                logger.debug("Null ID {} task was already cancelled or null during cleanup.", removedData.entityId());
            }

            Player targetPlayer = Bukkit.getPlayer(playerUUID);
            if (targetPlayer != null && targetPlayer.isOnline()) {
                try {
                    logger.debug("Sending despawn packets for Null ID {} to {}", removedData.entityId(), targetPlayer.getName());
                    ProtocolManager protocolManager = Desync.getInstance().getProtocolManager();

                    protocolManager.sendServerPacket(targetPlayer, removedData.destroyPacket());
                    protocolManager.sendServerPacket(targetPlayer, removedData.removeInfoPacket());
                } catch (Exception e) {
                    logger.error("Failed sending despawn packets for Null ID {} to {}", removedData.entityId(), targetPlayer.getName(), e);
                }
            } else {
                logger.debug("Skipping despawn packet send for Null ID {} - target player {} is offline.", removedData.entityId(), playerUUID);
            }
        } else {
            logger.debug("No active Null found for player {} during cancelNullForPlayer.", playerUUID);
        }
    }

    /**
     * Helper method to clean up a specific Null instance.
     * Removes from the map, cancels task, and sends despawn packets.
     * Uses the prepared packets stored in NullData.
     */
    private static void cleanupNull(@NotNull UUID targetPlayerUUID, int entityId, @NotNull Logger logger, @NotNull ProtocolManager protocolManager, @NotNull PacketContainer removeInfoPacket, @NotNull PacketContainer destroyPacket, BukkitRunnable taskToCancel) {

        activeNulls.remove(targetPlayerUUID);

        logger.debug("Cleaning up Null ID {} for player {}.", entityId, targetPlayerUUID);

        if (taskToCancel != null && !taskToCancel.isCancelled()) {
            taskToCancel.cancel();
            logger.debug("Cancelled Bukkit task for Null ID {}.", entityId);
        } else {
            logger.debug("Null ID {} task was already cancelled or null.", entityId);
        }

        Player targetPlayer = Bukkit.getPlayer(targetPlayerUUID);
        if (targetPlayer != null && targetPlayer.isOnline()) {
            try {
                logger.debug("Sending despawn packets for Null ID {} to {}", entityId, targetPlayer.getName());
                protocolManager.sendServerPacket(targetPlayer, destroyPacket);
                protocolManager.sendServerPacket(targetPlayer, removeInfoPacket);
            } catch (Exception e) {
                logger.error("Failed sending despawn packets for Null ID {} to {}", entityId, targetPlayer.getName(), e);
            }
        } else {
            logger.debug("Skipping despawn packet send for Null ID {} - target player {} is offline.", entityId, targetPlayerUUID);
        }
    }

    /**
     * Helper to check if a player is isolated from *other players* within a radius.
     * Adapted from StalkerSpawnEvent.
     */
    private static boolean isPlayerIsolatedFromOthers(@NotNull Player player) {
        if (Bukkit.getOnlinePlayers().size() <= 1) {
            return true;
        }

        double radiusSquared = NullSpawnEvent.NULL_ISOLATION_RADIUS_OTHERS * NullSpawnEvent.NULL_ISOLATION_RADIUS_OTHERS;
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
     * Attempts to find a valid spawn location directly behind the player on solid ground.
     */
    private Location findSpawnLocationBehindPlayer(@NotNull Player player, @NotNull Logger logger) {
        Location playerLoc = player.getLocation();
        World world = player.getWorld();


        Vector backwards = playerLoc.getDirection().multiply(-1).normalize();


        Location potentialLoc = playerLoc.clone().add(backwards.multiply(NULL_SPAWN_DISTANCE));


        double searchStartY = playerLoc.getY() + 1;
        Location groundCheckLoc = new Location(world, potentialLoc.getX(), searchStartY + 5, potentialLoc.getZ());

        double foundGroundY = EffectUtils.findGroundY(groundCheckLoc);


        if (foundGroundY != -1) {
            Location finalSpawnLoc = new Location(world, potentialLoc.getX(), foundGroundY, potentialLoc.getZ());


            Block feetBlock = finalSpawnLoc.getBlock();
            Block headBlock = finalSpawnLoc.clone().add(0, 1.0, 0).getBlock();
            Block groundBlock = finalSpawnLoc.clone().subtract(0, 0.1, 0).getBlock();


            if (groundBlock.isSolid() && !feetBlock.isLiquid() && feetBlock.isPassable() && !headBlock.isLiquid() && headBlock.isPassable()) {

                finalSpawnLoc.setPitch(0);
                finalSpawnLoc.setYaw(0);
                logger.debug("Found suitable Null spawn location: {}", finalSpawnLoc.toVector());
                return finalSpawnLoc;
            } else {
                logger.debug("Found ground at {}, but space is blocked (Ground: {}, Feet: {}, Head: {})", finalSpawnLoc.toVector(), groundBlock.getType(), feetBlock.getType(), headBlock.getType());
            }
        } else {
            logger.debug("Could not find solid ground below potential Null spawn location starting from Y {}", searchStartY);
        }


        logger.debug("Failed to find suitable Null spawn location behind player {}.", player.getName());
        return null;
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


        if (activeNulls.containsKey(player.getUniqueId())) {
            logger.debug("NullSpawn check for {}: Already active.", player.getName());
            return false;
        }


        if (player.isDead() || !player.isOnline() || player.isInsideVehicle() || player.isGliding() || player.isSwimming()) {
            logger.debug("NullSpawn check for {}: Player busy or in invalid state.", player.getName());
            return false;
        }


        if (!isPlayerIsolatedFromOthers(player)) {
            logger.debug("NullSpawn check for {}: Not isolated from other players (radius {}).", player.getName(), NULL_ISOLATION_RADIUS_OTHERS);
            return false;
        }


        Vector velocity = player.getVelocity();
        boolean isStandingStill = velocity.lengthSquared() < 0.001;
        boolean isLookingDown = player.getLocation().getPitch() > 45.0;

        if (!isStandingStill && !isLookingDown) {
            logger.debug("NullSpawn check for {}: Player is moving or looking forward/up (Velocity: {}, Pitch: {}).", player.getName(), velocity.lengthSquared(), player.getLocation().getPitch());
            return false;
        }


        logger.debug("NullSpawn check for {}: Passed eligibility.", player.getName());
        return true;
    }

    @Override
    public void trigger(@NotNull Player player, @NotNull Desync plugin) {
        Logger logger = plugin.getPluginLogger();
        ProtocolManager protocolManager = plugin.getProtocolManager();
        ThreadLocalRandom random = ThreadLocalRandom.current();
        UUID targetPlayerUUID = player.getUniqueId();


        if (activeNulls.containsKey(targetPlayerUUID)) {
            logger.debug("Skipping NullSpawn for {}: One is already active (defensive check in trigger).", player.getName());
            return;
        }


        WrappedGameProfile nullGameProfile = SkinUtils.getSkinProfile(player, logger, "Null");
        UUID nullUUID = nullGameProfile.getUUID();


        Location spawnLoc = findSpawnLocationBehindPlayer(player, logger);
        if (spawnLoc == null) {
            logger.warn("Could not find suitable Null spawn location behind {} after multiple attempts. Aborting NullSpawn.", player.getName());
            return;
        }


        Vector lookDir = player.getEyeLocation().toVector().subtract(spawnLoc.clone().add(0, 1.6, 0).toVector()).normalize();
        float yaw = (float) (Math.toDegrees(Math.atan2(lookDir.getZ(), lookDir.getX())) - 90);
        float pitch = (float) Math.toDegrees(-Math.atan2(lookDir.getY(), Math.sqrt(lookDir.getX() * lookDir.getX() + lookDir.getZ() * lookDir.getZ())));
        spawnLoc.setYaw(yaw);
        spawnLoc.setPitch(pitch);


        int entityId = fakeEntityIdCounter.incrementAndGet();


        PlayerInfoData playerInfoData = new PlayerInfoData(nullGameProfile, 1, NativeGameMode.SURVIVAL, null);
        PacketContainer playerInfoAddPacket = protocolManager.createPacket(PacketType.Play.Server.PLAYER_INFO);
        playerInfoAddPacket.getPlayerInfoActions().write(0, EnumSet.of(PlayerInfoAction.ADD_PLAYER));
        playerInfoAddPacket.getPlayerInfoDataLists().write(1, List.of(playerInfoData));


        PacketContainer spawnPacket = protocolManager.createPacket(PacketType.Play.Server.SPAWN_ENTITY);
        spawnPacket.getIntegers().write(0, entityId);
        spawnPacket.getUUIDs().write(0, nullUUID);
        spawnPacket.getEntityTypeModifier().write(0, EntityType.PLAYER);
        spawnPacket.getDoubles()
                .write(0, spawnLoc.getX())
                .write(1, spawnLoc.getY())
                .write(2, spawnLoc.getZ());
        spawnPacket.getBytes()
                .write(0, (byte) (spawnLoc.getYaw() * 256.0F / 360.0F))
                .write(1, (byte) (spawnLoc.getPitch() * 256.0F / 360.0F));
        spawnPacket.getIntegers().write(1, 0).write(2, 0).write(3, 0);
        spawnPacket.getIntegers().write(4, 0);


        PacketContainer metadataPacket = protocolManager.createPacket(PacketType.Play.Server.ENTITY_METADATA);
        metadataPacket.getIntegers().write(0, entityId);
        List<WrappedDataValue> wrappedDataValues = new ArrayList<>();


        byte skinLayers = (byte) 0x7E;

        wrappedDataValues.add(new WrappedDataValue(17, WrappedDataWatcher.Registry.get(Byte.class), skinLayers));
        metadataPacket.getDataValueCollectionModifier().write(0, wrappedDataValues);


        PacketContainer equipmentPacket = protocolManager.createPacket(PacketType.Play.Server.ENTITY_EQUIPMENT);
        equipmentPacket.getIntegers().write(0, entityId);
        List<Pair<EnumWrappers.ItemSlot, ItemStack>> equipmentList = EffectUtils.createMainHandEquipmentPair(null);
        equipmentPacket.getSlotStackPairLists().write(0, equipmentList);


        PacketContainer playerInfoRemovePacket = protocolManager.createPacket(PacketType.Play.Server.PLAYER_INFO_REMOVE);
        playerInfoRemovePacket.getUUIDLists().write(0, List.of(nullUUID));


        PacketContainer destroyPacket = protocolManager.createPacket(PacketType.Play.Server.ENTITY_DESTROY);
        destroyPacket.getIntLists().write(0, List.of(entityId));


        final PacketContainer finalPlayerInfoRemovePacket = playerInfoRemovePacket;
        final PacketContainer finalDestroyPacket = destroyPacket;

        try {
            logger.debug("Spawning Null (ID {}) for {} ({}) at {}", entityId, player.getName(), nullGameProfile.getName(), spawnLoc.toVector());


            protocolManager.sendServerPacket(player, playerInfoAddPacket);


            new BukkitRunnable() {
                @Override
                public void run() {
                    Player currentPlayer = Bukkit.getPlayer(targetPlayerUUID);
                    if (currentPlayer == null || !currentPlayer.isOnline()) {
                        activeNulls.remove(targetPlayerUUID);
                        logger.debug("Player {} logged off before Null ID {} spawn packets sent. Aborting spawn.", targetPlayerUUID, entityId);
                        return;
                    }

                    try {

                        protocolManager.sendServerPacket(currentPlayer, spawnPacket);
                        protocolManager.sendServerPacket(currentPlayer, metadataPacket);
                        protocolManager.sendServerPacket(currentPlayer, equipmentPacket);


                        new BukkitRunnable() {
                            @Override
                            public void run() {
                                Player p = Bukkit.getPlayer(targetPlayerUUID);
                                if (p == null || !p.isOnline()) return;
                                try {
                                    protocolManager.sendServerPacket(p, finalPlayerInfoRemovePacket);
                                    logger.debug("Sent PlayerInfoRemove packet for Null ID {} to {}", entityId, p.getName());
                                } catch (Exception e) {
                                    logger.error("Failed to send PlayerInfoRemove packet for ID {} to {}", entityId, p.getName(), e);
                                }
                            }
                        }.runTaskLater(plugin, 20L);

                    } catch (Exception e) {
                        logger.error("Failed to send initial Null spawn packets for ID {} to {}", entityId, targetPlayerUUID, e);

                        cleanupNull(targetPlayerUUID, entityId, logger, protocolManager, finalPlayerInfoRemovePacket, finalDestroyPacket, null);
                    }
                }
            }.runTaskLater(plugin, 1L);


            BukkitTask nullTask = new BukkitRunnable() {
                final double VIEW_ANGLE_THRESHOLD_COS = Math.cos(Math.toRadians(NULL_VIEW_ANGLE_THRESHOLD));
                long ticksLived = 0;
                boolean seenByPlayer = false;
                long seenTimestamp = -1;

                @Override
                public void run() {
                    Player originalTargetPlayer = Bukkit.getPlayer(targetPlayerUUID);


                    if (this.isCancelled() || originalTargetPlayer == null || !originalTargetPlayer.isOnline() || ticksLived >= NULL_MAX_LIFESPAN_TICKS) {
                        String reason = (originalTargetPlayer == null || !originalTargetPlayer.isOnline()) ? "Target Offline" : "Max Lifespan";
                        logger.debug("Null (ID {}) despawning: {}", entityId, reason);
                        cleanupNull(targetPlayerUUID, entityId, logger, protocolManager, finalPlayerInfoRemovePacket, finalDestroyPacket, this);
                        return;
                    }


                    if (!seenByPlayer) {
                        try {
                            Location playerEyeLoc = originalTargetPlayer.getEyeLocation();
                            Location nullEyeLoc = spawnLoc.clone().add(0, 1.6, 0);

                            Vector playerLook = playerEyeLoc.getDirection().normalize();
                            Vector dirToNull = nullEyeLoc.toVector().subtract(playerEyeLoc.toVector()).normalize();

                            if (dirToNull.lengthSquared() > 0.001) {
                                double dot = playerLook.dot(dirToNull);


                                if (dot >= VIEW_ANGLE_THRESHOLD_COS) {
                                    logger.debug("Null (ID {}) SEEN/TURNED TOWARDS by player {}", entityId, originalTargetPlayer.getName());
                                    seenByPlayer = true;
                                    seenTimestamp = System.currentTimeMillis();
                                }
                            }
                        } catch (Exception e) {

                            logger.debug("Error during Null view check for {}: {}", originalTargetPlayer.getName(), e.getMessage());
                        }
                    }


                    if (seenByPlayer) {
                        long timeSinceSeen = System.currentTimeMillis() - seenTimestamp;
                        if (timeSinceSeen >= (NULL_VIEW_DESPAWN_TICKS * 50L)) {
                            logger.debug("Null (ID {}) despawning after being seen.", entityId);
                            cleanupNull(targetPlayerUUID, entityId, logger, protocolManager, finalPlayerInfoRemovePacket, finalDestroyPacket, this);
                            return;
                        }
                    }


                    if (ticksLived % NULL_SOUND_INTERVAL_TICKS == 0) {
                        try {

                            Block groundBlock = spawnLoc.clone().subtract(0, 0.1, 0).getBlock();
                            Sound stepSound = getStepSound(groundBlock.getType());

                            float vol = random.nextFloat() * 0.1f + 0.15f;
                            float pit = random.nextFloat() * 0.2f + 0.9f;

                            PacketContainer soundPacket = protocolManager.createPacket(PacketType.Play.Server.NAMED_SOUND_EFFECT);
                            soundPacket.getSoundEffects().write(0, stepSound);
                            soundPacket.getSoundCategories().write(0, com.comphenix.protocol.wrappers.EnumWrappers.SoundCategory.PLAYERS);


                            soundPacket.getIntegers()
                                    .write(0, (int) (spawnLoc.getX() * 8.0D))
                                    .write(1, (int) (spawnLoc.getY() * 8.0D))
                                    .write(2, (int) (spawnLoc.getZ() * 8.0D));
                            soundPacket.getFloat().write(0, vol * 16.0F).write(1, pit);
                            soundPacket.getLongs().write(0, random.nextLong());

                            protocolManager.sendServerPacket(originalTargetPlayer, soundPacket);
                            logger.debug("Sent Null footstep sound packet for ID {} to {}", entityId, originalTargetPlayer.getName());

                        } catch (Exception e) {
                            logger.error("Failed to send Null sound packet for ID {} to {}", entityId, originalTargetPlayer.getName(), e);
                        }
                    }


                    try {
                        Location currentNullHeadPos = spawnLoc.clone().add(0, 1.6, 0);
                        Vector lookDir = originalTargetPlayer.getEyeLocation().toVector().subtract(currentNullHeadPos.toVector());

                        float bodyYaw = (float) (Math.toDegrees(Math.atan2(lookDir.getZ(), lookDir.getX())) - 90);
                        float pitch = (float) Math.toDegrees(-Math.atan2(lookDir.getY(), Math.sqrt(lookDir.getX() * lookDir.getX() + lookDir.getZ() * lookDir.getZ())));


                        pitch = Math.max(-89.9f, Math.min(89.9f, pitch));


                        PacketContainer headPacket = protocolManager.createPacket(PacketType.Play.Server.ENTITY_HEAD_ROTATION);
                        headPacket.getIntegers().write(0, entityId);
                        headPacket.getBytes().write(0, (byte) (bodyYaw * 256.0F / 360.0F));
                        protocolManager.sendServerPacket(originalTargetPlayer, headPacket);


                        PacketContainer bodyLookPacket = protocolManager.createPacket(PacketType.Play.Server.ENTITY_LOOK);
                        bodyLookPacket.getIntegers().write(0, entityId);
                        bodyLookPacket.getBytes()
                                .write(0, (byte) (bodyYaw * 256.0F / 360.0F))
                                .write(1, (byte) (pitch * 256.0F / 360.0F));
                        bodyLookPacket.getBooleans().write(0, true);
                        protocolManager.sendServerPacket(originalTargetPlayer, bodyLookPacket);

                    } catch (Exception e) {
                        logger.error("Failed to send Null rotation packets for ID {} to {}", entityId, originalTargetPlayer.getName(), e);
                    }


                    ticksLived += 1L;
                }
            }.runTaskTimer(plugin, 5L, 1L);


            activeNulls.put(targetPlayerUUID, new NullData(entityId, nullUUID, nullTask, finalPlayerInfoRemovePacket, finalDestroyPacket));

        } catch (Exception e) {
            logger.error("Failed to send Null initial spawn packets for {}!", player.getName(), e);

            activeNulls.remove(targetPlayerUUID);
            logger.warn("Removed Null entry from map for {}.", player.getName());
        }
    }

    /**
     * Helper to get a plausible step sound based on block type.
     * Add more mappings as needed.
     */
    private Sound getStepSound(@NotNull Material material) {
        if (material == Material.GRASS_BLOCK || material == Material.TALL_GRASS || material == Material.FERN) {
            return Sound.BLOCK_GRASS_STEP;
        } else if (material == Material.STONE || material == Material.COBBLESTONE || material == Material.ANDESITE || material == Material.DIORITE || material == Material.GRANITE) {
            return Sound.BLOCK_STONE_STEP;
        } else if (material.name().contains("WOOD") || material.name().contains("PLANKS")) {
            return Sound.BLOCK_WOOD_STEP;
        } else if (material == Material.SAND || material == Material.RED_SAND) {
            return Sound.BLOCK_SAND_STEP;
        } else if (material == Material.GRAVEL) {
            return Sound.BLOCK_GRAVEL_STEP;
        } else if (material == Material.SNOW_BLOCK || material == Material.SNOW) {
            return Sound.BLOCK_SNOW_STEP;
        } else if (material == Material.GLASS || material == Material.GLASS_PANE) {
            return Sound.BLOCK_GLASS_STEP;
        }

        return Sound.BLOCK_STONE_STEP;
    }


    public record NullData(int entityId, UUID nullUuid, BukkitTask task, PacketContainer removeInfoPacket,
                           PacketContainer destroyPacket) {
    }
}