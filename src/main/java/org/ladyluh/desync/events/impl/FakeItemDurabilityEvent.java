package org.ladyluh.desync.events.impl;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.events.PacketContainer;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.scheduler.BukkitRunnable;
import org.ladyluh.desync.Desync;
import org.ladyluh.desync.events.PlayerDesyncEvent;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Briefly alters the durability display of an item in the player's inventory clientside.
 */
public class FakeItemDurabilityEvent implements PlayerDesyncEvent {

    private static final String KEY = "item_durability";
    private static final String DESCRIPTION = "An item's durability display glitches briefly.";
    private static final long DEFAULT_COOLDOWN_MS = 40 * 1000L;

    private static final int MIN_DURABILITY_CHANGE = 5;
    private static final int MAX_DURABILITY_CHANGE = 20;

    private static final long FLICKER_DURATION_TICKS = 10;


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

        PlayerInventory inventory = player.getInventory();

        for (int i = 0; i <= 35; i++) {
            ItemStack item = inventory.getItem(i);
            if (item != null && item.getType().getMaxDurability() > 0) {
                return true;
            }
        }

        ItemStack offhandItem = inventory.getItem(40);
        return offhandItem != null && offhandItem.getType().getMaxDurability() > 0;
    }

    /**
     * Triggers the item durability flicker event.
     * Finds an item with durability, modifies its damage value clientside, and schedules a revert.
     *
     * @param player The player whose item durability to flicker.
     * @param plugin The main plugin instance.
     */
    @Override
    public void trigger(Player player, Desync plugin) {
        Logger logger = plugin.getPluginLogger();
        ProtocolManager protocolManager = plugin.getProtocolManager();
        ThreadLocalRandom random = ThreadLocalRandom.current();
        PlayerInventory inventory = player.getInventory();


        List<Integer> durabilityItemSlots = new ArrayList<>();
        for (int i = 0; i <= 35; i++) {
            ItemStack item = inventory.getItem(i);
            if (item != null && item.getType().getMaxDurability() > 0) {
                durabilityItemSlots.add(i);
            }
        }
        ItemStack offhandItem = inventory.getItem(40);
        if (offhandItem != null && offhandItem.getType().getMaxDurability() > 0) {
            durabilityItemSlots.add(40);
        }


        if (durabilityItemSlots.isEmpty()) {
            logger.debug("FakeItemDurability trigger for {}: No items with durability found during trigger execution.", player.getName());
            return;
        }


        int targetSlot = durabilityItemSlots.get(random.nextInt(durabilityItemSlots.size()));
        ItemStack originalItem = inventory.getItem(targetSlot);
        if (originalItem == null || originalItem.getType().getMaxDurability() <= 0) {
            logger.debug("FakeItemDurability trigger for {}: Item in target slot {} is unexpectedly null or has no durability.", player.getName(), targetSlot);
            return;
        }


        short maxDurability = originalItem.getType().getMaxDurability();
        Damageable dmgItem = (Damageable) originalItem.getItemMeta();
        int currentDamage = dmgItem.getDamage();

        int damageChange = random.nextInt(MAX_DURABILITY_CHANGE - MIN_DURABILITY_CHANGE + 1) + MIN_DURABILITY_CHANGE;

        short fakeNewDamage = (short) (currentDamage + damageChange);

        fakeNewDamage = (short) Math.min(fakeNewDamage, maxDurability);


        if (fakeNewDamage == currentDamage && maxDurability > currentDamage) {
            fakeNewDamage = (short) Math.min(currentDamage + MIN_DURABILITY_CHANGE, maxDurability);
        }


        if (fakeNewDamage == currentDamage) {
            logger.debug("FakeItemDurability trigger for {}: Cannot change durability of item in slot {} (already broken or minimal change not possible).", player.getName(), targetSlot);
            return;
        }


        logger.info("Triggering FakeItemDurability for {} (Slot {}, Damage: {} -> {})",
                player.getName(), targetSlot, currentDamage, fakeNewDamage);


        try {


            Damageable itemToSend = (Damageable) originalItem.clone();


            itemToSend.setDamage(fakeNewDamage);


            PacketContainer setSlotPacket = protocolManager.createPacket(PacketType.Play.Server.SET_SLOT);
            setSlotPacket.getIntegers().write(0, 0);
            setSlotPacket.getIntegers().write(1, 0);


            setSlotPacket.getIntegers().write(2, targetSlot);
            setSlotPacket.getItemModifier().write(0, (ItemStack) itemToSend);

            protocolManager.sendServerPacket(player, setSlotPacket);
            logger.debug("Sent SET_SLOT packet for slot {} with fake damage {} to {}", targetSlot, fakeNewDamage, player.getName());


            final int finalTargetSlot = targetSlot;
            final Damageable finalOriginalItem = (Damageable) originalItem.clone();

            new BukkitRunnable() {
                @Override
                public void run() {
                    if (!player.isOnline()) {
                        logger.debug("FakeItemDurability skipping revert for {} - player offline.", player.getName());
                        return;
                    }

                    try {

                        PacketContainer revertPacket = protocolManager.createPacket(PacketType.Play.Server.SET_SLOT);
                        revertPacket.getIntegers().write(0, 0);
                        revertPacket.getIntegers().write(1, 0);
                        revertPacket.getIntegers().write(2, finalTargetSlot);
                        revertPacket.getItemModifier().write(0, (ItemStack) finalOriginalItem);

                        protocolManager.sendServerPacket(player, revertPacket);
                        logger.debug("Sent SET_SLOT packet for slot {} with original damage {} to {}", finalTargetSlot, finalOriginalItem.getDamage(), player.getName());

                    } catch (Exception e) {
                        logger.error("Failed to send FakeItemDurability revert SET_SLOT packet for slot {} to {}", finalTargetSlot, player.getName(), e);
                    }
                }
            }.runTaskLater(plugin, FLICKER_DURATION_TICKS);


        } catch (Exception e) {
            logger.error("Failed to send initial FakeItemDurability SET_SLOT packet to {}", player.getName(), e);
        }
    }
}