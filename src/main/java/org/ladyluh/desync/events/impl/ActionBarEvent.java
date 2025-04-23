package org.ladyluh.desync.events.impl;


import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.wrappers.WrappedChatComponent;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.ladyluh.desync.Desync;
import org.ladyluh.desync.events.PlayerDesyncEvent;
import org.slf4j.Logger;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Briefly displays a fake message in the player's action bar clientside.
 */
public class ActionBarEvent implements PlayerDesyncEvent {

    private static final String KEY = "action_bar";
    private static final String DESCRIPTION = "A strange message appears above your hotbar.";
    private static final long DEFAULT_COOLDOWN_MS = 60 * 1000L;


    private static final List<String> SPOOKY_ACTION_BAR_MESSAGES = List.of(
            "...*Cof Cof*...",
            "...Who are you?...",
            "...I don't know you...",
            "...Get out...",
            "...Leave...",
            "...Behind...",
            "...You...",
            "...I'm following...",
            "...Hehehe..."
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
     * Triggers the action bar event.
     * Selects a random spooky message and sends it as an action bar packet.
     *
     * @param player The player to send the message to.
     * @param plugin The main plugin instance.
     */
    @Override
    public void trigger(Player player, Desync plugin) {
        Logger logger = plugin.getPluginLogger();
        ProtocolManager protocolManager = plugin.getProtocolManager();
        ThreadLocalRandom random = ThreadLocalRandom.current();

        if (SPOOKY_ACTION_BAR_MESSAGES.isEmpty()) {
            logger.warn("ActionBarEvent triggered for {} but SPOOKY_ACTION_BAR_MESSAGES list is empty!", player.getName());
            return;
        }


        String rawMessage = SPOOKY_ACTION_BAR_MESSAGES.get(random.nextInt(SPOOKY_ACTION_BAR_MESSAGES.size()));

        String formattedMessage = ChatColor.GRAY + "" + ChatColor.ITALIC + rawMessage;


        logger.info("Triggering ActionBar for {}: \"{}\"", player.getName(), rawMessage);


        try {
            PacketContainer actionBarPacket = protocolManager.createPacket(PacketType.Play.Server.SET_ACTION_BAR_TEXT);


            WrappedChatComponent chatComponent = WrappedChatComponent.fromLegacyText(formattedMessage);
            actionBarPacket.getChatComponents().write(0, chatComponent);

            protocolManager.sendServerPacket(player, actionBarPacket);
            logger.debug("Sent SET_ACTION_BAR_TEXT packet to {}", player.getName());


        } catch (Exception e) {
            logger.error("Failed to send ActionBar packet to {}", player.getName(), e);
        }
    }
}