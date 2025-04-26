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
 * Spawns a temporary, distant fake player entity that looks at the target player.
 * Attempts to despawn shortly after the player looks towards it.
 * Uses another online player's skin or a default skin if alone (handled by SkinUtils).
 */
public class StalkerSpawnEvent implements PlayerDesyncEvent {

    public static final Map<UUID, StalkerData> activeStalkers = new ConcurrentHashMap<>();
    private static final String KEY = "stalker";
    private static final String DESCRIPTION = "A distant figure briefly appears and watches you.";
    private static final long DEFAULT_COOLDOWN_MS = 15 * 60 * 1000L;
    private static final List<Material> STALKER_ITEM_CANDIDATES = List.of(
            Material.STONE_SWORD,
            Material.WOODEN_SWORD,
            Material.STONE_PICKAXE,
            Material.TORCH,
            Material.COMPASS,
            Material.PAPER,
            Material.BONE,
            Material.ROTTEN_FLESH

    );
    private static final double STALKER_MIN_DISTANCE = 60.0;
    private static final double STALKER_MAX_DISTANCE = 100.0;
    private static final double STALKER_VIEW_ANGLE_THRESHOLD = 65.0;
    private static final long STALKER_VIEW_DESPAWN_TICKS = 80;
    private static final double STALKER_INTERACTION_RADIUS = STALKER_MAX_DISTANCE + 35.0;
    private static final long STALKER_MAX_LIFESPAN_TICKS = 20 * 60;
    private static final double STALKER_ISOLATION_RADIUS_OTHERS = 128.0;
    static final AtomicInteger fakeEntityIdCounter = new AtomicInteger(Integer.MIN_VALUE / 2);

    /**
     * Static helper to check if a specific mob is currently involved in an active stalker task.
     * Currently, this is only needed internally for filtering candidates, but made public
     * following the AnimalFollow/AnimalStare pattern for consistency, though currently unused externally.
     *
     * @param mobUuid The UUID of the mob (stalker) to check.
     * @return True if the mob is currently a stalker, false otherwise.
     */
    public static boolean isMobStalker(@NotNull UUID mobUuid) {

        return activeStalkers.values().stream()
                .anyMatch(stalkerData -> stalkerData.stalkerUuid().equals(mobUuid));
    }

