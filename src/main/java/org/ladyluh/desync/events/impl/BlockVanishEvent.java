package org.ladyluh.desync.events.impl;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.wrappers.BlockPosition;
import com.comphenix.protocol.wrappers.WrappedBlockData;
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

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Makes a single solid block disappear clientside for a moment and then reappear.
 */
public class BlockVanishEvent implements PlayerDesyncEvent {

    private static final String KEY = "block_vanish";
    private static final String DESCRIPTION = "A block disappears and reappears briefly.";
    private static final long DEFAULT_COOLDOWN_MS = 30 * 1000L; // 30 seconds

    private static final int SEARCH_RADIUS = 5; // Radius to search for blocks (closer than flicker)
    private static final long VANISH_DURATION_TICKS = 15 + ThreadLocalRandom.current().nextInt(16); // Vanish for 0.75 - 1.5 seconds (15-30 ticks)

    // Block types eligible for vanishing (must be solid and non-interactive usually)
    private static final Map<Material, List<Material>> ELIGIBLE_VANISH_TYPES = new EnumMap<>(Map.ofEntries(
            Map.entry(Material.STONE, List.of()), // Empty lists as values are not used
            Map.entry(Material.GRASS_BLOCK, List.of()),
            Map.entry(Material.DIRT, List.of()),
            Map.entry(Material.COBBLESTONE, List.of()),
            Map.entry(Material.OAK_LOG, List.of()),
            Map.entry(Material.OAK_PLANKS, List.of()),
            Map.entry(Material.BRICK, List.of()),
            Map.entry(Material.STONE_BRICKS, List.of()),
            Map.entry(Material.SAND, List.of()),
            Map.entry(Material.GRAVEL, List.of()),
            Map.entry(Material.IRON_ORE, List.of()),
            Map.entry(Material.COAL_ORE, List.of()),
            Map.entry(Material.DIAMOND_ORE, List.of()),
            Map.entry(Material.GOLD_ORE, List.of()),
            Map.entry(Material.REDSTONE_ORE, List.of()),
            Map.entry(Material.EMERALD_ORE, List.of()),
            Map.entry(Material.LAPIS_ORE, List.of()),
            Map.entry(Material.NETHERRACK, List.of()),
            Map.entry(Material.END_STONE, List.of()),
            Map.entry(Material.OBSIDIAN, List.of()),
            Map.entry(Material.COBBLED_DEEPSLATE, List.of()),
            Map.entry(Material.DEEPSLATE, List.of())
            // Add more relevant solid block types...
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
        // Requires player to be online and a suitable solid block to be nearby.
        // Check nearby blocks randomly within the radius.
        Location startLoc = player.getLocation();
        ThreadLocalRandom random = ThreadLocalRandom.current();

        int sampleSize = 10; // Check a few random blocks
        try {
            for(int i = 0; i < sampleSize; i++) {
                int x = random.nextInt(-SEARCH_RADIUS, SEARCH_RADIUS + 1);
                int y = random.nextInt(-SEARCH_RADIUS, SEARCH_RADIUS + 1);
                int z = random.nextInt(-SEARCH_RADIUS, SEARCH_RADIUS + 1);
                if (x == 0 && y == 0 && z == 0) continue;

                Location checkLoc = startLoc.clone().add(x, y, z);
                if (!checkLoc.getWorld().isChunkLoaded(checkLoc.getChunk())) continue;

                Block block = checkLoc.getBlock();
                if (isEligibleVanishBlock(block)) {
                    return true; // Found an eligible block
                }
            }
        } catch (Exception e) {
            Desync.getInstance().getPluginLogger().warn("Error during BlockVanish canTrigger check for {}", player.getName(), e);
            return false;
        }
        return false; // No eligible block found
    }

    /**
     * Helper to check if a block is eligible for vanishing.
     * @param block The block to check.
     * @return True if eligible, false otherwise.
     */
    private boolean isEligibleVanishBlock(Block block) {
        // Must be a block, not air/liquid/gravity/interactive, and in our list.
        if (block == null || !block.getType().isBlock() || !block.getType().isSolid() || block.getType().hasGravity() || block.isLiquid() ||
                block.getBlockData() instanceof Openable || block.getType().name().contains("_CHEST") || block.getType() == Material.BARREL || block.getType() == Material.LEVER || block.getType().name().contains("_BUTTON")) {
            return false; // Filter out non-blocks, non-solids, gravity, liquids, and interactive blocks
        }
        // Check if it's in our defined list of eligible types
        return ELIGIBLE_VANISH_TYPES.containsKey(block.getType());
    }

    /**
     * Helper method to find a random eligible block nearby.
     * Called by trigger().
     * @param player The player.
     * @param plugin The main plugin instance (needed for logger).
     * @return An eligible Block, or null if none found.
     */
    private Block findRandomNearbyEligibleBlock(Player player, Desync plugin) {
        Logger logger = plugin.getPluginLogger();
        Location startLoc = player.getLocation();
        ThreadLocalRandom random = ThreadLocalRandom.current();

        List<Block> candidates = new ArrayList<>();
        for (int x = -SEARCH_RADIUS; x <= SEARCH_RADIUS; x++) {
            for (int y = -SEARCH_RADIUS; y <= SEARCH_RADIUS; y++) {
                for (int z = -SEARCH_RADIUS; z <= SEARCH_RADIUS; z++) {
                    if (x == 0 && y == 0 && z == 0) continue;

                    Location checkLoc = startLoc.clone().add(x, y, z);
                    if (checkLoc.getWorld() == null || !checkLoc.getWorld().isChunkLoaded(checkLoc.getChunk())) continue;

                    try {
                        Block potential = checkLoc.getBlock();
                        if (isEligibleVanishBlock(potential)) {
                            candidates.add(potential);
                        }
                    } catch (Exception e) {
                        logger.warn("Error finding block at relative location ({},{},{}) near {} for BlockVanish trigger: {}",
                                x, y, z, player.getName(), e.getMessage());
                    }
                }
            }
        }

        if (candidates.isEmpty()) {
            return null; // No eligible block candidates found
        }

        // Pick one randomly from the list of candidates
        return candidates.get(random.nextInt(candidates.size()));
    }


    /**
     * Triggers the block vanish event.
     * Finds a nearby solid block, makes it clientside air, then reverts it.
     * @param player The player to show the vanishing block to.
     * @param plugin The main plugin instance.
     */
    @Override
    public void trigger(Player player, Desync plugin) {
        Logger logger = plugin.getPluginLogger(); // Use passed plugin
        ProtocolManager protocolManager = plugin.getProtocolManager(); // Use passed plugin

        // Find a nearby eligible block to make vanish
        Block targetBlock = findRandomNearbyEligibleBlock(player, plugin);

        if (targetBlock == null) {
            logger.debug("BlockVanish trigger for {}: No suitable target block found during trigger execution.", player.getName());
            return; // Should not happen if canTrigger was true
        }

        Location blockLocation = targetBlock.getLocation();
        BlockData originalBlockData = targetBlock.getBlockData();


        logger.debug("Triggering BlockVanish for {} at {}", player.getName(), blockLocation.toVector());

        // --- Send vanish packet (change to air clientside) ---
        try {
            // Send BLOCK_CHANGE packet to make the block appear as air
            PacketContainer vanishPacket = protocolManager.createPacket(PacketType.Play.Server.BLOCK_CHANGE);
            vanishPacket.getBlockPositionModifier().write(0, new BlockPosition(blockLocation.getBlockX(), blockLocation.getBlockY(), blockLocation.getBlockZ()));
            vanishPacket.getBlockData().write(0, WrappedBlockData.createData(Material.AIR.createBlockData())); // Change to air

            protocolManager.sendServerPacket(player, vanishPacket);
            logger.debug("Sent BLOCK_CHANGE packet (Vanish) to {}", player.getName());


            // --- Schedule revert task (change back to original) ---
            final Location finalBlockLocation = blockLocation;
            final BlockData finalOriginalBlockData = originalBlockData; // Final original block data

            new BukkitRunnable() {
                @Override
                public void run() {
                    if (!player.isOnline()) {
                        logger.debug("BlockVanish skipping revert for {} - player offline.", player.getName());
                        return;
                    }

                    // Send BLOCK_CHANGE packet to revert to original block
                    try {
                        // Defensive check: Is the location still loaded?
                        if (finalBlockLocation.getWorld() == null || !finalBlockLocation.getWorld().isChunkLoaded(finalBlockLocation.getChunk())) {
                            logger.debug("Skipping revert for block at {} - chunk not loaded.", finalBlockLocation.toVector());
                            return;
                        }

                        EffectUtils.sendBlockChange(player, finalBlockLocation, finalOriginalBlockData, plugin); // Use utility method
                        logger.debug("BlockVanish reverted block at {} clientside.", finalBlockLocation.toVector());

                    } catch (Exception e) {
                        logger.error("Failed to send BlockVanish revert packet for {} to {}", finalBlockLocation.toVector(), player.getName(), e);
                    }
                }
            }.runTaskLater(plugin, VANISH_DURATION_TICKS); // Schedule revert after duration


        } catch (Exception e) {
            logger.error("Failed to send initial BlockVanish packet to {}", player.getName(), e);
            // If initial send fails, no revert is needed clientside.
        }
    }
}