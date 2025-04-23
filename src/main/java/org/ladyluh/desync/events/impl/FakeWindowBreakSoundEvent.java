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

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Plays a fake glass breaking sound near the player, ideally near a glass block.
 */
public class FakeWindowBreakSoundEvent implements PlayerDesyncEvent {

    private static final String KEY = "fake_window_break";
    private static final String DESCRIPTION = "You hear a window break nearby.";
    private static final long DEFAULT_COOLDOWN_MS = 50 * 1000L;

    private static final int SEARCH_RADIUS = 10;


    private static final Set<Material> GLASS_TYPES = Set.of(
            Material.GLASS, Material.GLASS_PANE,
            Material.TINTED_GLASS,
            Material.BLACK_STAINED_GLASS, Material.BLUE_STAINED_GLASS, Material.BROWN_STAINED_GLASS,
            Material.CYAN_STAINED_GLASS, Material.GRAY_STAINED_GLASS, Material.GREEN_STAINED_GLASS,
            Material.LIGHT_BLUE_STAINED_GLASS, Material.LIGHT_GRAY_STAINED_GLASS, Material.LIME_STAINED_GLASS,
            Material.MAGENTA_STAINED_GLASS, Material.ORANGE_STAINED_GLASS, Material.PINK_STAINED_GLASS,
            Material.PURPLE_STAINED_GLASS, Material.RED_STAINED_GLASS, Material.WHITE_STAINED_GLASS,
            Material.YELLOW_STAINED_GLASS,
            Material.BLACK_STAINED_GLASS_PANE, Material.BLUE_STAINED_GLASS_PANE, Material.BROWN_STAINED_GLASS_PANE,
            Material.CYAN_STAINED_GLASS_PANE, Material.GRAY_STAINED_GLASS_PANE, Material.GREEN_STAINED_GLASS_PANE,
            Material.LIGHT_BLUE_STAINED_GLASS_PANE, Material.LIGHT_GRAY_STAINED_GLASS_PANE, Material.LIME_STAINED_GLASS_PANE,
            Material.MAGENTA_STAINED_GLASS_PANE, Material.ORANGE_STAINED_GLASS_PANE, Material.PINK_STAINED_GLASS_PANE,
            Material.PURPLE_STAINED_GLASS_PANE, Material.RED_STAINED_GLASS_PANE, Material.WHITE_STAINED_GLASS_PANE,
            Material.YELLOW_STAINED_GLASS_PANE
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

        return player.isOnline() && hasNearbyGlass(player);
    }

    /**
     * Helper to check if a glass/pane block is nearby for sound placement.
     *
     * @param player The player.
     * @return True if a glass/pane block is found nearby, false otherwise.
     */
    private boolean hasNearbyGlass(Player player) {
        Location startLoc = player.getLocation();
        try {
            for (int x = -SEARCH_RADIUS; x <= SEARCH_RADIUS; x++) {
                for (int y = -SEARCH_RADIUS; y <= SEARCH_RADIUS; y++) {
                    for (int z = -SEARCH_RADIUS; z <= SEARCH_RADIUS; z++) {
                        if (x == 0 && y == 0 && z == 0) continue;

                        Location checkLoc = startLoc.clone().add(x, y, z);
                        if (checkLoc.getWorld() == null || !checkLoc.getWorld().isChunkLoaded(checkLoc.getChunk()))
                            continue;

                        Block block = checkLoc.getBlock();
                        if (GLASS_TYPES.contains(block.getType())) {
                            return true;
                        }
                    }
                }
            }
        } catch (Exception e) {
            Desync.getInstance().getPluginLogger().warn("Error during FakeWindowBreakSound canTrigger check for {}", player.getName(), e);
            return false;
        }
        return false;
    }

    /**
     * Helper method to find a random glass block nearby.
     *
     * @param player The player.
     * @param plugin The main plugin instance (needed for logger).
     * @return A glass Block, or null if none found.
     */
    private Block findRandomNearbyGlass(Player player, Desync plugin) {
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
                        if (GLASS_TYPES.contains(potential.getType())) {
                            candidates.add(potential);
                        }
                    } catch (Exception e) {
                        logger.warn("Error finding block at relative location ({},{},{}) near {} for FakeWindowBreakSound trigger: {}",
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


    /**
     * Triggers the fake window break sound event.
     * Finds a nearby glass block location and plays a glass break sound.
     *
     * @param player The player to play the sound for.
     * @param plugin The main plugin instance.
     */
    @Override
    public void trigger(Player player, Desync plugin) {
        Logger logger = plugin.getPluginLogger();
        ProtocolManager protocolManager = plugin.getProtocolManager();
        ThreadLocalRandom random = ThreadLocalRandom.current();

        float volume = 0.5f;
        float pitchVariance = 0.1f;


        Block targetBlock = findRandomNearbyGlass(player, plugin);

        Location soundLocation;
        if (targetBlock != null) {

            soundLocation = targetBlock.getLocation().add(0.5, 0.5, 0.5);
            logger.debug("Playing FakeWindowBreakSound near glass block at {}", targetBlock.getLocation().toVector());
        } else {


            double angle = random.nextDouble() * 2 * Math.PI;
            double distance = random.nextDouble() * 3.0 + 2.0;
            double offsetX = Math.cos(angle) * distance;
            double offsetZ = Math.sin(angle) * distance;
            double offsetY = random.nextDouble() - 0.5;
            soundLocation = player.getLocation().clone().add(offsetX, offsetY, offsetZ);
            logger.debug("Playing FakeWindowBreakSound at random location {} (no glass found)", soundLocation.toVector());
        }


        Sound soundToPlay = Sound.BLOCK_GLASS_BREAK;

        float pitch = 1.0f + (float) (random.nextDouble() * (pitchVariance * 2) - pitchVariance);

        logger.info("Triggering FakeWindowBreakSound for {} at {}",
                player.getName(), soundLocation.toVector());


        try {
            PacketContainer soundPacket = protocolManager.createPacket(PacketType.Play.Server.NAMED_SOUND_EFFECT);
            soundPacket.getSoundEffects().write(0, soundToPlay);
            soundPacket.getSoundCategories().write(0, EnumWrappers.SoundCategory.BLOCKS);
            soundPacket.getIntegers()
                    .write(0, (int) (soundLocation.getX() * 8.0D))
                    .write(1, (int) (soundLocation.getY() * 8.0D))
                    .write(2, (int) (soundLocation.getZ() * 8.0D));
            soundPacket.getFloat().write(0, volume * 16.0F).write(1, pitch);
            soundPacket.getLongs().write(0, random.nextLong());

            protocolManager.sendServerPacket(player, soundPacket);
        } catch (Exception e) {
            logger.error("Failed to send FakeWindowBreakSound packet to {}", player.getName(), e);
        }
    }
}