    /**
     * Helper to check if a player is isolated from *other players* within a radius.
     * Static helper used by canTrigger.
     */
    private static boolean isPlayerIsolatedFromOthers(@NotNull Player player) {
        if (Bukkit.getOnlinePlayers().size() <= 1) {
            return true;
        }

        double radiusSquared = StalkerSpawnEvent.STALKER_ISOLATION_RADIUS_OTHERS * StalkerSpawnEvent.STALKER_ISOLATION_RADIUS_OTHERS;
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
     * Helper method to clean up a specific stalker instance.
     * Removes from the map, cancels task, and sends despawn packets.
     * This is called internally by the stalker's BukkitRunnable.
     * Uses the prepared packets stored in StalkerData.
     */
    private static void cleanupStalker(@NotNull UUID targetPlayerUUID, int entityId, @NotNull Logger logger, @NotNull ProtocolManager protocolManager, @NotNull PacketContainer removeInfoPacket, @NotNull PacketContainer destroyPacket, BukkitRunnable taskToCancel) {


        activeStalkers.remove(targetPlayerUUID);


        logger.debug("Cleaning up stalker ID {} for player {}.", entityId, targetPlayerUUID);


        if (taskToCancel != null && !taskToCancel.isCancelled()) {
            taskToCancel.cancel();
            logger.debug("Cancelled Bukkit task for stalker ID {}.", entityId);
        } else {

            logger.debug("Stalker ID {} task was already cancelled or null.", entityId);
        }


        Player targetPlayer = Bukkit.getPlayer(targetPlayerUUID);
        if (targetPlayer != null && targetPlayer.isOnline()) {
            try {
                logger.debug("Sending despawn packets for stalker ID {} to {}", entityId, targetPlayer.getName());

                protocolManager.sendServerPacket(targetPlayer, destroyPacket);
                protocolManager.sendServerPacket(targetPlayer, removeInfoPacket);
            } catch (Exception e) {
                logger.error("Failed sending despawn packets for stalker ID {} to {}", entityId, targetPlayer.getName(), e);
            }
        } else {
            logger.debug("Skipping despawn packet send for stalker ID {} - target player {} is offline.", entityId, targetPlayerUUID);
        }
    }

    /**
     * Static helper to cancel a stalker specifically by target player UUID.
     * Called by PlayerQuitListener or EventService cleanup.
     * Retrieves stored packets from the StalkerData record.
     */
    public static void cancelStalkerForPlayer(@NotNull UUID playerUUID, @NotNull Logger logger) {
        logger.debug("Attempting stalker cancellation for player {}", playerUUID);

        StalkerData removedData = activeStalkers.remove(playerUUID);

        if (removedData != null) {
            logger.debug("Cancelling active stalker ID {} for player {}", removedData.entityId(), playerUUID);

            if (removedData.task() != null && !removedData.task().isCancelled()) {
                removedData.task().cancel();
                logger.debug("Cancelled Bukkit task for stalker ID {}.", removedData.entityId());
            } else {
                logger.debug("Stalker ID {} task was already cancelled or null during PlayerQuit cleanup.", removedData.entityId());
            }


            Player targetPlayer = Bukkit.getPlayer(playerUUID);
            if (targetPlayer != null && targetPlayer.isOnline()) {
                try {
                    logger.debug("Sending despawn packets for stalker ID {} to {}", removedData.entityId(), targetPlayer.getName());
                    ProtocolManager protocolManager = Desync.getInstance().getProtocolManager();

                    protocolManager.sendServerPacket(targetPlayer, removedData.destroyPacket());
                    protocolManager.sendServerPacket(targetPlayer, removedData.removeInfoPacket());
                } catch (Exception e) {
                    logger.error("Failed sending despawn packets for stalker ID {} to {}", removedData.entityId(), targetPlayer.getName(), e);
                }
            } else {
                logger.debug("Skipping despawn packet send for stalker ID {} - target player {} is offline.", removedData.entityId(), playerUUID);
            }
        } else {
            logger.debug("No active stalker found for player {} during cancelStalkerForPlayer.", playerUUID);
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


        if (activeStalkers.containsKey(player.getUniqueId())) {
            logger.debug("StalkerSpawn check for {}: Already active stalker.", player.getName());
            return false;
        }


        if (!isPlayerIsolatedFromOthers(player)) {
            logger.debug("StalkerSpawn check for {}: Not isolated from other players (radius {}).", player.getName(), STALKER_ISOLATION_RADIUS_OTHERS);
            return false;
        }


        World world = player.getWorld();
        if (world.getEnvironment() != World.Environment.NORMAL) {
            logger.debug("StalkerSpawn check for {}: Not in normal world (Env: {}).", player.getName(), world.getEnvironment());
            return false;
        }


        long time = world.getTime();
        boolean isNight = time > 13000 && time < 23000;
        int lightLevel = player.getLocation().getBlock().getLightLevel();


        if (!isNight && lightLevel > 7) {
            logger.debug("StalkerSpawn check for {}: Not dark enough (Time: {}, Light: {}).", player.getName(), time, lightLevel);
            return false;
        }


        double seaLevel = world.getSeaLevel();
        if (player.getLocation().getY() < seaLevel - 20 && lightLevel < 7) {
            logger.debug("StalkerSpawn check for {}: Too deep underground (Y: {}).", player.getName(), (int) player.getLocation().getY());
            return false;
        }


        logger.debug("StalkerSpawn check for {}: Passed basic eligibility.", player.getName());
        return true;
    }

    /**
     * Spawns a temporary, distant fake player entity that looks at the target player.
     * Attempts to despawn shortly after the player looks towards it.
     * Uses another online player's skin or a default skin if alone (handled by SkinUtils).
     *
     * @param player The player to be observed.
     * @param plugin The main plugin instance.
     */
    @Override
    public void trigger(@NotNull Player player, @NotNull Desync plugin) {
        Logger logger = plugin.getPluginLogger();
        ProtocolManager protocolManager = plugin.getProtocolManager();
        ThreadLocalRandom random = ThreadLocalRandom.current();
        UUID targetPlayerUUID = player.getUniqueId();


        if (activeStalkers.containsKey(targetPlayerUUID)) {
            logger.debug("Skipping StalkerSpawn for {}: One is already active (defensive check in trigger).", player.getName());
            return;
        }


        WrappedGameProfile stalkerGameProfile = SkinUtils.getSkinProfile(player, logger, "Null");


        UUID stalkerUUID = stalkerGameProfile.getUUID();


        Location spawnLoc = findSpawnLocation(player, plugin);
        if (spawnLoc == null) {
            logger.warn("Could not find suitable stalker spawn location for {} after multiple attempts. Aborting StalkerSpawn.", player.getName());
            return;
        }


        Vector lookDir = player.getEyeLocation().toVector().subtract(spawnLoc.clone().add(0, 1.6, 0).toVector()).normalize();
        float yaw = (float) (Math.toDegrees(Math.atan2(lookDir.getZ(), lookDir.getX())) - 90);
        float pitch = (float) Math.toDegrees(-Math.atan2(lookDir.getY(), Math.sqrt(lookDir.getX() * lookDir.getX() + lookDir.getZ() * lookDir.getZ())));
        spawnLoc.setYaw(yaw);
        spawnLoc.setPitch(pitch);


        int entityId = fakeEntityIdCounter.incrementAndGet();


        PlayerInfoData playerInfoData = new PlayerInfoData(stalkerGameProfile, 1, NativeGameMode.SURVIVAL, null);
        PacketContainer playerInfoAddPacket = protocolManager.createPacket(PacketType.Play.Server.PLAYER_INFO);
        playerInfoAddPacket.getPlayerInfoActions().write(0, EnumSet.of(PlayerInfoAction.ADD_PLAYER));
        playerInfoAddPacket.getPlayerInfoDataLists().write(1, List.of(playerInfoData));


        PacketContainer spawnPacket = protocolManager.createPacket(PacketType.Play.Server.SPAWN_ENTITY);
        spawnPacket.getIntegers().write(0, entityId);
        spawnPacket.getUUIDs().write(0, stalkerUUID);
        spawnPacket.getEntityTypeModifier().write(0, EntityType.PLAYER);
        spawnPacket.getDoubles()
                .write(0, spawnLoc.getX())
                .write(1, spawnLoc.getY())
                .write(2, spawnLoc.getZ());
        spawnPacket.getBytes()
                .write(0, (byte) (spawnLoc.getPitch() * 256.0F / 360.0F))
                .write(1, (byte) (spawnLoc.getYaw() * 256.0F / 360.0F));
        spawnPacket.getIntegers().write(1, 0).write(2, 0).write(3, 0);
        spawnPacket.getIntegers().write(4, 0);


        PacketContainer metadataPacket = protocolManager.createPacket(PacketType.Play.Server.ENTITY_METADATA);
        metadataPacket.getIntegers().write(0, entityId);
        List<WrappedDataValue> wrappedDataValues = new ArrayList<>();

        byte skinLayers = (byte) 0x7E;
        wrappedDataValues.add(new WrappedDataValue(17, WrappedDataWatcher.Registry.get(Byte.class), skinLayers));
        metadataPacket.getDataValueCollectionModifier().write(0, wrappedDataValues);


        ItemStack heldItem = null;
        if (random.nextDouble() < 0.7) {
            Material itemType = STALKER_ITEM_CANDIDATES.get(random.nextInt(STALKER_ITEM_CANDIDATES.size()));
            heldItem = new ItemStack(itemType);
        }
        PacketContainer equipmentPacket = protocolManager.createPacket(PacketType.Play.Server.ENTITY_EQUIPMENT);
        equipmentPacket.getIntegers().write(0, entityId);

        List<Pair<EnumWrappers.ItemSlot, ItemStack>> equipmentList = EffectUtils.createMainHandEquipmentPair(heldItem);
        equipmentPacket.getSlotStackPairLists().write(0, equipmentList);
        logger.debug("Stalker (ID {}) will hold: {}", entityId, (heldItem != null ? heldItem.getType() : "nothing"));


        PacketContainer playerInfoRemovePacket = protocolManager.createPacket(PacketType.Play.Server.PLAYER_INFO_REMOVE);
        playerInfoRemovePacket.getUUIDLists().write(0, List.of(stalkerUUID));


        PacketContainer destroyPacket = protocolManager.createPacket(PacketType.Play.Server.ENTITY_DESTROY);
        destroyPacket.getIntLists().write(0, List.of(entityId));


        final PacketContainer finalPlayerInfoRemovePacket = playerInfoRemovePacket;
        final PacketContainer finalDestroyPacket = destroyPacket;

        try {
            logger.debug("Spawning Stalker (ID {}) for {} ({}) at {}", entityId, player.getName(), stalkerGameProfile.getName(), spawnLoc.toVector());


            protocolManager.sendServerPacket(player, playerInfoAddPacket);


            new BukkitRunnable() {
                @Override
                public void run() {
                    if (!player.isOnline()) {


                        activeStalkers.remove(targetPlayerUUID);
                        logger.debug("Player {} logged off before stalker ID {} spawn packets sent. Aborting spawn.", player.getName(), entityId);
                        return;
                    }

                    try {

                        protocolManager.sendServerPacket(player, spawnPacket);

                        protocolManager.sendServerPacket(player, metadataPacket);

                        protocolManager.sendServerPacket(player, equipmentPacket);


                        new BukkitRunnable() {
                            @Override
                            public void run() {
                                if (!player.isOnline()) return;
                                try {
                                    protocolManager.sendServerPacket(player, finalPlayerInfoRemovePacket);
                                    logger.debug("Sent PlayerInfoRemove packet for stalker ID {} to {}", entityId, player.getName());
                                } catch (Exception e) {
                                    logger.error("Failed to send PlayerInfoRemove packet for ID {} to {}", entityId, player.getName(), e);
                                }
                            }
                        }.runTaskLater(plugin, 20L);
                    } catch (Exception e) {
                        logger.error("Failed to send initial Stalker spawn packets for ID {} to {}", entityId, player.getName(), e);


                        cleanupStalker(targetPlayerUUID, entityId, logger, protocolManager, finalPlayerInfoRemovePacket, finalDestroyPacket, null);
                    }
                }
            }.runTaskLater(plugin, 2L);


            BukkitTask viewCheckTask = new BukkitRunnable() {
                final double MIN_PROXIMITY_SQ = 20.0 * 20.0;
                long ticksLived = 0;
                boolean seenByAny = false;
                long seenTimestamp = -1;

                @Override
                public void run() {
                    Player originalTargetPlayer = Bukkit.getPlayer(targetPlayerUUID);


                    if (this.isCancelled() || originalTargetPlayer == null || !originalTargetPlayer.isOnline() || ticksLived >= STALKER_MAX_LIFESPAN_TICKS) {
                        String reason = (originalTargetPlayer == null || !originalTargetPlayer.isOnline()) ? "Target Offline" : "Max Lifespan";
                        logger.debug("Stalker (ID {}) despawning: {}", entityId, reason);

                        cleanupStalker(targetPlayerUUID, entityId, logger, protocolManager, finalPlayerInfoRemovePacket, finalDestroyPacket, this);
                        return;
                    }


                    ThreadLocalRandom random = ThreadLocalRandom.current();

                    if (random.nextDouble() < 0.005 * 5) {
                        try {
                            PacketContainer packetContainer = protocolManager.createPacket(PacketType.Play.Server.ANIMATION);
                            packetContainer.getIntegers().write(0, entityId);
                            packetContainer.getIntegers().write(1, 0);
                            protocolManager.sendServerPacket(originalTargetPlayer, packetContainer);

                        } catch (Exception e) {
                            logger.error("Failed to send Stalker arm swing packet for ID {} to {}", entityId, originalTargetPlayer.getName(), e);
                        }
                    }


                    if (random.nextDouble() < 0.01) {
                        try {

                            Sound subtleSound = random.nextBoolean() ? Sound.ENTITY_PLAYER_HURT : Sound.BLOCK_GRAVEL_STEP;
                            float vol = random.nextFloat() * 0.2f + 0.1f;
                            float pit = random.nextFloat() * 0.1f + 0.95f;

                            PacketContainer soundPacket = protocolManager.createPacket(PacketType.Play.Server.NAMED_SOUND_EFFECT);
                            soundPacket.getSoundEffects().write(0, subtleSound);
                            soundPacket.getSoundCategories().write(0, com.comphenix.protocol.wrappers.EnumWrappers.SoundCategory.PLAYERS);

                            soundPacket.getIntegers()
                                    .write(0, (int) (spawnLoc.getX() * 8.0D))
                                    .write(1, (int) (spawnLoc.getY() * 8.0D))
                                    .write(2, (int) (spawnLoc.getZ() * 8.0D));
                            soundPacket.getFloat().write(0, vol * 16.0F).write(1, pit);
                            soundPacket.getLongs().write(0, random.nextLong());

                            protocolManager.sendServerPacket(originalTargetPlayer, soundPacket);


                        } catch (Exception e) {
                            logger.error("Failed to send Stalker location sound packet for ID {} to {}", entityId, originalTargetPlayer.getName(), e);
                        }
                    }


                    try {

                        Location currentStalkerHeadPos = spawnLoc.clone().add(0, 1.6, 0);
                        Vector lookDir = originalTargetPlayer.getEyeLocation().toVector().subtract(currentStalkerHeadPos.toVector());


                        float bodyYaw = (float) (Math.toDegrees(Math.atan2(lookDir.getZ(), lookDir.getX())) - 90);
                        float pitch = (float) Math.toDegrees(-Math.atan2(lookDir.getY(), Math.sqrt(lookDir.getX() * lookDir.getX() + lookDir.getZ() * lookDir.getZ())));


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
                        logger.error("Failed to send Stalker rotation packets for ID {} to {}", entityId, originalTargetPlayer.getName(), e);
                    }


                    boolean proximityTrigger = false;


                    Collection<Player> nearbyPlayers = spawnLoc.getWorld().getNearbyPlayers(spawnLoc, STALKER_INTERACTION_RADIUS);

                    for (Player nearbyPlayer : nearbyPlayers) {
                        if (nearbyPlayer == null || !nearbyPlayer.isOnline()) continue;


                        if (spawnLoc.distanceSquared(nearbyPlayer.getLocation()) < MIN_PROXIMITY_SQ) {
                            logger.debug("Stalker (ID {}) despawning: Player {} got too close.", entityId, nearbyPlayer.getName());
                            proximityTrigger = true;
                            break;
                        }


                        if (!seenByAny) {

                            Vector playerLook = nearbyPlayer.getEyeLocation().getDirection().normalize();
                            Vector dirToStalker = spawnLoc.clone().add(0, 1.6, 0).toVector()
                                    .subtract(nearbyPlayer.getEyeLocation().toVector()).normalize();


                            if (dirToStalker.lengthSquared() > 0.01) {
                                double angle = Math.toDegrees(Math.acos(Math.max(-1.0, Math.min(1.0, playerLook.dot(dirToStalker)))));


                                if (angle <= STALKER_VIEW_ANGLE_THRESHOLD) {
                                    logger.debug("Stalker (ID {}) SEEN by player {}", entityId, nearbyPlayer.getName());
                                    seenByAny = true;
                                    seenTimestamp = System.currentTimeMillis();

                                }
                            }
                        }
                    }


                    if (proximityTrigger) {

                        cleanupStalker(targetPlayerUUID, entityId, logger, protocolManager, finalPlayerInfoRemovePacket, finalDestroyPacket, this);
                        return;
                    }

                    if (seenByAny) {
                        long timeSinceSeen = System.currentTimeMillis() - seenTimestamp;

                        if (timeSinceSeen >= (STALKER_VIEW_DESPAWN_TICKS * 50L)) {
                            logger.debug("Stalker (ID {}) despawning after being seen.", entityId);

                            cleanupStalker(targetPlayerUUID, entityId, logger, protocolManager, finalPlayerInfoRemovePacket, finalDestroyPacket, this);
                            return;
                        }
                    }

                    ticksLived += 1L;
                }
            }.runTaskTimer(plugin, 10L, 1L);


            activeStalkers.put(targetPlayerUUID, new StalkerData(entityId, stalkerUUID, viewCheckTask, finalPlayerInfoRemovePacket, finalDestroyPacket));


        } catch (Exception e) {
            logger.error("Failed to send Stalker initial spawn packets for {}!", player.getName(), e);


            activeStalkers.remove(targetPlayerUUID);
            logger.warn("Removed stalker entry from map for {}.", player.getName());
        }
    }

    /**
     * Helper method to find a suitable spawn location for the stalker.
     * Searches for a location in the player's peripheral vision, distant, and on solid ground, with line of sight.
     */
    private Location findSpawnLocation(@NotNull Player player, @NotNull Desync plugin) {
        Logger logger = plugin.getPluginLogger();
        Location playerLoc = player.getLocation();
        World world = player.getWorld();
        ThreadLocalRandom random = ThreadLocalRandom.current();

        int attempts = 0;
        int maxAttempts = 50;


        Location playerEyeLoc = player.getEyeLocation();


        int worldMinY = world.getMinHeight();
        int worldMaxY = world.getMaxHeight();


        while (attempts < maxAttempts) {
            attempts++;


            double angle = random.nextDouble() * 2 * Math.PI;
            double distance = STALKER_MIN_DISTANCE + random.nextDouble() * (STALKER_MAX_DISTANCE - STALKER_MIN_DISTANCE);


            double randomX = Math.cos(angle) * distance;
            double randomZ = Math.sin(angle) * distance;


            int startY = playerLoc.getBlockY();
            int verticalSearchRange = 10;
            int minYSearch = Math.max(worldMinY, startY - verticalSearchRange);
            int maxYSearch = Math.min(worldMaxY - 1, startY + verticalSearchRange);


            for (int currentY = maxYSearch; currentY >= minYSearch; currentY--) {

                Location groundCheckLoc = new Location(world,
                        playerLoc.getBlockX() + randomX,
                        currentY,
                        playerLoc.getBlockZ() + randomZ
                );


                if (groundCheckLoc.getY() < worldMinY || groundCheckLoc.getY() >= worldMaxY ||
                        groundCheckLoc.getWorld() == null || !groundCheckLoc.getWorld().isChunkLoaded(groundCheckLoc.getChunk()))
                    continue;


                double foundGroundYAbove = EffectUtils.findGroundY(groundCheckLoc);


                Location potentialBase = new Location(world,
                        groundCheckLoc.getBlockX(),
                        foundGroundYAbove,
                        groundCheckLoc.getBlockZ());


                if (potentialBase.getY() < worldMinY || potentialBase.getY() >= worldMaxY ||
                        potentialBase.getWorld() == null || !potentialBase.getWorld().isChunkLoaded(potentialBase.getChunk()))
                    continue;


                Block blockBelowBase = potentialBase.clone().subtract(0, 1.0, 0).getBlock();
                if (blockBelowBase.isPassable() || blockBelowBase.isLiquid() || blockBelowBase.getType().hasGravity() || !blockBelowBase.getType().isSolid()) {

                    logger.debug("Attempt {}: Location {} does not have solid ground below base (Block Below: {})", attempts, potentialBase.toVector(), blockBelowBase.getType());
                    continue;
                }


                Block feetBlock = potentialBase.getBlock();
                Block headBlock = potentialBase.clone().add(0, 1.0, 0).getBlock();
                if (!feetBlock.isPassable() || feetBlock.isLiquid() || feetBlock.getType().isSolid() ||
                        !headBlock.isPassable() || headBlock.isLiquid() || headBlock.getType().isSolid()) {

                    logger.debug("Attempt {}: Space at potential base location {} is blocked (Feet: {}, Head: {})", attempts, potentialBase.toVector(), feetBlock.getType(), headBlock.getType());
                    continue;
                }


                Vector dirFromPlayer = potentialBase.toVector().subtract(playerLoc.toVector()).normalize();
                Vector playerLookDir = playerLoc.getDirection().normalize();

                if (dirFromPlayer.lengthSquared() > 0.01) {
                    double dot = playerLookDir.dot(dirFromPlayer);
                    double angleFromPlayerLook = Math.toDegrees(Math.acos(Math.max(-1.0, Math.min(1.0, dot))));


                    if (angleFromPlayerLook < 60) {

                        continue;
                    }
                } else {

                    continue;
                }


                Location stalkerEyeLevel = potentialBase.clone().add(0, 1.6, 0);
                if (EffectUtils.hasLineOfSight(stalkerEyeLevel, playerEyeLoc, plugin)) {


                    potentialBase.setPitch(0);
                    potentialBase.setYaw(0);
                    logger.debug("Found suitable stalker spawn location after {} attempts: {}", attempts, potentialBase.toVector());
                    return potentialBase;
                }
            }
        }

        logger.debug("Could not find suitable StalkerSpawn placement location for {} after {} attempts.", player.getName(), maxAttempts);
        return null;
    }

    public record StalkerData(int entityId, UUID stalkerUuid, BukkitTask task, PacketContainer removeInfoPacket,
                              PacketContainer destroyPacket) {
    }
}