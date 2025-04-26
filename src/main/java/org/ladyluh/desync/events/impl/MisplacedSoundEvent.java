package org.ladyluh.desync.events.impl;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.wrappers.EnumWrappers;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.ladyluh.desync.Desync;
import org.ladyluh.desync.events.PlayerDesyncEvent;
import org.slf4j.Logger;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Plays a sound near the player that feels out of place for the current environment/context.
 */
public class MisplacedSoundEvent implements PlayerDesyncEvent {

    private static final String KEY = "misplaced_sound";
    private static final String DESCRIPTION = "You hear a sound that doesn't belong here.";
    private static final long DEFAULT_COOLDOWN_MS = 120 * 1000L;


    private static final List<Sound> OVERWORLD_SURFACE_SOUNDS = List.of(
            Sound.ENTITY_VILLAGER_AMBIENT, Sound.ENTITY_COW_AMBIENT, Sound.ENTITY_SHEEP_AMBIENT,
            Sound.ENTITY_PIG_AMBIENT, Sound.ENTITY_CHICKEN_AMBIENT,
            Sound.ENTITY_WANDERING_TRADER_AMBIENT, Sound.ENTITY_FOX_AMBIENT,
            Sound.ENTITY_WOLF_AMBIENT
    );
    private static final List<Sound> CAVE_SOUNDS = List.of(
            Sound.AMBIENT_CAVE, Sound.ENTITY_BAT_LOOP, Sound.BLOCK_GRAVEL_STEP,
            Sound.ENTITY_SILVERFISH_AMBIENT
    );
    private static final List<Sound> UNDEAD_SOUNDS = List.of(
            Sound.ENTITY_ZOMBIE_AMBIENT, Sound.ENTITY_SKELETON_AMBIENT, Sound.ENTITY_STRAY_AMBIENT,
            Sound.ENTITY_HUSK_AMBIENT, Sound.ENTITY_ZOMBIE_VILLAGER_AMBIENT
    );
    private static final List<Sound> HOSTILE_MOB_SOUNDS = List.of(
            Sound.ENTITY_SPIDER_AMBIENT, Sound.ENTITY_CREEPER_PRIMED, Sound.ENTITY_ENDERMAN_AMBIENT

    );

    private static final List<Sound> NETHER_SOUNDS = List.of(
            Sound.AMBIENT_NETHER_WASTES_LOOP, Sound.AMBIENT_CRIMSON_FOREST_LOOP,
            Sound.AMBIENT_WARPED_FOREST_LOOP, Sound.AMBIENT_SOUL_SAND_VALLEY_LOOP,
            Sound.AMBIENT_BASALT_DELTAS_LOOP, Sound.ENTITY_GHAST_AMBIENT, Sound.ENTITY_BLAZE_AMBIENT,
            Sound.ENTITY_ZOMBIFIED_PIGLIN_AMBIENT, Sound.ENTITY_HOGLIN_AMBIENT, Sound.ENTITY_STRIDER_AMBIENT,
            Sound.ENTITY_MAGMA_CUBE_SQUISH
    );
    private static final List<Sound> END_SOUNDS = List.of(
            Sound.ENTITY_ENDERMAN_AMBIENT, Sound.ENTITY_ENDERMAN_STARE, Sound.ENTITY_ENDERMAN_TELEPORT,
            Sound.ENTITY_SHULKER_AMBIENT
    );

    private static final double MIN_DISTANCE = 10.0;
    private static final double MAX_DISTANCE = 20.0;

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


