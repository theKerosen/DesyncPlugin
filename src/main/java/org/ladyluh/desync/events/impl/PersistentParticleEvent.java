package org.ladyluh.desync.events.impl;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.wrappers.WrappedParticle;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.jetbrains.annotations.NotNull;
import org.ladyluh.desync.Desync;
import org.ladyluh.desync.events.PlayerDesyncEvent;
import org.slf4j.Logger;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Creates a short-lived, eerie particle effect that appears to originate from a specific location in the world.
 */
public class PersistentParticleEvent implements PlayerDesyncEvent {

    private static final String KEY = "persistent_particle";
    private static final String DESCRIPTION = "You see strange particles in the distance.";
    private static final long DEFAULT_COOLDOWN_MS = 50 * 1000L;

    private static final int SEARCH_RADIUS = 20;
    private static final int PARTICLE_COUNT_PER_TICK = 1;
    private static final float PARTICLE_SPREAD = 0.1f;
    private static final long EFFECT_DURATION_TICKS = 40;
    private static final int SEND_INTERVAL_TICKS = 5;

    private static final List<Particle> ELIGIBLE_PARTICLE_TYPES = List.of(
            Particle.SMOKE_NORMAL,
            Particle.CAMPFIRE_SIGNAL_SMOKE,
            Particle.SOUL,
            Particle.CRIT,
            Particle.CRIT_MAGIC,
            Particle.WHITE_ASH,
            Particle.DRAGON_BREATH

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


        return player.isOnline();

    }

    /**
     * Triggers the persistent particle event.
     * Finds a suitable location and schedules a runnable to repeatedly send particle packets.
     *
     * @param player The player to show the particles to.
     * @param plugin The main plugin instance.
     */
    @Override
    public void trigger(Player player, Desync plugin) {
        Logger logger = plugin.getPluginLogger();
        ProtocolManager protocolManager = plugin.getProtocolManager();
        ThreadLocalRandom random = ThreadLocalRandom.current();
        if (ELIGIBLE_PARTICLE_TYPES.isEmpty()) {
            logger.warn("PersistentParticle trigger for {}: ELIGIBLE_PARTICLE_TYPES list is empty!", player.getName());
            return;
        }


        Location effectLocation = findEffectLocation(player, plugin);
        if (effectLocation == null) {
            logger.debug("PersistentParticle trigger for {}: Could not find a suitable effect location.", player.getName());
            return;
        }


        Particle particleType = ELIGIBLE_PARTICLE_TYPES.get(random.nextInt(ELIGIBLE_PARTICLE_TYPES.size()));


        logger.info("Triggering PersistentParticle for {} at {} (Type: {})",
                player.getName(), effectLocation.toVector(), particleType.name());


        final Location finalEffectLocation = effectLocation;
        final Particle finalParticleType = particleType;
        final ThreadLocalRandom finalRandom = random;


        new BukkitRunnable() {
            private long ticksSent = 0;

            @Override
            public void run() {
                Player currentPlayer = Bukkit.getPlayer(player.getUniqueId());

                if (this.isCancelled() || currentPlayer == null || !currentPlayer.isOnline() ||
                        finalEffectLocation.getWorld() == null || !finalEffectLocation.getWorld().isChunkLoaded(finalEffectLocation.getChunk()) ||
                        ticksSent >= EFFECT_DURATION_TICKS) {

                    if (!this.isCancelled()) {
                        this.cancel();
                        logger.debug("PersistentParticle task cancelled for {} at {} (Reason: Duration/Player/Chunk)", currentPlayer != null ? currentPlayer.getName() : "Unknown", finalEffectLocation.toVector());
                    }
                    return;
                }


                try {

                    Location packetLocation = finalEffectLocation.clone().add(
                            finalRandom.nextDouble() * PARTICLE_SPREAD - PARTICLE_SPREAD / 2,
                            finalRandom.nextDouble() * PARTICLE_SPREAD - PARTICLE_SPREAD / 2,
                            finalRandom.nextDouble() * PARTICLE_SPREAD - PARTICLE_SPREAD / 2
                    );


                    PacketContainer particlePacket = protocolManager.createPacket(PacketType.Play.Server.WORLD_PARTICLES);


                    WrappedParticle<?> wrappedParticle = WrappedParticle.create(finalParticleType, null);


                    particlePacket.getNewParticles().write(0, wrappedParticle);
                    particlePacket.getBooleans().write(0, true);
                    particlePacket.getDoubles()
                            .write(0, packetLocation.getX())
                            .write(1, packetLocation.getY())
                            .write(2, packetLocation.getZ());

                    particlePacket.getFloat()
                            .write(0, PARTICLE_SPREAD)
                            .write(1, PARTICLE_SPREAD)
                            .write(2, PARTICLE_SPREAD)
                            .write(3, 0.0f);
                    particlePacket.getIntegers().write(0, PARTICLE_COUNT_PER_TICK);

                    protocolManager.sendServerPacket(currentPlayer, particlePacket);


                } catch (Exception e) {
                    logger.error("Failed to send PersistentParticle packet (Tick {}) to {} at {}", ticksSent, currentPlayer.getName(), finalEffectLocation.toVector(), e);

                    if (!this.isCancelled()) {
                        this.cancel();
                        logger.debug("PersistentParticle task cancelled due to send error for {} at {}", currentPlayer.getName(), finalEffectLocation.toVector());
                    }
                }

                ticksSent += SEND_INTERVAL_TICKS;
            }
        }.runTaskTimer(plugin, 0L, SEND_INTERVAL_TICKS);


    }

