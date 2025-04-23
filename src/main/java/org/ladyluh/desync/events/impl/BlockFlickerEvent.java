package org.ladyluh.desync.events.impl;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Lightable;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.RayTraceResult;
import org.ladyluh.desync.Desync;
import org.ladyluh.desync.events.PlayerDesyncEvent;
import org.ladyluh.desync.utils.EffectUtils;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Makes a nearby block briefly flicker to a different, related type for the player.
 */
public class BlockFlickerEvent implements PlayerDesyncEvent {

    private static final String KEY = "block_flicker";
    private static final String DESCRIPTION = "A nearby block briefly changes appearance.";
    private static final long DEFAULT_COOLDOWN_MS = 75 * 1000L;

    private static final int SEARCH_RADIUS = 8;
    private static final int LIGHT_THRESHOLD = 5;


    private static final Map<Material, List<Material>> BLOCK_FLICKER_MAP = new EnumMap<>(Map.ofEntries(
            Map.entry(Material.STONE, List.of(Material.COBBLESTONE, Material.MOSSY_COBBLESTONE, Material.ANDESITE)),
            Map.entry(Material.COBBLESTONE, List.of(Material.STONE, Material.MOSSY_COBBLESTONE, Material.GRAVEL)),
            Map.entry(Material.MOSSY_COBBLESTONE, List.of(Material.COBBLESTONE, Material.STONE)),
            Map.entry(Material.DIRT, List.of(Material.COARSE_DIRT, Material.GRAVEL, Material.PODZOL)),
            Map.entry(Material.GRASS_BLOCK, List.of(Material.DIRT, Material.MYCELIUM, Material.PODZOL)),
            Map.entry(Material.SAND, List.of(Material.RED_SAND, Material.GRAVEL)),
            Map.entry(Material.GRAVEL, List.of(Material.SAND, Material.CLAY)),
            Map.entry(Material.OAK_LOG, List.of(Material.SPRUCE_LOG, Material.DARK_OAK_LOG)),
            Map.entry(Material.SPRUCE_LOG, List.of(Material.OAK_LOG, Material.BIRCH_LOG)),
            Map.entry(Material.OAK_PLANKS, List.of(Material.SPRUCE_PLANKS, Material.DARK_OAK_PLANKS)),
            Map.entry(Material.STONE_BRICKS, List.of(Material.MOSSY_STONE_BRICKS, Material.CRACKED_STONE_BRICKS)),
            Map.entry(Material.MOSSY_STONE_BRICKS, List.of(Material.STONE_BRICKS, Material.INFESTED_MOSSY_STONE_BRICKS)),
            Map.entry(Material.CRACKED_STONE_BRICKS, List.of(Material.STONE_BRICKS, Material.INFESTED_CRACKED_STONE_BRICKS))

    ));


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
    public boolean canTrigger(Player player) {


        try {

            RayTraceResult rayTrace = player.rayTraceBlocks(SEARCH_RADIUS);
            if (rayTrace != null && rayTrace.getHitBlock() != null) {
                Block block = rayTrace.getHitBlock();
                if (isEligibleFlickerBlock(block)) return true;
            }


            Location startLoc = player.getEyeLocation();
            ThreadLocalRandom random = ThreadLocalRandom.current();
            int sampleSize = 10;

            for (int i = 0; i < sampleSize; i++) {
                int x = random.nextInt(-SEARCH_RADIUS, SEARCH_RADIUS + 1);
                int y = random.nextInt(-SEARCH_RADIUS / 2, SEARCH_RADIUS / 2 + 1);
                int z = random.nextInt(-SEARCH_RADIUS, SEARCH_RADIUS + 1);
                if (x == 0 && y == 0 && z == 0) continue;

                Location checkLoc = startLoc.clone().add(x, y, z);
                if (checkLoc.getWorld() == null || !checkLoc.getWorld().isChunkLoaded(checkLoc.getChunk()))
                    continue;

                Block block = checkLoc.getBlock();
                if (isEligibleFlickerBlock(block)) {
                    return true;
                }
            }

        } catch (Exception e) {

            Desync.getInstance().getPluginLogger().error("Error during BlockFlicker canTrigger check for {}", player.getName(), e);
            return false;
        }
        return false;
    }

