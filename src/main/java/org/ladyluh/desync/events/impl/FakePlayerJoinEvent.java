package org.ladyluh.desync.events.impl;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.wrappers.EnumWrappers.NativeGameMode;
import com.comphenix.protocol.wrappers.EnumWrappers.PlayerInfoAction;
import com.comphenix.protocol.wrappers.PlayerInfoData;
import com.comphenix.protocol.wrappers.WrappedChatComponent;
import com.comphenix.protocol.wrappers.WrappedGameProfile;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.ladyluh.desync.Desync;
import org.ladyluh.desync.events.PlayerDesyncEvent;
import org.ladyluh.desync.utils.SkinUtils;
import org.slf4j.Logger;

import java.util.Collections;
import java.util.EnumSet;
import java.util.UUID;


/**
 * Makes a fake player with the name "Null" appear in the player list briefly and sends fake join/leave messages.
 * Always attempts to use the pre-loaded target skin ('Joo'), falling back only to the default skin.
 */
public class FakePlayerJoinEvent implements PlayerDesyncEvent {

    private static final String KEY = "fake_join_leave_null";
    private static final String DESCRIPTION = "A user named 'Null' briefly joins and then leaves.";
    private static final long DEFAULT_COOLDOWN_MS = 3 * 60 * 1000L;

    private static final String FAKE_PLAYER_NAME = "Null";


    private static final long DISPLAY_DURATION_TICKS = 7200;
    private static final long REMOVE_MESSAGE_DELAY_TICKS = 5;


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
     * Triggers the fake player join event sequence:
     * 1. Obtain a fake player profile with the 'Joo' skin or default, using a dynamically generated UUID and the 'Null' name.
     * 2. PLAYER_INFO (ADD_PLAYER) to add this profile to the player list for the target player.
     * 3. SYSTEM_CHAT to send the join message to the target player.
     * 4. Schedule PLAYER_INFO_REMOVE after DISPLAY_DURATION_TICKS, using the SAME dynamic UUID.
     * 5. Schedule SYSTEM_CHAT leave message after PLAYER_INFO_REMOVE + REMOVE_MESSAGE_DELAY_TICKS.
     *
     * @param player The player to show the fake join/leave to.
     * @param plugin The main plugin instance.
     */
    @Override
    public void trigger(Player player, Desync plugin) {
        Logger logger = plugin.getPluginLogger();
        ProtocolManager protocolManager = plugin.getProtocolManager();

        logger.debug("Triggering FakePlayerJoinLeave ('{}') for {}", FAKE_PLAYER_NAME, player.getName());

        try {

            WrappedGameProfile fakeProfile = SkinUtils.getTargetSkinOrDefaultProfile(logger, FAKE_PLAYER_NAME);
            final UUID fakePlayerInstanceUUID = fakeProfile.getUUID();
            PlayerInfoData playerInfoData = new PlayerInfoData(fakeProfile, 0, NativeGameMode.SURVIVAL, WrappedChatComponent.fromLegacyText(FAKE_PLAYER_NAME));
            PacketContainer addPlayerPacket = protocolManager.createPacket(PacketType.Play.Server.PLAYER_INFO);

            addPlayerPacket.getPlayerInfoActions().write(0, EnumSet.of(PlayerInfoAction.ADD_PLAYER, PlayerInfoAction.UPDATE_LISTED));

            addPlayerPacket.getPlayerInfoDataLists().write(1, Collections.singletonList(playerInfoData));


            protocolManager.sendServerPacket(player, addPlayerPacket);
            logger.debug("Sent PLAYER_INFO (ADD_PLAYER) packet for '{}' ({}) to {}", FAKE_PLAYER_NAME, fakePlayerInstanceUUID, player.getName());


            String joinMessageText = FAKE_PLAYER_NAME + " joined the game";
            WrappedChatComponent joinMessageComponent = WrappedChatComponent.fromLegacyText(ChatColor.YELLOW + joinMessageText);

            PacketContainer joinMessagePacket = protocolManager.createPacket(PacketType.Play.Server.SYSTEM_CHAT);
            joinMessagePacket.getChatComponents().write(0, joinMessageComponent);
            joinMessagePacket.getBooleans().write(0, false);

            protocolManager.sendServerPacket(player, joinMessagePacket);
            logger.debug("Sent SYSTEM_CHAT join message packet for '{}' to {}", FAKE_PLAYER_NAME, player.getName());


            new BukkitRunnable() {
                @Override
                public void run() {

                    if (!player.isOnline()) {
                        logger.debug("FakePlayerJoinLeave skipping remove sequence for {} - player offline.", player.getName());
                        return;
                    }

                    try {

                        PacketContainer removePlayerPacket = protocolManager.createPacket(PacketType.Play.Server.PLAYER_INFO_REMOVE);

                        removePlayerPacket.getUUIDLists().write(0, Collections.singletonList(fakePlayerInstanceUUID));


                        protocolManager.sendServerPacket(player, removePlayerPacket);
                        logger.debug("Sent PLAYER_INFO_REMOVE packet for '{}' ({}) to {}", FAKE_PLAYER_NAME, fakePlayerInstanceUUID, player.getName());


                        new BukkitRunnable() {
                            @Override
                            public void run() {

                                if (!player.isOnline()) return;

                                try {
                                    String leaveMessageText = FAKE_PLAYER_NAME + " left the game";
                                    WrappedChatComponent leaveMessageComponent = WrappedChatComponent.fromLegacyText(ChatColor.YELLOW + leaveMessageText);

                                    PacketContainer leaveMessagePacket = protocolManager.createPacket(PacketType.Play.Server.SYSTEM_CHAT);
                                    leaveMessagePacket.getChatComponents().write(0, leaveMessageComponent);
                                    leaveMessagePacket.getBooleans().write(0, false);

                                    protocolManager.sendServerPacket(player, leaveMessagePacket);
                                    logger.debug("Sent SYSTEM_CHAT leave message packet for '{}' to {}", FAKE_PLAYER_NAME, player.getName());

                                } catch (Exception e) {
                                    logger.error("Failed to send FakePlayerJoinLeave leave message packet for '{}' to {}", FAKE_PLAYER_NAME, player.getName(), e);
                                }
                            }
                        }.runTaskLater(plugin, REMOVE_MESSAGE_DELAY_TICKS);

                    } catch (Exception e) {
                        logger.error("Failed to send FakePlayerJoinLeave remove packet for '{}' ({}) to {}", FAKE_PLAYER_NAME, fakePlayerInstanceUUID, player.getName(), e);

                    }
                }
            }.runTaskLater(plugin, DISPLAY_DURATION_TICKS);

        } catch (Exception e) {

            logger.error("Failed to send initial FakePlayerJoinLeave packets to {}", player.getName(), e);
        }
    }
}