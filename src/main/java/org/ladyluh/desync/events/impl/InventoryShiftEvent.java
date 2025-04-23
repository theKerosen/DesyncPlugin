package org.ladyluh.desync.events.impl;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.events.PacketContainer;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import org.jetbrains.annotations.NotNull;
import org.ladyluh.desync.Desync;
import org.ladyluh.desync.events.PlayerDesyncEvent;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;


/**
 * Briefly shuffles the items in the player's inventory/hotbar clientside.
 */
public class InventoryShiftEvent implements PlayerDesyncEvent {

    private static final String KEY = "inventory_shift";
    private static final String DESCRIPTION = "Your inventory items briefly rearrange.";
    private static final long DEFAULT_COOLDOWN_MS = 75 * 1000L;


    private static final int EFFECT_MIN_SLOT = 9;
    private static final int EFFECT_MAX_SLOT = 44;

    private static final int SHUFFLE_COUNT_MIN = 2;
    private static final int SHUFFLE_COUNT_MAX = 4;


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


        Inventory playerInventory = player.getInventory();
        for (int i = EFFECT_MIN_SLOT; i <= EFFECT_MAX_SLOT; i++) {
            ItemStack item = playerInventory.getItem(i);
            if (item != null) {
                item.getType();
            }
        }


        int nonAirSlots = 0;
        for (int i = EFFECT_MIN_SLOT; i <= EFFECT_MAX_SLOT; i++) {
            if (playerInventory.getItem(i) != null && Objects.requireNonNull(playerInventory.getItem(i)).getType() != Material.AIR) {
                nonAirSlots++;
            }
        }
        return nonAirSlots >= 2;
    }

    /**
     * Triggers the inventory shift event.
     * Selects random pairs of slots and swaps their contents clientside briefly.
     *
     * @param player The player whose inventory to shift.
     * @param plugin The main plugin instance.
     */
    @Override
    public void trigger(Player player, Desync plugin) {
        Logger logger = plugin.getPluginLogger();
        ProtocolManager protocolManager = plugin.getProtocolManager();
        ThreadLocalRandom random = ThreadLocalRandom.current();
        Inventory playerInventory = player.getInventory();


        List<ItemStack> originalItems = new ArrayList<>();
        List<Integer> relevantSlots = new ArrayList<>();
        for (int i = EFFECT_MIN_SLOT; i <= EFFECT_MAX_SLOT; i++) {
            originalItems.add(playerInventory.getItem(i));
            relevantSlots.add(i);
        }


        List<ItemStack> shuffledItems = new ArrayList<>(originalItems);

        java.util.Collections.shuffle(shuffledItems, random);


        List<Integer> slotsToUpdate = getIntegers(originalItems, shuffledItems, relevantSlots);


        if (slotsToUpdate.isEmpty()) {
            logger.debug("InventoryShift trigger for {}: Shuffling resulted in no item changes in relevant slots.", player.getName());


            return;
        }


        int desiredUpdatesCount = random.nextInt(SHUFFLE_COUNT_MIN, SHUFFLE_COUNT_MAX + 1);
        int actualUpdatesCount = Math.min(slotsToUpdate.size(), desiredUpdatesCount);


        Collections.shuffle(slotsToUpdate, random);
        List<Integer> finalSlotsToUpdate = slotsToUpdate.subList(0, actualUpdatesCount);


        logger.info("Triggering InventoryShift for {} (Updating {} slots clientside)", player.getName(), finalSlotsToUpdate.size());


        try {

            for (int updatedSlot : finalSlotsToUpdate) {


                int originalIndex = relevantSlots.indexOf(updatedSlot);
                if (originalIndex == -1) {
                    logger.warn("InventoryShift: Could not find original index for slot {} for player {}", updatedSlot, player.getName());
                    continue;
                }
                ItemStack itemToSend = shuffledItems.get(originalIndex);

                PacketContainer setSlotPacket = protocolManager.createPacket(PacketType.Play.Server.SET_SLOT);
                setSlotPacket.getIntegers().write(0, 0);
                setSlotPacket.getIntegers().write(1, 0);


                setSlotPacket.getIntegers().write(2, updatedSlot);
                setSlotPacket.getItemModifier().write(0, itemToSend);

                protocolManager.sendServerPacket(player, setSlotPacket);
                logger.debug("Sent SET_SLOT packet for slot {} with item {} for {}", updatedSlot, (itemToSend != null ? itemToSend.getType() : "AIR"), player.getName());
            }


            final List<ItemStack> finalOriginalItems = originalItems;

            final List<Integer> finalRelevantSlots = relevantSlots;

            new BukkitRunnable() {
                @Override
                public void run() {
                    if (!player.isOnline()) {
                        logger.debug("InventoryShift skipping revert for {} - player offline.", player.getName());
                        return;
                    }


                    for (int updatedSlot : finalSlotsToUpdate) {
                        int originalIndex = finalRelevantSlots.indexOf(updatedSlot);
                        if (originalIndex == -1) continue;

                        ItemStack originalItem = finalOriginalItems.get(originalIndex);

                        try {
                            PacketContainer setSlotPacket = protocolManager.createPacket(PacketType.Play.Server.SET_SLOT);
                            setSlotPacket.getIntegers().write(0, 0);
                            setSlotPacket.getIntegers().write(1, 0);
                            setSlotPacket.getIntegers().write(2, updatedSlot);
                            setSlotPacket.getItemModifier().write(0, originalItem);

                            protocolManager.sendServerPacket(player, setSlotPacket);
                            logger.debug("Reverted SET_SLOT packet for slot {} with item {} for {}", updatedSlot, (originalItem != null ? originalItem.getType() : "AIR"), player.getName());
                        } catch (Exception e) {
                            logger.error("Failed to send InventoryShift revert SET_SLOT packet for slot {} to {}", updatedSlot, player.getName(), e);
                        }
                    }
                    logger.debug("InventoryShift revert complete for {}", player.getName());
                }
            }.runTaskLater(plugin, 10L);


        } catch (Exception e) {
            logger.error("Failed to send initial InventoryShift SET_SLOT packets to {}", player.getName(), e);
        }
    }

    private static @NotNull List<Integer> getIntegers(List<ItemStack> originalItems, List<ItemStack> shuffledItems, List<Integer> relevantSlots) {
        List<Integer> slotsToUpdate = new ArrayList<>();
        for (int i = 0; i < originalItems.size(); i++) {


            if (!Objects.equals(originalItems.get(i), shuffledItems.get(i))) {
                slotsToUpdate.add(relevantSlots.get(i));
            }
        }
        return slotsToUpdate;
    }
}