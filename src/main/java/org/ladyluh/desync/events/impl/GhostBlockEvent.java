package org.ladyluh.desync.events.impl;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.wrappers.EnumWrappers;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.ladyluh.desync.Desync;
import org.ladyluh.desync.events.PlayerDesyncEvent;
import org.slf4j.Logger;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Plays a fake block interaction sound near the player if relevant block is nearby.
 */
public class GhostBlockEvent implements PlayerDesyncEvent {

    private static final String KEY = "ghost_block";
    private static final String DESCRIPTION = "You hear a nearby block being interacted with.";
    private static final long DEFAULT_COOLDOWN_MS = 35 * 1000L;

    private static final int SEARCH_RADIUS = 6;


    private static final Map<Material, Sound> BLOCK_INTERACTION_SOUNDS_MAP = new EnumMap<>(Map.ofEntries(
            Map.entry(Material.OAK_DOOR, Sound.BLOCK_WOODEN_DOOR_OPEN),
            Map.entry(Material.SPRUCE_DOOR, Sound.BLOCK_WOODEN_DOOR_OPEN),
            Map.entry(Material.BIRCH_DOOR, Sound.BLOCK_WOODEN_DOOR_OPEN),
            Map.entry(Material.JUNGLE_DOOR, Sound.BLOCK_WOODEN_DOOR_OPEN),
            Map.entry(Material.ACACIA_DOOR, Sound.BLOCK_WOODEN_DOOR_OPEN),
            Map.entry(Material.DARK_OAK_DOOR, Sound.BLOCK_WOODEN_DOOR_OPEN),
            Map.entry(Material.MANGROVE_DOOR, Sound.BLOCK_WOODEN_DOOR_OPEN),
            Map.entry(Material.CHERRY_DOOR, Sound.BLOCK_WOODEN_DOOR_OPEN),
            Map.entry(Material.IRON_DOOR, Sound.BLOCK_IRON_DOOR_OPEN),

            Map.entry(Material.OAK_FENCE_GATE, Sound.BLOCK_FENCE_GATE_OPEN),
            Map.entry(Material.SPRUCE_FENCE_GATE, Sound.BLOCK_FENCE_GATE_OPEN),
            Map.entry(Material.ACACIA_FENCE_GATE, Sound.BLOCK_FENCE_GATE_OPEN),
            Map.entry(Material.BIRCH_FENCE_GATE, Sound.BLOCK_FENCE_GATE_OPEN),
            Map.entry(Material.JUNGLE_FENCE_GATE, Sound.BLOCK_FENCE_GATE_OPEN),
            Map.entry(Material.BAMBOO_FENCE_GATE, Sound.BLOCK_FENCE_GATE_OPEN),
            Map.entry(Material.DARK_OAK_FENCE_GATE, Sound.BLOCK_FENCE_GATE_OPEN),
            Map.entry(Material.CRIMSON_FENCE_GATE, Sound.BLOCK_FENCE_GATE_OPEN),
            Map.entry(Material.WARPED_FENCE_GATE, Sound.BLOCK_WOODEN_TRAPDOOR_OPEN),

            Map.entry(Material.OAK_TRAPDOOR, Sound.BLOCK_WOODEN_TRAPDOOR_OPEN),
            Map.entry(Material.SPRUCE_TRAPDOOR, Sound.BLOCK_WOODEN_TRAPDOOR_OPEN),
            Map.entry(Material.ACACIA_TRAPDOOR, Sound.BLOCK_WOODEN_TRAPDOOR_OPEN),
            Map.entry(Material.BIRCH_TRAPDOOR, Sound.BLOCK_WOODEN_TRAPDOOR_OPEN),
            Map.entry(Material.DARK_OAK_TRAPDOOR, Sound.BLOCK_WOODEN_TRAPDOOR_OPEN),
            Map.entry(Material.CRIMSON_TRAPDOOR, Sound.BLOCK_WOODEN_TRAPDOOR_OPEN),
            Map.entry(Material.MANGROVE_TRAPDOOR, Sound.BLOCK_WOODEN_TRAPDOOR_OPEN),
            Map.entry(Material.WARPED_TRAPDOOR, Sound.BLOCK_WOODEN_TRAPDOOR_OPEN),
            Map.entry(Material.IRON_TRAPDOOR, Sound.BLOCK_IRON_TRAPDOOR_OPEN),

            Map.entry(Material.CHEST, Sound.BLOCK_CHEST_OPEN),
            Map.entry(Material.TRAPPED_CHEST, Sound.BLOCK_CHEST_OPEN),
            Map.entry(Material.ENDER_CHEST, Sound.BLOCK_ENDER_CHEST_OPEN),
            Map.entry(Material.BARREL, Sound.BLOCK_BARREL_OPEN),
            Map.entry(Material.LEVER, Sound.BLOCK_LEVER_CLICK),
            Map.entry(Material.STONE_BUTTON, Sound.BLOCK_STONE_BUTTON_CLICK_ON),
            Map.entry(Material.OAK_BUTTON, Sound.BLOCK_WOODEN_BUTTON_CLICK_ON),
            Map.entry(Material.SPRUCE_BUTTON, Sound.BLOCK_WOODEN_BUTTON_CLICK_ON),
            Map.entry(Material.BIRCH_BUTTON, Sound.BLOCK_WOODEN_BUTTON_CLICK_ON),
            Map.entry(Material.JUNGLE_BUTTON, Sound.BLOCK_WOODEN_BUTTON_CLICK_ON),
            Map.entry(Material.ACACIA_BUTTON, Sound.BLOCK_WOODEN_BUTTON_CLICK_ON),
            Map.entry(Material.DARK_OAK_BUTTON, Sound.BLOCK_WOODEN_BUTTON_CLICK_ON),
            Map.entry(Material.MANGROVE_BUTTON, Sound.BLOCK_WOODEN_BUTTON_CLICK_ON),
            Map.entry(Material.CHERRY_BUTTON, Sound.BLOCK_WOODEN_BUTTON_CLICK_ON),
            Map.entry(Material.CRIMSON_BUTTON, Sound.BLOCK_WOODEN_BUTTON_CLICK_ON),
            Map.entry(Material.WARPED_BUTTON, Sound.BLOCK_WOODEN_BUTTON_CLICK_ON),
            Map.entry(Material.POLISHED_BLACKSTONE_BUTTON, Sound.BLOCK_STONE_BUTTON_CLICK_ON)
    ));


