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

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Triggers phantom footstep sounds near the player, including single steps and sequences.
 */
public class FootstepEvent implements PlayerDesyncEvent {

    private static final String KEY = "footstep";
    private static final String DESCRIPTION = "Plays a phantom footstep sound nearby.";


    private static final List<Sound> RANDOM_STEP_SOUNDS = List.of(
            Sound.BLOCK_WOOD_STEP, Sound.BLOCK_STONE_STEP, Sound.BLOCK_GRAVEL_STEP,
            Sound.BLOCK_MUD_STEP, Sound.BLOCK_SNOW_STEP, Sound.BLOCK_METAL_STEP,
            Sound.BLOCK_LADDER_STEP, Sound.BLOCK_WOOL_STEP, Sound.BLOCK_SCAFFOLDING_STEP,
            Sound.BLOCK_NETHERRACK_STEP, Sound.BLOCK_SOUL_SAND_STEP, Sound.BLOCK_SOUL_SOIL_STEP,
            Sound.BLOCK_NYLIUM_STEP, Sound.BLOCK_BASALT_STEP, Sound.BLOCK_NETHER_BRICKS_STEP,
            Sound.BLOCK_NETHER_ORE_STEP, Sound.BLOCK_WART_BLOCK_STEP, Sound.BLOCK_GLASS_STEP,
            Sound.BLOCK_BONE_BLOCK_STEP
    );