        if (!player.isOnline()) return false;
        player.getWorld();
        return true;
    }

    /**
     * Plays a misplaced ambient or distant mob sound near the player.
     * Selects a sound pool based on the player's current environment (dimension, underground/surface, night/day)
     * and picks a sound from a *different* context's pool.
     *
     * @param player The player to play the sound for.
     * @param plugin The main plugin instance.
     */
    @Override
    public void trigger(Player player, Desync plugin) {
        Logger logger = plugin.getPluginLogger();
        ProtocolManager protocolManager = plugin.getProtocolManager();
        ThreadLocalRandom random = ThreadLocalRandom.current();

        float baseVolume = 0.02f;
        float volumeVariance = 0.4f;
        float pitchVariance = 0.05f;


        Location loc = player.getLocation();
        World world = player.getWorld();

        World.Environment environment = world.getEnvironment();
        double yLevel = loc.getY();
        long time = world.getTime();
        boolean isNight = time > 13000 && time < 23000;

        double seaLevel = world.getEnvironment() == World.Environment.NORMAL ? world.getSeaLevel() : 63;

        boolean isUnderground = yLevel < seaLevel - 5 || yLevel < 50;


        List<Sound> soundPoolToPickFrom;
        String contextDescription;
        String soundPoolDescription;


        if (environment == World.Environment.NORMAL) {
            if (isUnderground) {
                contextDescription = "Overworld Underground";

                int choice = random.nextInt(3);
                if (choice == 0) {
                    soundPoolToPickFrom = OVERWORLD_SURFACE_SOUNDS;
                    soundPoolDescription = "Overworld Surface";
                } else if (choice == 1) {
                    soundPoolToPickFrom = NETHER_SOUNDS;
                    soundPoolDescription = "Nether";
                } else {
                    soundPoolToPickFrom = END_SOUNDS;
                    soundPoolDescription = "The End";
                }
            } else {
                contextDescription = "Overworld Surface";

                int choice = random.nextInt(5);
                if (choice == 0) {
                    soundPoolToPickFrom = CAVE_SOUNDS;
                    soundPoolDescription = "Cave";
                } else if (choice == 1 && isNight) {
                    soundPoolToPickFrom = UNDEAD_SOUNDS;
                    soundPoolDescription = "Undead (Night)";
                } else if (choice == 2) {
                    soundPoolToPickFrom = HOSTILE_MOB_SOUNDS;
                    soundPoolDescription = "Hostile Mobs";
                } else if (choice == 3) {
                    soundPoolToPickFrom = NETHER_SOUNDS;
                    soundPoolDescription = "Nether";
                } else {
                    soundPoolToPickFrom = END_SOUNDS;
                    soundPoolDescription = "The End";
                }


                if (!isNight && soundPoolToPickFrom == UNDEAD_SOUNDS) {
                    soundPoolToPickFrom = random.nextBoolean() ? HOSTILE_MOB_SOUNDS : CAVE_SOUNDS;
                    soundPoolDescription = random.nextBoolean() ? "Hostile Mobs" : "Cave";
                }
            }
        } else if (environment == World.Environment.NETHER) {
            contextDescription = "Nether";

            soundPoolToPickFrom = random.nextBoolean() ? OVERWORLD_SURFACE_SOUNDS : END_SOUNDS;
            soundPoolDescription = random.nextBoolean() ? "Overworld Surface" : "The End";
        } else if (environment == World.Environment.THE_END) {
            contextDescription = "The End";

            soundPoolToPickFrom = random.nextBoolean() ? OVERWORLD_SURFACE_SOUNDS : NETHER_SOUNDS;
            soundPoolDescription = random.nextBoolean() ? "Overworld Surface" : "Nether";
        } else {
            logger.warn("Unknown world environment for {}: {}", player.getName(), environment);
            return;
        }


        if (soundPoolToPickFrom.isEmpty()) {
            logger.warn("Misplaced sound pool for context mismatch '{}' was empty. Using default.", contextDescription);
            soundPoolToPickFrom = List.of(Sound.AMBIENT_CAVE);
            soundPoolDescription = "Default Cave";
        }


        Sound soundToPlay = soundPoolToPickFrom.get(random.nextInt(soundPoolToPickFrom.size()));


        float finalVolume = baseVolume * (1.0f + (random.nextFloat() * 2.0f - 1.0f) * volumeVariance);
        float pitch = 1.0f + (float) (random.nextDouble() * (pitchVariance * 2) - pitchVariance);


        SoundCategory category;
        if (UNDEAD_SOUNDS.contains(soundToPlay) || HOSTILE_MOB_SOUNDS.contains(soundToPlay))
            category = SoundCategory.HOSTILE;
        else if (NETHER_SOUNDS.contains(soundToPlay) || END_SOUNDS.contains(soundToPlay))
            category = SoundCategory.AMBIENT;
        else category = SoundCategory.AMBIENT;


        double angle = random.nextDouble() * 2 * Math.PI;
        double distance = MIN_DISTANCE + random.nextDouble() * (MAX_DISTANCE - MIN_DISTANCE);
        double offsetX = Math.cos(angle) * distance;
        double offsetZ = Math.sin(angle) * distance;
        double offsetY = random.nextDouble() * 6.0 - 3.0;
        Location soundLocation = loc.clone().add(offsetX, offsetY, offsetZ);


        logger.debug("Triggering MisplacedSound (Context: {}, Sound Pool: {}) -> Sound {} for {} at {}, vol={}, pitch={}",
                contextDescription, soundPoolDescription, soundToPlay.name(), player.getName(), soundLocation.toVector(), finalVolume, pitch);


        try {
            PacketContainer soundPacket = protocolManager.createPacket(PacketType.Play.Server.NAMED_SOUND_EFFECT);

            soundPacket.getSoundEffects().write(0, soundToPlay);
            soundPacket.getSoundCategories().write(0, EnumWrappers.SoundCategory.valueOf(category.name()));
            soundPacket.getIntegers()
                    .write(0, (int) (soundLocation.getX() * 8.0D))
                    .write(1, (int) (soundLocation.getY() * 8.0D))
                    .write(2, (int) (soundLocation.getZ() * 8.0D));
            soundPacket.getFloat()
                    .write(0, finalVolume * 16.0F)
                    .write(1, pitch);
            soundPacket.getLongs().write(0, random.nextLong());

            protocolManager.sendServerPacket(player, soundPacket);
        } catch (Exception e) {
            logger.error("Failed to send MisplacedSound packet to {}", player.getName(), e);
        }
    }
}