    private static final Set<Material> INTERACTABLE_TYPES = BLOCK_INTERACTION_SOUNDS_MAP.keySet();


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

        return hasNearbyInteractableBlock(player);
    }

    /**
     * Plays a fake block interaction sound near the player.
     * Finds a nearby interactable block and plays an open/close or click sound.
     *
     * @param player The player to play the sound for.
     * @param plugin The main plugin instance.
     */
    @Override
    public void trigger(Player player, Desync plugin) {
        Logger logger = plugin.getPluginLogger();
        ProtocolManager protocolManager = plugin.getProtocolManager();
        ThreadLocalRandom random = ThreadLocalRandom.current();

        float volume = 0.6f;
        float pitchVariance = 0.1f;


        Block targetBlock = findNearbyInteractableBlock(player);

        if (targetBlock == null) {

            logger.debug("GhostBlockEvent requested for {}, but no suitable block found.", player.getName());
            return;
        }

        Material blockMaterial = targetBlock.getType();
        Sound soundToPlay = BLOCK_INTERACTION_SOUNDS_MAP.get(blockMaterial);

        if (soundToPlay == null) {
            logger.warn("Found interactable block {} at {} for {} but sound mapping is missing!", blockMaterial, targetBlock.getLocation().toVector(), player.getName());
            return;
        }


        if (soundToPlay == Sound.BLOCK_WOODEN_DOOR_OPEN)
            soundToPlay = random.nextBoolean() ? Sound.BLOCK_WOODEN_DOOR_OPEN : Sound.BLOCK_WOODEN_DOOR_CLOSE;
        else if (soundToPlay == Sound.BLOCK_IRON_DOOR_OPEN)
            soundToPlay = random.nextBoolean() ? Sound.BLOCK_IRON_DOOR_OPEN : Sound.BLOCK_IRON_DOOR_CLOSE;
        else if (soundToPlay == Sound.BLOCK_FENCE_GATE_OPEN)
            soundToPlay = random.nextBoolean() ? Sound.BLOCK_FENCE_GATE_OPEN : Sound.BLOCK_FENCE_GATE_CLOSE;
        else if (soundToPlay == Sound.BLOCK_WOODEN_TRAPDOOR_OPEN)
            soundToPlay = random.nextBoolean() ? Sound.BLOCK_WOODEN_TRAPDOOR_OPEN : Sound.BLOCK_WOODEN_TRAPDOOR_CLOSE;
        else if (soundToPlay == Sound.BLOCK_IRON_TRAPDOOR_OPEN)
            soundToPlay = random.nextBoolean() ? Sound.BLOCK_IRON_TRAPDOOR_OPEN : Sound.BLOCK_IRON_TRAPDOOR_CLOSE;
        else if (soundToPlay == Sound.BLOCK_CHEST_OPEN)
            soundToPlay = random.nextBoolean() ? Sound.BLOCK_CHEST_OPEN : Sound.BLOCK_CHEST_CLOSE;
        else if (soundToPlay == Sound.BLOCK_STONE_BUTTON_CLICK_ON)
            soundToPlay = random.nextBoolean() ? Sound.BLOCK_STONE_BUTTON_CLICK_ON : Sound.BLOCK_STONE_BUTTON_CLICK_OFF;
        else if (soundToPlay == Sound.BLOCK_WOODEN_BUTTON_CLICK_ON)
            soundToPlay = random.nextBoolean() ? Sound.BLOCK_WOODEN_BUTTON_CLICK_ON : Sound.BLOCK_WOODEN_BUTTON_CLICK_OFF;


        Location soundLocation = targetBlock.getLocation().add(0.5, 0.5, 0.5);

        float pitch = 1.0f + (float) (random.nextDouble() * (pitchVariance * 2) - pitchVariance);


        logger.info("Triggering GhostBlockInteraction for {}: Sound {} near block {} at {}",
                player.getName(), soundToPlay.name(), blockMaterial.name(), targetBlock.getLocation().toVector());


        try {
            PacketContainer soundPacket = protocolManager.createPacket(PacketType.Play.Server.NAMED_SOUND_EFFECT);
            soundPacket.getSoundEffects().write(0, soundToPlay);
            soundPacket.getSoundCategories().write(0, EnumWrappers.SoundCategory.BLOCKS);
            soundPacket.getIntegers()
                    .write(0, (int) (soundLocation.getX() * 8.0D))
                    .write(1, (int) (soundLocation.getY() * 8.0D))
                    .write(2, (int) (soundLocation.getZ() * 8.0D));
            soundPacket.getFloat().write(0, volume).write(1, pitch);
            soundPacket.getLongs().write(0, random.nextLong());

            protocolManager.sendServerPacket(player, soundPacket);
        } catch (Exception e) {
            logger.error("Failed to send GhostBlockInteraction packet to {}", player.getName(), e);
        }
    }

    /**
     * Helper method to find a nearby interactable block within the search radius.
     * Logic moved from EventScheduler pre-check.
     *
     * @param player The player.
     * @return A Block that is interactable and nearby, or null if none found.
     */
    private boolean hasNearbyInteractableBlock(Player player) {
        Location startLoc = player.getLocation();

        for (int x = -SEARCH_RADIUS; x <= SEARCH_RADIUS; x++) {
            for (int y = -SEARCH_RADIUS; y <= SEARCH_RADIUS; y++) {
                for (int z = -SEARCH_RADIUS; z <= SEARCH_RADIUS; z++) {
                    if (x == 0 && y == 0 && z == 0) continue;

                    try {
                        Block block = startLoc.clone().add(x, y, z).getBlock();
                        if (INTERACTABLE_TYPES.contains(block.getType())) {


                            return true;
                        }
                    } catch (Exception e) {

                        Desync.getInstance().getPluginLogger().warn("Error checking block at relative location ({},{},{}) near {} for GhostBlockEvent pre-check: {}",
                                x, y, z, player.getName(), e.getMessage());

                    }
                }
            }
        }
        return false;
    }

    /**
     * Helper method to actually retrieve the Block object.
     * Called by trigger() after canTrigger() passes.
     *
     * @param player The player.
     * @return The found Block, or null if none found (should match hasNearbyInteractableBlock logic).
     */
    private Block findNearbyInteractableBlock(Player player) {
        Location startLoc = player.getLocation();
        ThreadLocalRandom random = ThreadLocalRandom.current();


        List<Block> candidates = new java.util.ArrayList<>();
        for (int x = -SEARCH_RADIUS; x <= SEARCH_RADIUS; x++) {
            for (int y = -SEARCH_RADIUS; y <= SEARCH_RADIUS; y++) {
                for (int z = -SEARCH_RADIUS; z <= SEARCH_RADIUS; z++) {
                    if (x == 0 && y == 0 && z == 0) continue;

                    try {
                        Block block = startLoc.clone().add(x, y, z).getBlock();
                        if (INTERACTABLE_TYPES.contains(block.getType())) {
                            candidates.add(block);
                        }
                    } catch (Exception e) {
                        Desync.getInstance().getPluginLogger().warn("Error finding block at relative location ({},{},{}) near {} for GhostBlockEvent trigger: {}",
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