package org.ladyluh.desync.utils;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.wrappers.BlockPosition;
import com.comphenix.protocol.wrappers.EnumWrappers;
import com.comphenix.protocol.wrappers.Pair;
import com.comphenix.protocol.wrappers.WrappedBlockData;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.RayTraceResult;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.ladyluh.desync.Desync;

import java.util.List;


/**
 * Utility class for common effect-related tasks like sending block changes,
 * line of sight checks, etc.
 */
public class EffectUtils {

    private EffectUtils() {
    }

    /**
     * Helper method to send a block change packet to a specific player.
     *
     * @param player    The player to send the packet to.
     * @param location  The location of the block.
     * @param blockData The BlockData to display.
     * @param plugin    The main plugin instance.
     */
    public static void sendBlockChange(@NotNull Player player, @NotNull Location location, @NotNull BlockData blockData, @NotNull Desync plugin) {
        try {
            ProtocolManager protocolManager = plugin.getProtocolManager();
            PacketContainer blockChangePacket = protocolManager.createPacket(PacketType.Play.Server.BLOCK_CHANGE);

            blockChangePacket.getBlockPositionModifier().write(0, new BlockPosition(location.getBlockX(), location.getBlockY(), location.getBlockZ()));
            blockChangePacket.getBlockData().write(0, WrappedBlockData.createData(blockData));

            protocolManager.sendServerPacket(player, blockChangePacket);
        } catch (Exception e) {
            plugin.getPluginLogger().error("Failed to send BlockChange packet to {} for location {}", player.getName(), location.toVector(), e);
        }
    }

    /**
     * Crude helper to find the Y level of the ground below a location.
     * Logic moved from EventExecutor.
     *
     * @param loc The starting location.
     * @return The Y level of the first non-passable block below, plus 1. Returns minY - 1 if no solid block found above world minimum.
     */
    public static double findGroundY(@NotNull Location loc) {
        World world = loc.getWorld();
        if (world == null) return loc.getY();

        int startY = loc.getBlockY();
        int minY = world.getMinHeight();

        for (int y = startY; y >= minY; y--) {
            Block block = world.getBlockAt(loc.getBlockX(), y, loc.getBlockZ());

            if (!block.isPassable() && block.getType() != Material.AIR) {
                return y + 1.0;
            }
        }
        return minY - 1;
    }

    /**
     * Basic Line of Sight Check using RayTracing.
     * Logic moved from EventExecutor.
     *
     * @param start  The starting location (e.g., eye level).
     * @param end    The ending location (e.g., target player's eye level).
     * @param plugin The main plugin instance (needed for logger).
     * @return True if there is a clear line of sight (no block hit), false otherwise.
     */
    public static boolean hasLineOfSight(@Nullable Location start, @Nullable Location end, @NotNull Desync plugin) {
        if (start == null || end == null || !start.getWorld().equals(end.getWorld())) {

            return false;
        }

        World world = start.getWorld();
        double distance = start.distance(end);
        if (distance <= 0.01) return true;

        try {

            RayTraceResult result = world.rayTraceBlocks(
                    start,
                    end.toVector().subtract(start.toVector()).normalize(),
                    distance,
                    FluidCollisionMode.NEVER,
                    true
            );


            return result == null;
        } catch (Exception e) {
            plugin.getPluginLogger().error("Error during ray trace line of sight check from {} to {}: {}",
                    start.toVector(), end.toVector(), e.getMessage());

            return false;
        }
    }

    /**
     * Helper to create a list of ItemSlot/ItemStack pairs for entity equipment packets.
     * Logic moved from EventExecutor.
     *
     * @param heldItem The ItemStack to be placed in the mainhand. Can be null for empty hand.
     * @return A list containing a pair for the mainhand slot.
     */
    public static List<Pair<EnumWrappers.ItemSlot, ItemStack>> createMainHandEquipmentPair(@Nullable ItemStack heldItem) {

        EnumWrappers.ItemSlot protocolLibSlot = EnumWrappers.ItemSlot.MAINHAND;

        return List.of(new Pair<>(protocolLibSlot, heldItem));
    }


}