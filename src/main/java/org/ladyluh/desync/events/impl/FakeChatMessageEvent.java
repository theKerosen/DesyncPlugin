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
 * Sends a fake, spooky chat message to the player clientside.
 */
public class FakeChatMessageEvent implements PlayerDesyncEvent {

    private static final long DEFAULT_COOLDDOWN_MS = 30 * 1000L;
    private static final String KEY = "fake_chat";
    private static final String DESCRIPTION = "You see a strange message in chat.";

    
    private static final List<String> SPOOKY_MESSAGES = List.of(
            "I feel tired.",
            "Something is watching me...",
            "What was that?",
            "What was that noise?",
            "Huh..?",
            "It's getting colder...",
            "Footsteps..?",
            "It is reeking of death...",
            "Keep going"
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
        return DEFAULT_COOLDDOWN_MS;
    }

    @Override
    public boolean canTrigger(Player player) {
        
        return player.isOnline();
    }

    /**
     * Triggers the fake chat message event.
     * Selects a random spooky message and sends it as a fake chat packet to the player.
     * @param player The player to send the message to.
     * @param plugin The main plugin instance.
     */
    @Override
    public void trigger(Player player, Desync plugin) {
        Logger logger = plugin.getPluginLogger(); 
        ProtocolManager protocolManager = plugin.getProtocolManager(); 
        ThreadLocalRandom random = ThreadLocalRandom.current();

        if (SPOOKY_MESSAGES.isEmpty()) {
            logger.warn("FakeChatMessageEvent triggered for {} but SPOOKY_MESSAGES list is empty!", player.getName());
            return; 
        }

        
        String rawMessage = SPOOKY_MESSAGES.get(random.nextInt(SPOOKY_MESSAGES.size()));
        
        String formattedMessage = rawMessage.contains(ChatColor.COLOR_CHAR + "") ? rawMessage : ChatColor.GRAY + "" + ChatColor.ITALIC + rawMessage;


        logger.info("Triggering FakeChat for {}: \"{}\"", player.getName(), rawMessage);

        
        try {
            PacketContainer chatPacket = protocolManager.createPacket(PacketType.Play.Server.SYSTEM_CHAT);
            WrappedChatComponent chatComponent = WrappedChatComponent.fromLegacyText(formattedMessage);
            chatPacket.getChatComponents().write(0, chatComponent);
            chatPacket.getBooleans().write(0, false);
            protocolManager.sendServerPacket(player, chatPacket);

        } catch (Exception e) {
            logger.error("Failed to send FakeChatMessage packet to {}", player.getName(), e);
        }
    }
}