    private static final long DEFAULT_COOLDDOWN_MS = 30 * 1000L;


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
        return DEFAULT_COOLDDOWN_MS;
    }

    @Override
    public boolean canTrigger(Player player) {


        if (!player.isOnline()) return false;
        player.getWorld();
        return true;
    }

    /**
     * Triggers a footstep event. Randomly chooses between a single step (random or cloned) or a sequence.
     *
     * @param player The player to trigger the event for.
     * @param plugin The main plugin instance.
     */
    @Override
    public void trigger(Player player, Desync plugin) {
        ThreadLocalRandom random = ThreadLocalRandom.current();


        int randChoice = random.nextInt(3);

        if (randChoice == 0) {
            playSingleFootstep(player, plugin, false);
        } else if (randChoice == 1) {
            playSingleFootstep(player, plugin, true);
        } else {
            playFootstepSequence(player, plugin);
        }
    }

    /**
     * Plays a single phantom footstep sound near the player.
     *
     * @param player           The player to play the sound for.
     * @param plugin           The main plugin instance.
     * @param clonePlayerSound If true, mimics player's step sound. If false, plays random.
     */

    private void playSingleFootstep(Player player, Desync plugin, boolean clonePlayerSound) {
        Logger logger = plugin.getPluginLogger();
        ProtocolManager protocolManager = plugin.getProtocolManager();
        ThreadLocalRandom random = ThreadLocalRandom.current();

        float volume = 0.4f;
        float pitchVariance = 0.15f;
        double maxOffset = 2.5;
        double minOffset = 1.0;
        Sound soundToPlay;
        String soundTypeName;


        if (clonePlayerSound) {
            soundTypeName = "ClonedFootstep";


            Location checkLoc = player.getLocation().subtract(0, 0.1, 0);
            Block blockBelow = checkLoc.getBlock();


            if (blockBelow.isPassable() || blockBelow.getType() == Material.AIR) {
                soundToPlay = Sound.BLOCK_STONE_STEP;
                logger.debug("Block below {} not solid ({}), defaulting clone sound to STONE.", player.getName(), blockBelow.getType());
            } else {
                try {

                    soundToPlay = blockBelow.getBlockData().getSoundGroup().getStepSound();
                    logger.debug("Cloning step sound for {} from block {}: {}", player.getName(), blockBelow.getType(), soundToPlay.name());
                } catch (Exception e) {

                    soundToPlay = Sound.BLOCK_STONE_STEP;
                    logger.warn("Error getting step sound for block type {} at {} for {}. Defaulting to STONE.",
                            blockBelow.getType(), blockBelow.getLocation().toVector(), player.getName(), e);
                }
            }
        } else {
            soundTypeName = "RandomFootstep";
            soundToPlay = RANDOM_STEP_SOUNDS.get(random.nextInt(RANDOM_STEP_SOUNDS.size()));
            logger.debug("Playing random step sound for {}: {}", player.getName(), soundToPlay.name());
        }


        Location playerLoc = player.getLocation();
        double angle = random.nextDouble() * 2 * Math.PI;
        double distance = minOffset + random.nextDouble() * (maxOffset - minOffset);
        double offsetX = Math.cos(angle) * distance;
        double offsetZ = Math.sin(angle) * distance;
        double offsetY = random.nextDouble() * 0.5 - 0.25;
        Location soundLocation = playerLoc.clone().add(offsetX, offsetY, offsetZ);

        float pitch = 1.0f + (float) (random.nextDouble() * (pitchVariance * 2) - pitchVariance);

        logger.info("Playing Single {} for {} at {}, vol={}, pitch={}",
                soundTypeName, player.getName(), soundLocation.toVector(), volume, pitch);


        try {
            PacketContainer soundPacket = protocolManager.createPacket(PacketType.Play.Server.NAMED_SOUND_EFFECT);

            soundPacket.getSoundEffects().write(0, soundToPlay);
            soundPacket.getSoundCategories().write(0, EnumWrappers.SoundCategory.PLAYERS);
            soundPacket.getIntegers()
                    .write(0, (int) (soundLocation.getX() * 8.0D))
                    .write(1, (int) (soundLocation.getY() * 8.0D))
                    .write(2, (int) (soundLocation.getZ() * 8.0D));
            soundPacket.getFloat().write(0, volume).write(1, pitch);
            soundPacket.getLongs().write(0, random.nextLong());

            protocolManager.sendServerPacket(player, soundPacket);
        } catch (Exception e) {
            logger.error("Failed to send Single Footstep packet to {}", player.getName(), e);
        }
    }

    /**
     * Plays a sequence of phantom footstep sounds near the player.
     *
     * @param player The player to hear the sequence.
     * @param plugin The main plugin instance.
     */

    private void playFootstepSequence(Player player, Desync plugin) {
        Logger logger = plugin.getPluginLogger();
        ProtocolManager protocolManager = plugin.getProtocolManager();
        ThreadLocalRandom random = ThreadLocalRandom.current();

        int numSteps = random.nextInt(2, 5);
        long baseDelayTicks = random.nextInt(6, 11);
        boolean cloneSoundForSequence = random.nextBoolean();
        float volume = 0.15f;
        float pitchVariance = 0.15f;
        double maxOffset = 3.0;
        double minOffset = 1.5;
        String sequenceTypeName = cloneSoundForSequence ? "ClonedFootstepSequence" : "RandomFootstepSequence";
        Sound initialSoundType = null;


        if (cloneSoundForSequence) {

            Location checkLoc = player.getLocation().subtract(0, 0.1, 0);
            Block blockBelow = checkLoc.getBlock();
            if (blockBelow.isPassable() || blockBelow.getType() == Material.AIR) {
                initialSoundType = Sound.BLOCK_STONE_STEP;
                logger.debug("Block below {} not solid ({}), defaulting clone sound for sequence to STONE.", player.getName(), blockBelow.getType());
            } else {
                try {
                    initialSoundType = blockBelow.getBlockData().getSoundGroup().getStepSound();
                    logger.debug("Cloning step sound for sequence for {} from block {}: {}", player.getName(), blockBelow.getType(), initialSoundType.name());
                } catch (Exception e) {
                    initialSoundType = Sound.BLOCK_STONE_STEP;
                    logger.warn("Error getting step sound for block type {} at {} for {} during sequence. Defaulting to STONE.",
                            blockBelow.getType(), blockBelow.getLocation().toVector(), player.getName(), e);
                }
            }
        } else {
            logger.debug("Starting random sound sequence for {}", player.getName());

        }


        Location playerLoc = player.getLocation();
        double angle = random.nextDouble() * 2 * Math.PI;
        double distance = minOffset + random.nextDouble() * (maxOffset - minOffset);
        double offsetX = Math.cos(angle) * distance;
        double offsetZ = Math.sin(angle) * distance;
        double offsetY = random.nextDouble() * 0.5 - 0.25;
        final Location sequenceBaseLocation = playerLoc.clone().add(offsetX, offsetY, offsetZ);

        logger.info("Triggering {} ({} steps) for {} near {}",
                sequenceTypeName, numSteps, player.getName(), sequenceBaseLocation.toVector());


        for (int i = 0; i < numSteps; i++) {

            long delay = (i * baseDelayTicks) + random.nextInt(-1, 3);
            if (delay < 0) delay = 0;


            final Sound soundForThisStep = cloneSoundForSequence
                    ? initialSoundType
                    : RANDOM_STEP_SOUNDS.get(random.nextInt(RANDOM_STEP_SOUNDS.size()));

            final float pitch = 1.0f + (float) (random.nextDouble() * (pitchVariance * 2) - pitchVariance);
            final int stepNum = i + 1;


            new org.bukkit.scheduler.BukkitRunnable() {
                @Override
                public void run() {

                    if (!player.isOnline()) {
                        logger.debug("Skipping step {}/{} for {} - player offline.", stepNum, numSteps, player.getName());
                        return;
                    }

                    logger.debug("Playing step {}/{} of {} for {} (Sound: {}, Pitch: {})",
                            stepNum, numSteps, sequenceTypeName, player.getName(), soundForThisStep.name(), pitch);


                    try {
                        PacketContainer soundPacket = protocolManager.createPacket(PacketType.Play.Server.NAMED_SOUND_EFFECT);
                        soundPacket.getSoundEffects().write(0, soundForThisStep);
                        soundPacket.getSoundCategories().write(0, EnumWrappers.SoundCategory.PLAYERS);
                        soundPacket.getIntegers()
                                .write(0, (int) (sequenceBaseLocation.getX() * 8.0D))
                                .write(1, (int) (sequenceBaseLocation.getY() * 8.0D))
                                .write(2, (int) (sequenceBaseLocation.getZ() * 8.0D));
                        soundPacket.getFloat().write(0, volume).write(1, pitch);
                        soundPacket.getLongs().write(0, random.nextLong());

                        protocolManager.sendServerPacket(player, soundPacket);
                    } catch (Exception e) {
                        logger.error("Failed to send Footstep Sequence packet (step {}/{}) to {}", stepNum, numSteps, player.getName(), e);
                    }
                }
            }.runTaskLater(plugin, delay);
        }
    }
}