package org.ladyluh.desync.events.impl;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Openable;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.ladyluh.desync.Desync;
import org.ladyluh.desync.events.PlayerDesyncEvent;
import org.ladyluh.desync.utils.EffectUtils;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Makes a nearby block visually change state clientside as if it were interacted with
 * (e.g., a door opening/closing, a trapdoor flipping).
 * Complements GhostBlockEvent (which only plays sound).
 */
public class VisualBlockInteractEvent implements PlayerDesyncEvent {

    private static final String KEY = "visual_interact";
    private static final String DESCRIPTION = "A nearby block briefly changes state.";
    private static final long DEFAULT_COOLDOWN_MS = 60 * 1000L;

    private static final int SEARCH_RADIUS = 6;


    private static final Set<Material> INTERACTABLE_VISUAL_TYPES = Set.of(
            Material.OAK_DOOR, Material.SPRUCE_DOOR, Material.BIRCH_DOOR, Material.JUNGLE_DOOR,
            Material.ACACIA_DOOR, Material.DARK_OAK_DOOR, Material.MANGROVE_DOOR, Material.CHERRY_DOOR,
            Material.IRON_DOOR,

            Material.OAK_TRAPDOOR, Material.SPRUCE_TRAPDOOR, Material.BIRCH_TRAPDOOR, Material.JUNGLE_TRAPDOOR,
            Material.ACACIA_TRAPDOOR, Material.DARK_OAK_TRAPDOOR, Material.MANGROVE_TRAPDOOR, Material.CHERRY_TRAPDOOR,
            Material.IRON_TRAPDOOR,

            Material.OAK_FENCE_GATE, Material.SPRUCE_FENCE_GATE, Material.BIRCH_FENCE_GATE, Material.JUNGLE_FENCE_GATE,
            Material.ACACIA_FENCE_GATE, Material.DARK_OAK_FENCE_GATE, Material.MANGROVE_FENCE_GATE, Material.CHERRY_FENCE_GATE,
            Material.CRIMSON_FENCE_GATE, Material.WARPED_FENCE_GATE

    );


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
            Location startLoc = player.getLocation();
            for (int x = -SEARCH_RADIUS; x <= SEARCH_RADIUS; x++) {
                for (int y = -SEARCH_RADIUS; y <= SEARCH_RADIUS; y++) {
                    for (int z = -SEARCH_RADIUS; z <= SEARCH_RADIUS; z++) {
                        if (x == 0 && y == 0 && z == 0) continue;
                        Location checkLoc = startLoc.clone().add(x, y, z);
                        if (checkLoc.getWorld() == null || !checkLoc.getWorld().isChunkLoaded(checkLoc.getChunk()))
                            continue;

                        Block block = checkLoc.getBlock();
                        if (INTERACTABLE_VISUAL_TYPES.contains(block.getType())) {

                            if (block.getBlockData() instanceof Openable) {
                                return true;
                            }

                        }
                    }
                }
            }
        } catch (Exception e) {
            Desync.getInstance().getPluginLogger().warn("Error during VisualBlockInteract canTrigger check for {}", player.getName(), e);
            return false;
        }
        return false;
    }

    /**
     * Triggers a visual block interaction effect for the given player.
     * Finds a nearby suitable block and briefly changes its state clientside.
     *
     * @param player The player to show the effect to.
     * @param plugin The main plugin instance.
     */
    @Override
    public void trigger(Player player, Desync plugin) {
        Logger logger = plugin.getPluginLogger();
        ThreadLocalRandom random = ThreadLocalRandom.current();

        long flickerDurationTicks = 8 + random.nextInt(8);


        Block targetBlock = findRandomNearbyInteractableBlock(player, plugin);

        if (targetBlock == null) {
            logger.debug("VisualBlockInteract trigger for {}: No suitable target block found during trigger execution.", player.getName());
            return;
        }

        Material originalMaterial = targetBlock.getType();
        BlockData originalBlockData = targetBlock.getBlockData();
        Location blockLocation = targetBlock.getLocation();


        BlockData flickerBlockData = null;
        boolean stateChanged = false;


        if (originalBlockData instanceof Openable openable) {
            BlockData modifiedData = originalBlockData.clone();
            Openable modifiedOpenable = (Openable) modifiedData;
            modifiedOpenable.setOpen(!openable.isOpen());
            flickerBlockData = modifiedData;
            stateChanged = true;
            logger.debug("Toggling {} at {} for {} (New state: Open={})",
                    originalMaterial, blockLocation.toVector(), player.getName(), modifiedOpenable.isOpen());
        }


        if (!stateChanged) {
            logger.debug("VisualBlockInteract failed for {}: Could not determine valid visual state change for {}.", player.getName(), originalMaterial);
            return;
        }


        EffectUtils.sendBlockChange(player, blockLocation, flickerBlockData, plugin);
        logger.debug("Triggered VisualBlockInteract for {}: Changed {} at {} clientside.",
                player.getName(), originalMaterial, blockLocation.toVector());


        final BlockData finalOriginalBlockData = originalBlockData;
        final Location finalBlockLocation = blockLocation;

        new BukkitRunnable() {
            @Override
            public void run() {

                if (player.isOnline()) {

                    EffectUtils.sendBlockChange(player, finalBlockLocation, finalOriginalBlockData, plugin);
                    logger.debug("VisualBlockInteract reverted block at {} for {}", finalBlockLocation.toVector(), player.getName());
                } else {
                    logger.debug("VisualBlockInteract skipping revert for {} - player offline.", player.getName());
                }
            }
        }.runTaskLater(plugin, flickerDurationTicks);
    }


    /**
     * Helper method to find a random suitable interactable block nearby.
     * Called by trigger().
     *
     * @param player The player.
     * @param plugin The main plugin instance (needed for logger).
     * @return An eligible Block, or null if none found.
     */
    private Block findRandomNearbyInteractableBlock(Player player, Desync plugin) {
        Logger logger = plugin.getPluginLogger();
        Location startLoc = player.getLocation();
        ThreadLocalRandom random = ThreadLocalRandom.current();

        List<Block> candidates = new ArrayList<>();
        for (int x = -SEARCH_RADIUS; x <= SEARCH_RADIUS; x++) {
            for (int y = -SEARCH_RADIUS; y <= SEARCH_RADIUS; y++) {
                for (int z = -SEARCH_RADIUS; z <= SEARCH_RADIUS; z++) {
                    if (x == 0 && y == 0 && z == 0) continue;

                    Location checkLoc = startLoc.clone().add(x, y, z);
                    if (checkLoc.getWorld() == null || !checkLoc.getWorld().isChunkLoaded(checkLoc.getChunk()))
                        continue;


                    try {
                        Block potential = checkLoc.getBlock();

                        if (INTERACTABLE_VISUAL_TYPES.contains(potential.getType())) {
                            BlockData data = potential.getBlockData();
                            if (data instanceof Openable) {
                                candidates.add(potential);
                            }


                        }
                    } catch (Exception e) {

                        logger.warn("Error finding block at relative location ({},{},{}) near {} for VisualBlockInteract trigger: {}",
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