    /**
     * Helper to find a suitable location near the player for the particle effect.
     * Tries to find a location in low light conditions.
     */
    private Location findEffectLocation(@NotNull Player player, @NotNull Desync plugin) {
        Logger logger = plugin.getPluginLogger();
        Location playerLoc = player.getLocation();
        World world = player.getWorld();
        ThreadLocalRandom random = ThreadLocalRandom.current();

        int attempts = 0;
        int maxAttempts = 20;

        while (attempts < maxAttempts) {
            attempts++;


            double angle = random.nextDouble() * 2 * Math.PI;
            double distance = random.nextDouble() * SEARCH_RADIUS;
            int offsetX = (int) (Math.cos(angle) * distance);
            int offsetZ = (int) (Math.sin(angle) * distance);


            int offsetY = random.nextInt(11) - 5;

            Location potentialLoc = playerLoc.clone().add(offsetX, offsetY, offsetZ);


            if (potentialLoc.getY() < world.getMinHeight() || potentialLoc.getY() > world.getMaxHeight() ||
                    potentialLoc.getWorld() == null || !potentialLoc.getWorld().isChunkLoaded(potentialLoc.getChunk()))
                continue;

            try {

                Block block = potentialLoc.getBlock();
                if (!block.isPassable() && !block.isLiquid()) {

                    continue;
                }


                int lightLevel = block.getLightLevel();


                boolean isLowLight = lightLevel < 8;

                if (isLowLight) {

                    logger.debug("Found particle effect location after {} attempts: {} (Light: {})", attempts, potentialLoc.toVector(), lightLevel);
                    return potentialLoc;
                }

            } catch (Exception e) {
                logger.warn("Error checking location {} for PersistentParticle trigger: {}", potentialLoc.toVector(), e.getMessage());
            }
        }

        logger.debug("Could not find suitable PersistentParticle location for {} after {} attempts.", player.getName(), maxAttempts);

        double angle = random.nextDouble() * 2 * Math.PI;
        double distance = random.nextDouble() * SEARCH_RADIUS;
        double offsetX = Math.cos(angle) * distance;
        double offsetZ = Math.sin(angle) * distance;
        int offsetY = random.nextInt(11) - 5;
        Location fallbackLoc = playerLoc.clone().add(offsetX, offsetY, offsetZ);


        if (fallbackLoc.getWorld() != null && fallbackLoc.getWorld().isChunkLoaded(fallbackLoc.getChunk())) {
            logger.debug("Using fallback random location for PersistentParticle: {}", fallbackLoc.toVector());
            return fallbackLoc;
        } else {
            logger.debug("Fallback location for PersistentParticle not loaded.");
            return null;
        }

    }
}