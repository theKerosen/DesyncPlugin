package org.ladyluh.desync.events.impl;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.events.PacketContainer;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.ladyluh.desync.Desync;
import org.ladyluh.desync.events.PlayerDesyncEvent;
import org.slf4j.Logger;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Makes the player see and/or hear visual/sound cues of taking damage without actually taking damage.
 * Can cause a screen flash and/or the player hurt sound.
 */
public class FakeDamageEvent implements PlayerDesyncEvent {

    private static final String KEY = "fake_damage";
    private static final String DESCRIPTION = "You feel like you briefly took damage.";
    private static final long DEFAULT_COOLDOWN_MS = 40 * 1000L;

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


        return player.isOnline() &&
                (player.getGameMode() == org.bukkit.GameMode.SURVIVAL || player.getGameMode() == org.bukkit.GameMode.ADVENTURE) &&
                player.getHealth() > 4.0;
    }

    /**
     * Triggers the fake damage effect.
     * Sends packets to simulate taking damageclientside.
     *
     * @param player The player to send the effect to.
     * @param plugin The main plugin instance.
     */
    @Override
    public void trigger(Player player, Desync plugin) {
        Logger logger = plugin.getPluginLogger();
        ProtocolManager protocolManager = plugin.getProtocolManager();
        ThreadLocalRandom random = ThreadLocalRandom.current();

        boolean playSound = random.nextBoolean();
        boolean playVisual = random.nextBoolean();

        if (!playSound && !playVisual) {

            if (random.nextBoolean()) playSound = true;
            else playVisual = true;
        }

        logger.info("Triggering FakeDamage for {} (Sound: {}, Visual: {})", player.getName(), playSound, playVisual);

        try {
            if (playVisual) {


                PacketContainer entityStatusPacket = protocolManager.createPacket(PacketType.Play.Server.ENTITY_STATUS);
                entityStatusPacket.getIntegers().write(0, player.getEntityId());
                entityStatusPacket.getBytes().write(0, (byte) 2);

                protocolManager.sendServerPacket(player, entityStatusPacket);
                logger.debug("Sent ENTITY_STATUS (damage) packet to {}", player.getName());
            }

            if (playSound) {


                PacketContainer soundPacket = protocolManager.createPacket(PacketType.Play.Server.NAMED_SOUND_EFFECT);
                soundPacket.getSoundEffects().write(0, org.bukkit.Sound.ENTITY_PLAYER_HURT);
                soundPacket.getSoundCategories().write(0, com.comphenix.protocol.wrappers.EnumWrappers.SoundCategory.PLAYERS);


                Location playerLoc = player.getLocation();
                soundPacket.getIntegers()
                        .write(0, (int) (playerLoc.getX() * 8.0D))
                        .write(1, (int) (playerLoc.getY() * 8.0D))
                        .write(2, (int) (playerLoc.getZ() * 8.0D));
                soundPacket.getFloat().write(0, 16.0F).write(1, 1.0F);
                soundPacket.getLongs().write(0, random.nextLong());

                protocolManager.sendServerPacket(player, soundPacket);
                logger.debug("Sent NAMED_SOUND_EFFECT (player_hurt) packet to {}", player.getName());
            }

        } catch (Exception e) {
            logger.error("Failed to send FakeDamage packets to {}", player.getName(), e);
        }
    }
}