    /**
     * Plays a fake block flicker effect for the given player.
     * Finds an eligible block and briefly changes its appearance clientside.
     *
     * @param player The player to show the flicker to.
     * @param plugin The main plugin instance.
     */
    @Override
    public void trigger(Player player, Desync plugin) {
        Logger logger = plugin.getPluginLogger();
        ThreadLocalRandom random = ThreadLocalRandom.current();

        long flickerDurationTicks = 10 + random.nextInt(11);


        Block targetBlock = null;
        RayTraceResult rayTrace = player.rayTraceBlocks(SEARCH_RADIUS);
        if (rayTrace != null && rayTrace.getHitBlock() != null) {
            Block lookedAt = rayTrace.getHitBlock();
            if (isEligibleFlickerBlock(lookedAt)) {
                targetBlock = lookedAt;
                logger.debug("BlockFlicker trying block player is looking at: {}", targetBlock.getType());
            }
        }


        if (targetBlock == null) {
            targetBlock = findRandomNearbyEligibleBlock(player, plugin);
            if (targetBlock != null) {
                logger.debug("BlockFlicker found random nearby candidate: {}", targetBlock.getType());
            }
        }

        if (targetBlock == null) {

            logger.debug("BlockFlicker trigger for {}: No suitable target block found during trigger execution.", player.getName());
            return;
        }

        Material originalMaterial = targetBlock.getType();
        BlockData originalBlockData = targetBlock.getBlockData();
        Location blockLocation = targetBlock.getLocation();


        BlockData flickerBlockData = null;
        Material flickerMaterial;


        if (originalBlockData instanceof Lightable lightable && lightable.isLit()) {
            lightable.setLit(false);
            flickerBlockData = lightable.clone();
            logger.debug("Flickering lit block {} OFF at {}", originalMaterial, blockLocation.toVector());
        } else if (originalMaterial == Material.TORCH || originalMaterial == Material.WALL_TORCH) {
            flickerMaterial = (originalMaterial == Material.TORCH) ? Material.REDSTONE_TORCH : Material.REDSTONE_WALL_TORCH;
            try {

                flickerBlockData = Bukkit.createBlockData(flickerMaterial);

                logger.debug("Flickering torch {} to {} at {}", originalMaterial, flickerMaterial, blockLocation.toVector());
            } catch (IllegalArgumentException e) {
                logger.warn("Could not create flicker block data for {} at {}. Skipping flicker.", flickerMaterial, blockLocation.toVector(), e);
                flickerBlockData = null;
            }
        } else if (BLOCK_FLICKER_MAP.containsKey(originalMaterial)) {
            List<Material> possibleFlickers = BLOCK_FLICKER_MAP.get(originalMaterial);
            if (!possibleFlickers.isEmpty()) {
                flickerMaterial = possibleFlickers.get(random.nextInt(possibleFlickers.size()));

                try {


                    flickerBlockData = Bukkit.createBlockData(flickerMaterial);
                    logger.debug("Flickering block {} to {} at {}", originalMaterial, flickerMaterial, blockLocation.toVector());
                } catch (IllegalArgumentException e) {

                    logger.warn("Could not create flicker block data for {} at {}. Skipping flicker.", flickerMaterial, blockLocation.toVector(), e);
                    flickerBlockData = null;
                }
            } else {
                logger.warn("Block {} has a mapping in BLOCK_FLICKER_MAP but the list is empty!", originalMaterial);
            }
        }


        if (flickerBlockData == null) {
            logger.debug("BlockFlicker failed for {}: Could not determine valid flicker state for {}.", player.getName(), originalMaterial);
            return;
        }


        EffectUtils.sendBlockChange(player, blockLocation, flickerBlockData, plugin);
        logger.info("Triggered BlockFlicker for {}: Changed {} at {} clientside.",
                player.getName(), originalMaterial, blockLocation.toVector());


        final BlockData finalOriginalBlockData = originalBlockData;
        final Location finalBlockLocation = blockLocation;

        new BukkitRunnable() {
            @Override
            public void run() {

                if (player.isOnline()) {


                    EffectUtils.sendBlockChange(player, finalBlockLocation, finalOriginalBlockData, plugin);
                    logger.debug("BlockFlicker reverted block at {} for {}", finalBlockLocation.toVector(), player.getName());
                } else {
                    logger.debug("BlockFlicker skipping revert for {} - player offline.", player.getName());
                }
            }
        }.runTaskLater(plugin, flickerDurationTicks);
    }

    /**
     * Helper to check if a block is eligible for flickering.
     *
     * @param block The block to check.
     * @return True if eligible, false otherwise.
     */
    private boolean isEligibleFlickerBlock(Block block) {
        if (block == null || block.getType() == Material.AIR || block.getType().hasGravity()) {
            return false;
        }


        boolean isWellLit = block.getLightFromSky() > LIGHT_THRESHOLD || block.getLightFromBlocks() > LIGHT_THRESHOLD;
        if (!isWellLit) return false;


        boolean hasMapping = BLOCK_FLICKER_MAP.containsKey(block.getType()) && !BLOCK_FLICKER_MAP.get(block.getType()).isEmpty();
        boolean isLitBlock = block.getBlockData() instanceof Lightable lightable && lightable.isLit();
        boolean isTorchType = block.getType() == Material.TORCH || block.getType() == Material.WALL_TORCH;


        return hasMapping || isLitBlock || isTorchType;
    }

    /**
     * Helper method to find a random eligible block nearby.
     * Called by trigger() if player isn't looking at one.
     *
     * @param player The player.
     * @param plugin The main plugin instance (needed for logger).
     * @return An eligible Block, or null if none found.
     */
    private Block findRandomNearbyEligibleBlock(Player player, Desync plugin) {
        Logger logger = plugin.getPluginLogger();
        Location startLoc = player.getEyeLocation();
        ThreadLocalRandom random = ThreadLocalRandom.current();

        List<Block> candidates = new ArrayList<>();
        for (int x = -SEARCH_RADIUS; x <= SEARCH_RADIUS; x++) {

            for (int y = -SEARCH_RADIUS / 2; y <= SEARCH_RADIUS / 2; y++) {
                for (int z = -SEARCH_RADIUS; z <= SEARCH_RADIUS; z++) {
                    if (x == 0 && y == 0 && z == 0) continue;

                    Location checkLoc = startLoc.clone().add(x, y, z);
                    if (checkLoc.getWorld() == null || !checkLoc.getWorld().isChunkLoaded(checkLoc.getChunk())) {

                        continue;
                    }

                    try {
                        Block potential = checkLoc.getBlock();
                        if (isEligibleFlickerBlock(potential)) {
                            candidates.add(potential);
                        }
                    } catch (Exception e) {

                        logger.warn("Error finding block at relative location ({},{},{}) near {} for BlockFlicker trigger: {}",
                                x, y, z, player.getName(), e.getMessage());
                    }
                }
            }
        }

        if (candidates.isEmpty()) {
            return null;
        }


        return candidates.get(random.nextInt(candidates.size()));
    }
}