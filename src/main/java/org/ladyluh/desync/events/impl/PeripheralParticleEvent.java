package org.ladyluh.desync.events.impl;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.wrappers.WrappedParticle;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;
import org.ladyluh.desync.Desync;
import org.ladyluh.desync.events.PlayerDesyncEvent;
import org.slf4j.Logger;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Triggers a small particle effect in the player's peripheral vision.
 */
public class PeripheralParticleEvent implements PlayerDesyncEvent {

    private static final String KEY = "peripheral_particle";
    private static final String DESCRIPTION = "A small particle effect appears in your peripheral vision.";
    private static final long DEFAULT_COOLDOWN_MS = 90 * 1000L;

    private static final double BASE_OFFSET_DISTANCE = 3.0;
    private static final double PERIPHERAL_ANGLE_MIN = 45.0;
    private static final double PERIPHERAL_ANGLE_MAX = 75.0;
    private static final double VERTICAL_ANGLE_VARIANCE = 20.0;


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

    @Override
    public void trigger(Player player, Desync plugin) {
        Logger logger = plugin.getPluginLogger();
        ProtocolManager protocolManager = plugin.getProtocolManager();
        ThreadLocalRandom random = ThreadLocalRandom.current();


        Particle particleType = Particle.SMOKE_NORMAL;
        int particleCount = random.nextInt(1, 4);
        float spreadMultiplier = 0.1f;
        float particleData = 0.0f;

        Location eyeLocation = player.getEyeLocation();
        Vector lookDirection = eyeLocation.getDirection().normalize();


        double horizontalAngleOffset = PERIPHERAL_ANGLE_MIN + random.nextDouble() * (PERIPHERAL_ANGLE_MAX - PERIPHERAL_ANGLE_MIN);
        if (random.nextBoolean()) {
            horizontalAngleOffset *= -1.0;
        }


        double verticalAngleOffset = (random.nextDouble() * 2.0 - 1.0) * VERTICAL_ANGLE_VARIANCE;


        Vector peripheralDirection = lookDirection.clone();

        peripheralDirection.rotateAroundY(Math.toRadians(horizontalAngleOffset));


        Vector perpendicularAxis = peripheralDirection.clone().crossProduct(new Vector(0, 1, 0)).normalize();
        if (perpendicularAxis.lengthSquared() > 0.1) {
            peripheralDirection.rotateAroundAxis(perpendicularAxis, Math.toRadians(verticalAngleOffset));
        }


        if (peripheralDirection.getY() < -0.85) {
            peripheralDirection.setY(-0.85).normalize();
        } else if (peripheralDirection.getY() > 0.85) {
            peripheralDirection.setY(0.85).normalize();
        }


        Location particleLocation = eyeLocation.clone().add(peripheralDirection.multiply(BASE_OFFSET_DISTANCE));


        if (!particleLocation.getBlock().isPassable()) {
            logger.debug("PeripheralParticle trigger for {}: Skipping, target location is inside a block.", player.getName());
            return;
        }

        logger.debug("Triggering PeripheralParticles ({}, count {}) for {} at offset location {}",
                particleType.name(), particleCount, player.getName(), particleLocation.toVector());


        try {
            PacketContainer particlePacket = protocolManager.createPacket(PacketType.Play.Server.WORLD_PARTICLES);


            WrappedParticle<?> wrappedParticle = WrappedParticle.create(particleType, null);

            particlePacket.getNewParticles().write(0, wrappedParticle);

            particlePacket.getBooleans().write(0, true);
            particlePacket.getDoubles()
                    .write(0, particleLocation.getX())
                    .write(1, particleLocation.getY())
                    .write(2, particleLocation.getZ());

            particlePacket.getFloat()
                    .write(0, spreadMultiplier)
                    .write(1, spreadMultiplier)
                    .write(2, spreadMultiplier)
                    .write(3, particleData);
            particlePacket.getIntegers().write(0, particleCount);

            protocolManager.sendServerPacket(player, particlePacket);

        } catch (Exception e) {
            logger.error("Failed to send PeripheralParticle packet to {}", player.getName(), e);
        }
    }
}