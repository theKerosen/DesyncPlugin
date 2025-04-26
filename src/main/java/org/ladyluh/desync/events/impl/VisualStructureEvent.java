package org.ladyluh.desync.events.impl;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.wrappers.BlockPosition;
import com.comphenix.protocol.wrappers.WrappedBlockData;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;
import org.ladyluh.desync.Desync;
import org.ladyluh.desync.events.PlayerDesyncEvent;
import org.ladyluh.desync.utils.EffectUtils;
import org.slf4j.Logger;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Makes a small, simple structure (like a short wall or doorway) appear clientside and then disappear.
 */
public class VisualStructureEvent implements PlayerDesyncEvent {

    private static final String KEY = "visual_structure";
    private static final String DESCRIPTION = "A small, strange structure appears briefly.";
    private static final long DEFAULT_COOLDOWN_MS = 90 * 1000L;

    private static final int SEARCH_RADIUS = 128;
    private static final long STRUCTURE_DURATION_TICKS = 40 + ThreadLocalRandom.current().nextInt(41);


    private static final List<Map<Vector, Material>> STRUCTURE_BLUEPRINTS;


    static {
        List<Map<Vector, Material>> blueprints = new ArrayList<>();


        Map<Vector, Material> bp1 = new HashMap<>();
        bp1.put(new Vector(0, 0, 0), Material.OBSIDIAN);
        bp1.put(new Vector(0, 1, 0), Material.CRYING_OBSIDIAN);
        bp1.put(new Vector(0, 2, 0), Material.SCULK);
        blueprints.add(bp1);


        Map<Vector, Material> bp2 = new HashMap<>();
        bp2.put(new Vector(0, 0, 0), Material.DEEPSLATE_BRICKS);
        bp2.put(new Vector(0, 1, 0), Material.CRACKED_DEEPSLATE_BRICKS);
        bp2.put(new Vector(1, 0, 0), Material.DEEPSLATE_BRICKS);
        bp2.put(new Vector(1, 1, 0), Material.MOSSY_COBBLESTONE);
        blueprints.add(bp2);


        Map<Vector, Material> bp3 = new HashMap<>();
        bp3.put(new Vector(0, 0, 0), Material.POLISHED_BLACKSTONE);
        bp3.put(new Vector(0, 1, 0), Material.IRON_DOOR);


        blueprints.add(bp3);


        Map<Vector, Material> bp4 = new HashMap<>();
        bp4.put(new Vector(0, 0, 0), Material.BLACKSTONE);
        bp4.put(new Vector(2, 0, 0), Material.BLACKSTONE);
        bp4.put(new Vector(0, 1, 0), Material.BLACKSTONE);
        bp4.put(new Vector(2, 1, 0), Material.BLACKSTONE);
        bp4.put(new Vector(0, 2, 0), Material.DEEPSLATE);
        bp4.put(new Vector(2, 2, 0), Material.DEEPSLATE);
        bp4.put(new Vector(0, 3, 0), Material.DEEPSLATE);
        bp4.put(new Vector(1, 3, 0), Material.CHISELED_DEEPSLATE);
        bp4.put(new Vector(2, 3, 0), Material.DEEPSLATE);
        blueprints.add(bp4);


        Map<Vector, Material> bp5 = new HashMap<>();

        bp5.put(new Vector(0, 0, 0), Material.SOUL_SOIL);
        bp5.put(new Vector(1, 0, 0), Material.NETHERRACK);
        bp5.put(new Vector(2, 0, 0), Material.CRIMSON_NYLIUM);
        bp5.put(new Vector(0, 0, 1), Material.NETHERRACK);
        bp5.put(new Vector(1, 0, 1), Material.SOUL_SAND);
        bp5.put(new Vector(2, 0, 1), Material.NETHERRACK);
        bp5.put(new Vector(0, 0, 2), Material.CRIMSON_NYLIUM);
        bp5.put(new Vector(1, 0, 2), Material.NETHERRACK);
        bp5.put(new Vector(2, 0, 2), Material.SOUL_SOIL);

        bp5.put(new Vector(0, 1, 0), Material.DEEPSLATE_BRICKS);
        bp5.put(new Vector(2, 1, 0), Material.DEEPSLATE_BRICKS);
        bp5.put(new Vector(0, 1, 1), Material.DEEPSLATE_BRICKS);
        bp5.put(new Vector(2, 1, 1), Material.DEEPSLATE_BRICKS);
        bp5.put(new Vector(0, 1, 2), Material.DEEPSLATE_BRICKS);
        bp5.put(new Vector(1, 1, 2), Material.DEEPSLATE_BRICKS);
        bp5.put(new Vector(2, 1, 2), Material.DEEPSLATE_BRICKS);

        bp5.put(new Vector(0, 2, 0), Material.DEEPSLATE_BRICKS);
        bp5.put(new Vector(2, 2, 0), Material.DEEPSLATE_BRICKS);
        bp5.put(new Vector(0, 2, 1), Material.IRON_BARS);
        bp5.put(new Vector(2, 2, 1), Material.IRON_BARS);
        bp5.put(new Vector(0, 2, 2), Material.DEEPSLATE_BRICKS);
        bp5.put(new Vector(1, 2, 2), Material.DEEPSLATE_BRICKS);
        bp5.put(new Vector(2, 2, 2), Material.DEEPSLATE_BRICKS);

        bp5.put(new Vector(0, 3, 0), Material.CRACKED_DEEPSLATE_BRICKS);
        bp5.put(new Vector(1, 3, 0), Material.MOSSY_COBBLESTONE);
        bp5.put(new Vector(2, 3, 0), Material.CRACKED_DEEPSLATE_BRICKS);
        bp5.put(new Vector(0, 3, 1), Material.MOSSY_COBBLESTONE);
        bp5.put(new Vector(2, 3, 1), Material.MOSSY_COBBLESTONE);
        bp5.put(new Vector(0, 3, 2), Material.CRACKED_DEEPSLATE_BRICKS);
        bp5.put(new Vector(1, 3, 2), Material.MOSSY_COBBLESTONE);
        bp5.put(new Vector(2, 3, 2), Material.CRACKED_DEEPSLATE_BRICKS);
        blueprints.add(bp5);


        Map<Vector, Material> bp6 = new HashMap<>();

        bp6.put(new Vector(0, 0, 0), Material.DARK_OAK_PLANKS);
        bp6.put(new Vector(2, 0, 0), Material.DARK_OAK_PLANKS);
        bp6.put(new Vector(0, 0, 2), Material.DARK_OAK_PLANKS);
        bp6.put(new Vector(2, 0, 2), Material.DARK_OAK_PLANKS);

        bp6.put(new Vector(0, 1, 0), Material.POLISHED_BLACKSTONE);
        bp6.put(new Vector(1, 1, 0), Material.POLISHED_BLACKSTONE);
        bp6.put(new Vector(2, 1, 0), Material.POLISHED_BLACKSTONE);
        bp6.put(new Vector(0, 1, 1), Material.POLISHED_BLACKSTONE);
        bp6.put(new Vector(1, 1, 1), Material.POLISHED_BLACKSTONE);
        bp6.put(new Vector(2, 1, 1), Material.POLISHED_BLACKSTONE);
        bp6.put(new Vector(0, 1, 2), Material.POLISHED_BLACKSTONE);
        bp6.put(new Vector(1, 1, 2), Material.POLISHED_BLACKSTONE);
        bp6.put(new Vector(2, 1, 2), Material.POLISHED_BLACKSTONE);

        bp6.put(new Vector(1, 2, 1), Material.SKELETON_SKULL);
        bp6.put(new Vector(0, 2, 0), Material.ZOMBIE_HEAD);
        bp6.put(new Vector(2, 2, 2), Material.WITHER_SKELETON_SKULL);
        bp6.put(new Vector(1, 2, 0), Material.CAULDRON);
        blueprints.add(bp6);


        Map<Vector, Material> bp7 = new HashMap<>();

        bp7.put(new Vector(0, 0, 0), Material.MOSSY_COBBLESTONE);
        bp7.put(new Vector(1, 0, 0), Material.COBBLESTONE);
        bp7.put(new Vector(2, 0, 0), Material.MOSSY_COBBLESTONE);
        bp7.put(new Vector(3, 0, 0), Material.COBBLESTONE);
        bp7.put(new Vector(0, 0, 1), Material.COBBLESTONE);
        bp7.put(new Vector(1, 0, 1), Material.MOSSY_COBBLESTONE);
        bp7.put(new Vector(2, 0, 1), Material.COBBLESTONE);
        bp7.put(new Vector(3, 0, 1), Material.MOSSY_COBBLESTONE);
        bp7.put(new Vector(0, 0, 2), Material.MOSSY_COBBLESTONE);
        bp7.put(new Vector(1, 0, 2), Material.COBBLESTONE);
        bp7.put(new Vector(2, 0, 2), Material.MOSSY_COBBLESTONE);
        bp7.put(new Vector(3, 0, 2), Material.COBBLESTONE);

        bp7.put(new Vector(0, 1, 0), Material.DARK_OAK_PLANKS);
        bp7.put(new Vector(3, 1, 0), Material.DARK_OAK_PLANKS);
        bp7.put(new Vector(0, 1, 1), Material.DARK_OAK_PLANKS);
        bp7.put(new Vector(3, 1, 1), Material.DARK_OAK_PLANKS);
        bp7.put(new Vector(0, 1, 2), Material.CRACKED_STONE_BRICKS);
        bp7.put(new Vector(1, 1, 2), Material.CRACKED_STONE_BRICKS);
        bp7.put(new Vector(2, 1, 2), Material.CRACKED_STONE_BRICKS);
        bp7.put(new Vector(3, 1, 2), Material.CRACKED_STONE_BRICKS);

        bp7.put(new Vector(0, 2, 0), Material.DARK_OAK_PLANKS);
        bp7.put(new Vector(3, 2, 0), Material.DARK_OAK_PLANKS);
        bp7.put(new Vector(1, 2, 0), Material.GLASS_PANE);
        bp7.put(new Vector(2, 2, 0), Material.COBWEB);
        bp7.put(new Vector(0, 2, 1), Material.DARK_OAK_PLANKS);
        bp7.put(new Vector(3, 2, 1), Material.DARK_OAK_PLANKS);
        bp7.put(new Vector(0, 2, 2), Material.DARK_OAK_PLANKS);
        bp7.put(new Vector(1, 2, 2), Material.DARK_OAK_PLANKS);
        bp7.put(new Vector(2, 2, 2), Material.DARK_OAK_PLANKS);
        bp7.put(new Vector(3, 2, 2), Material.DARK_OAK_PLANKS);

        bp7.put(new Vector(0, 3, 0), Material.SPRUCE_STAIRS);
        bp7.put(new Vector(1, 3, 0), Material.SPRUCE_STAIRS);
        bp7.put(new Vector(2, 3, 0), Material.SPRUCE_STAIRS);
        bp7.put(new Vector(3, 3, 0), Material.SPRUCE_STAIRS);
        bp7.put(new Vector(0, 3, 1), Material.SPRUCE_STAIRS);
        bp7.put(new Vector(1, 3, 1), Material.SPRUCE_STAIRS);
        bp7.put(new Vector(2, 3, 1), Material.SPRUCE_STAIRS);
        bp7.put(new Vector(3, 3, 1), Material.SPRUCE_STAIRS);
        bp7.put(new Vector(0, 3, 2), Material.SPRUCE_STAIRS);
        bp7.put(new Vector(1, 3, 2), Material.SPRUCE_STAIRS);
        bp7.put(new Vector(2, 3, 2), Material.SPRUCE_STAIRS);
        bp7.put(new Vector(3, 3, 2), Material.SPRUCE_STAIRS);
        blueprints.add(bp7);


        Map<Vector, Material> bp8 = new HashMap<>();
        bp8.put(new Vector(0, 0, 0), Material.BONE_BLOCK);
        bp8.put(new Vector(0, 1, 0), Material.BONE_BLOCK);
        bp8.put(new Vector(0, 2, 0), Material.SKELETON_SKULL);
        bp8.put(new Vector(0, 3, 0), Material.BONE_BLOCK);
        bp8.put(new Vector(0, 4, 0), Material.BONE_BLOCK);
        blueprints.add(bp8);


        Map<Vector, Material> bp9 = new HashMap<>();

        bp9.put(new Vector(0, 0, 0), Material.MOSSY_COBBLESTONE);
        bp9.put(new Vector(0, 0, 2), Material.MOSSY_COBBLESTONE);
        bp9.put(new Vector(4, 0, 0), Material.MOSSY_COBBLESTONE);
        bp9.put(new Vector(4, 0, 2), Material.MOSSY_COBBLESTONE);
        bp9.put(new Vector(0, 1, 0), Material.CRACKED_STONE_BRICKS);
        bp9.put(new Vector(0, 1, 2), Material.CRACKED_STONE_BRICKS);
        bp9.put(new Vector(4, 1, 0), Material.CRACKED_STONE_BRICKS);
        bp9.put(new Vector(4, 1, 2), Material.CRACKED_STONE_BRICKS);

        bp9.put(new Vector(0, 2, 0), Material.DARK_OAK_PLANKS);
        bp9.put(new Vector(1, 2, 0), Material.DARK_OAK_PLANKS); /* Gap at (2,2,0) */
        bp9.put(new Vector(3, 2, 0), Material.DARK_OAK_PLANKS);
        bp9.put(new Vector(4, 2, 0), Material.DARK_OAK_PLANKS);
        bp9.put(new Vector(0, 2, 1), Material.DARK_OAK_PLANKS);
        bp9.put(new Vector(1, 2, 1), Material.DARK_OAK_PLANKS);
        bp9.put(new Vector(2, 2, 1), Material.DARK_OAK_PLANKS);
        bp9.put(new Vector(3, 2, 1), Material.DARK_OAK_PLANKS);
        bp9.put(new Vector(4, 2, 1), Material.DARK_OAK_PLANKS);
        bp9.put(new Vector(0, 2, 2), Material.DARK_OAK_PLANKS);
        bp9.put(new Vector(1, 2, 2), Material.DARK_OAK_PLANKS); /* Gap at (2,2,2) */
        bp9.put(new Vector(3, 2, 2), Material.DARK_OAK_PLANKS);
        bp9.put(new Vector(4, 2, 2), Material.DARK_OAK_PLANKS);

        bp9.put(new Vector(0, 3, 0), Material.DARK_OAK_FENCE);
        bp9.put(new Vector(1, 3, 0), Material.DARK_OAK_FENCE);
        bp9.put(new Vector(3, 3, 0), Material.DARK_OAK_FENCE);
        bp9.put(new Vector(4, 3, 0), Material.DARK_OAK_FENCE);
        bp9.put(new Vector(0, 3, 2), Material.DARK_OAK_FENCE);
        bp9.put(new Vector(1, 3, 2), Material.DARK_OAK_FENCE);
        bp9.put(new Vector(3, 3, 2), Material.DARK_OAK_FENCE);
        bp9.put(new Vector(4, 3, 2), Material.DARK_OAK_FENCE);
        blueprints.add(bp9);


        Map<Vector, Material> bp10 = new HashMap<>();

        bp10.put(new Vector(0, 0, 0), Material.SCULK);
        bp10.put(new Vector(1, 0, 0), Material.SOUL_SOIL);
        bp10.put(new Vector(0, 0, 1), Material.MOSSY_COBBLESTONE);

        bp10.put(new Vector(0, 1, 0), Material.SCULK_VEIN);
        bp10.put(new Vector(1, 1, 0), Material.CRACKED_STONE_BRICKS);
        bp10.put(new Vector(0, 1, 1), Material.COBWEB);

        bp10.put(new Vector(1, 2, 0), Material.SCULK_CATALYST);
        blueprints.add(bp10);


        Map<Vector, Material> bp11 = new HashMap<>();

        bp11.put(new Vector(0, 0, 0), Material.COBBLED_DEEPSLATE);
        bp11.put(new Vector(1, 0, 0), Material.COBBLED_DEEPSLATE);
        bp11.put(new Vector(0, 0, 1), Material.COBBLED_DEEPSLATE);
        bp11.put(new Vector(1, 0, 1), Material.COBBLED_DEEPSLATE);

        bp11.put(new Vector(0, 1, 0), Material.DEEPSLATE_BRICKS);
        bp11.put(new Vector(1, 1, 0), Material.DEEPSLATE_BRICKS);
        bp11.put(new Vector(0, 1, 1), Material.DEEPSLATE_BRICKS);
        bp11.put(new Vector(0, 2, 0), Material.DEEPSLATE_BRICKS);
        bp11.put(new Vector(1, 2, 0), Material.DEEPSLATE_BRICKS);
        bp11.put(new Vector(0, 2, 1), Material.DEEPSLATE_BRICKS);
        bp11.put(new Vector(0, 3, 0), Material.DEEPSLATE_BRICKS);
        bp11.put(new Vector(1, 3, 0), Material.DEEPSLATE_BRICKS);
        bp11.put(new Vector(0, 3, 1), Material.DEEPSLATE_BRICKS);
        bp11.put(new Vector(0, 4, 0), Material.DEEPSLATE_BRICKS);
        bp11.put(new Vector(1, 4, 0), Material.DEEPSLATE_BRICKS);
        bp11.put(new Vector(0, 4, 1), Material.DEEPSLATE_BRICKS);

        bp11.put(new Vector(1, 1, 1), Material.LADDER);
        bp11.put(new Vector(1, 2, 1), Material.LADDER);
        bp11.put(new Vector(1, 3, 1), Material.LADDER);
        bp11.put(new Vector(1, 4, 1), Material.LADDER);

        bp11.put(new Vector(1, 5, 1), Material.DEEPSLATE_BRICKS);
        bp11.put(new Vector(0, 5, 0), Material.SOUL_TORCH);
        blueprints.add(bp11);


        Map<Vector, Material> bp12 = new HashMap<>();
        bp12.put(new Vector(0, 0, 0), Material.NETHER_BRICKS);
        bp12.put(new Vector(1, 0, 0), Material.NETHER_BRICKS);
        bp12.put(new Vector(0, 0, 1), Material.NETHER_BRICKS);
        bp12.put(new Vector(1, 0, 1), Material.NETHER_BRICKS);

        bp12.put(new Vector(0, 1, 0), Material.CRYING_OBSIDIAN);
        bp12.put(new Vector(1, 1, 0), Material.CHISELED_NETHER_BRICKS);
        bp12.put(new Vector(0, 1, 1), Material.SKELETON_SKULL);
        bp12.put(new Vector(1, 1, 1), Material.CAULDRON);

        bp12.put(new Vector(0, 1, -1), Material.RED_NETHER_BRICKS);
        bp12.put(new Vector(1, 1, -1), Material.RED_NETHER_BRICKS);
        bp12.put(new Vector(0, 2, -1), Material.RED_NETHER_BRICKS);
        bp12.put(new Vector(1, 2, -1), Material.RED_NETHER_BRICKS);

        bp12.put(new Vector(0, 3, -1), Material.LANTERN);

        blueprints.add(bp12);


        Map<Vector, Material> bp13 = new HashMap<>();

        bp13.put(new Vector(-2, 0, -2), Material.OBSIDIAN);
        bp13.put(new Vector(-1, 0, -2), Material.BLACKSTONE);
        bp13.put(new Vector(0, 0, -2), Material.OBSIDIAN);
        bp13.put(new Vector(1, 0, -2), Material.BLACKSTONE);
        bp13.put(new Vector(2, 0, -2), Material.OBSIDIAN);
        bp13.put(new Vector(-2, 0, -1), Material.BLACKSTONE);
        bp13.put(new Vector(2, 0, -1), Material.BLACKSTONE);
        bp13.put(new Vector(-2, 0, 0), Material.OBSIDIAN);
        bp13.put(new Vector(-1, 0, 0), Material.BLACKSTONE);
        bp13.put(new Vector(0, 0, 0), Material.SOUL_SAND);
        bp13.put(new Vector(1, 0, 0), Material.BLACKSTONE);
        bp13.put(new Vector(2, 0, 0), Material.OBSIDIAN);
        bp13.put(new Vector(-2, 0, 1), Material.BLACKSTONE);
        bp13.put(new Vector(2, 0, 1), Material.BLACKSTONE);
        bp13.put(new Vector(-2, 0, 2), Material.OBSIDIAN);
        bp13.put(new Vector(-1, 0, 2), Material.BLACKSTONE);
        bp13.put(new Vector(0, 0, 2), Material.OBSIDIAN);
        bp13.put(new Vector(1, 0, 2), Material.BLACKSTONE);
        bp13.put(new Vector(2, 0, 2), Material.OBSIDIAN);

        bp13.put(new Vector(0, 1, 0), Material.MAGMA_BLOCK);
        bp13.put(new Vector(0, 2, 0), Material.CRYING_OBSIDIAN);
        blueprints.add(bp13);


        Map<Vector, Material> bp14 = new HashMap<>();

        bp14.put(new Vector(0, 0, 0), Material.COBBLED_DEEPSLATE);
        bp14.put(new Vector(1, 0, 0), Material.COBBLED_DEEPSLATE);
        bp14.put(new Vector(2, 0, 0), Material.COBBLED_DEEPSLATE);
        bp14.put(new Vector(0, 0, 1), Material.COBBLED_DEEPSLATE);
        bp14.put(new Vector(1, 0, 1), Material.COBBLED_DEEPSLATE);
        bp14.put(new Vector(2, 0, 1), Material.COBBLED_DEEPSLATE);
        bp14.put(new Vector(0, 0, 2), Material.COBBLED_DEEPSLATE);
        bp14.put(new Vector(1, 0, 2), Material.COBBLED_DEEPSLATE);
        bp14.put(new Vector(2, 0, 2), Material.COBBLED_DEEPSLATE);

        bp14.put(new Vector(0, 1, 0), Material.RED_WOOL);
        bp14.put(new Vector(1, 1, 0), Material.BLACK_WOOL);
        bp14.put(new Vector(2, 1, 0), Material.RED_WOOL);
        bp14.put(new Vector(0, 1, 1), Material.BLACK_WOOL);
        bp14.put(new Vector(1, 1, 1), Material.RED_WOOL);
        bp14.put(new Vector(2, 1, 1), Material.BLACK_WOOL);
        bp14.put(new Vector(0, 1, 2), Material.RED_WOOL);
        bp14.put(new Vector(1, 1, 2), Material.BLACK_WOOL);
        bp14.put(new Vector(2, 1, 2), Material.RED_WOOL);
        blueprints.add(bp14);


        Map<Vector, Material> bp15 = new HashMap<>();
        bp15.put(new Vector(0, 0, 0), Material.NETHER_BRICKS);
        bp15.put(new Vector(1, 1, 0), Material.CRACKED_STONE_BRICKS);

        bp15.put(new Vector(3, 3, 0), Material.MOSSY_COBBLESTONE);
        blueprints.add(bp15);


        Map<Vector, Material> bp16 = new HashMap<>();

        bp16.put(new Vector(0, 0, 0), Material.DEEPSLATE);
        bp16.put(new Vector(1, 0, 0), Material.DEEPSLATE);
        bp16.put(new Vector(2, 0, 0), Material.DEEPSLATE);
        bp16.put(new Vector(0, 0, 1), Material.DEEPSLATE);
        bp16.put(new Vector(1, 0, 1), Material.MAGMA_BLOCK);
        bp16.put(new Vector(2, 0, 1), Material.DEEPSLATE);
        bp16.put(new Vector(0, 0, 2), Material.DEEPSLATE);
        bp16.put(new Vector(1, 0, 2), Material.DEEPSLATE);
        bp16.put(new Vector(2, 0, 2), Material.DEEPSLATE);

        bp16.put(new Vector(0, 1, 0), Material.IRON_BARS);
        bp16.put(new Vector(1, 1, 0), Material.IRON_BARS);
        bp16.put(new Vector(2, 1, 0), Material.IRON_BARS);
        bp16.put(new Vector(0, 1, 1), Material.IRON_BARS);
        bp16.put(new Vector(2, 1, 1), Material.IRON_BARS);
        bp16.put(new Vector(0, 1, 2), Material.IRON_BARS);
        bp16.put(new Vector(1, 1, 2), Material.IRON_BARS);
        bp16.put(new Vector(2, 1, 2), Material.IRON_BARS);

        bp16.put(new Vector(0, 2, 0), Material.IRON_BARS);
        bp16.put(new Vector(1, 2, 0), Material.IRON_BARS);
        bp16.put(new Vector(2, 2, 0), Material.IRON_BARS);
        bp16.put(new Vector(0, 2, 1), Material.IRON_BARS);
        bp16.put(new Vector(2, 2, 1), Material.IRON_BARS);
        bp16.put(new Vector(0, 2, 2), Material.IRON_BARS);
        bp16.put(new Vector(1, 2, 2), Material.IRON_BARS);
        bp16.put(new Vector(2, 2, 2), Material.IRON_BARS);
        blueprints.add(bp16);


        Map<Vector, Material> bp17 = new HashMap<>();

        bp17.put(new Vector(0, 0, 0), Material.COBBLED_DEEPSLATE);
        bp17.put(new Vector(1, 0, 0), Material.MOSSY_COBBLESTONE);
        bp17.put(new Vector(2, 0, 0), Material.COBBLED_DEEPSLATE);
        bp17.put(new Vector(0, 1, 0), Material.CRACKED_DEEPSLATE_BRICKS);
        bp17.put(new Vector(1, 1, 0), Material.DEEPSLATE_BRICKS);
        bp17.put(new Vector(2, 1, 0), Material.CRACKED_DEEPSLATE_BRICKS);

        bp17.put(new Vector(0, 2, 0), Material.SKELETON_SKULL);
        bp17.put(new Vector(2, 2, 0), Material.ZOMBIE_HEAD);
        blueprints.add(bp17);


        Map<Vector, Material> bp18 = new HashMap<>();
        bp18.put(new Vector(0, 0, 0), Material.BONE_BLOCK);
        bp18.put(new Vector(1, 0, 0), Material.BONE_BLOCK);
        bp18.put(new Vector(0, 0, 1), Material.BONE_BLOCK);
        bp18.put(new Vector(1, 0, 1), Material.SKELETON_SKULL);
        blueprints.add(bp18);


        Map<Vector, Material> bp19 = new HashMap<>();
        bp19.put(new Vector(0, 0, 0), Material.SCULK_CATALYST);
        bp19.put(new Vector(0, 1, 0), Material.SCULK_SHRIEKER);
        bp19.put(new Vector(1, 0, 0), Material.SCULK_VEIN);
        bp19.put(new Vector(-1, 0, 0), Material.SCULK_VEIN);
        bp19.put(new Vector(0, 0, 1), Material.SCULK_VEIN);
        bp19.put(new Vector(0, 0, -1), Material.SCULK_VEIN);
        bp19.put(new Vector(0, -1, 0), Material.SCULK_VEIN);
        blueprints.add(bp19);


        Map<Vector, Material> bp20 = new HashMap<>();

        bp20.put(new Vector(0, 0, 0), Material.BLACKSTONE);
        bp20.put(new Vector(1, 0, 0), Material.BLACKSTONE);
        bp20.put(new Vector(2, 0, 0), Material.BLACKSTONE);
        bp20.put(new Vector(0, 0, 1), Material.BLACKSTONE);
        bp20.put(new Vector(1, 0, 1), Material.BLACKSTONE);
        bp20.put(new Vector(2, 0, 1), Material.BLACKSTONE);

        bp20.put(new Vector(0, 1, 0), Material.DEEPSLATE_BRICKS);
        bp20.put(new Vector(0, 1, 1), Material.SPRUCE_STAIRS);
        bp20.put(new Vector(2, 1, 0), Material.DEEPSLATE_BRICKS);
        bp20.put(new Vector(2, 1, 1), Material.SPRUCE_STAIRS);
        blueprints.add(bp20);


        Map<Vector, Material> bp21 = new HashMap<>();
        bp21.put(new Vector(0, 0, 0), Material.NETHERRACK);
        bp21.put(new Vector(0, 1, 0), Material.NETHERRACK);
        bp21.put(new Vector(0, 2, 0), Material.NETHERRACK);
        bp21.put(new Vector(-1, 1, 0), Material.NETHERRACK);
        bp21.put(new Vector(1, 1, 0), Material.NETHERRACK);
        blueprints.add(bp21);


        Map<Vector, Material> bp22 = new HashMap<>();

        bp22.put(new Vector(-1, 0, -1), Material.GOLD_BLOCK);
        bp22.put(new Vector(0, 0, -1), Material.GOLD_BLOCK);
        bp22.put(new Vector(1, 0, -1), Material.GOLD_BLOCK);
        bp22.put(new Vector(-1, 0, 0), Material.GOLD_BLOCK);
        bp22.put(new Vector(0, 0, 0), Material.GOLD_BLOCK);
        bp22.put(new Vector(1, 0, 0), Material.GOLD_BLOCK);
        bp22.put(new Vector(-1, 0, 1), Material.GOLD_BLOCK);
        bp22.put(new Vector(0, 0, 1), Material.GOLD_BLOCK);
        bp22.put(new Vector(1, 0, 1), Material.GOLD_BLOCK);

        bp22.put(new Vector(0, 1, 0), Material.GOLD_BLOCK);

        bp22.put(new Vector(0, 2, 0), Material.FIRE);
        blueprints.add(bp22);


        Map<Vector, Material> bp23 = new HashMap<>();
        bp23.put(new Vector(0, 0, 0), Material.OBSIDIAN);
        bp23.put(new Vector(0, 1, 0), Material.OBSIDIAN);
        bp23.put(new Vector(0, 2, 0), Material.OBSIDIAN);
        bp23.put(new Vector(0, 3, 0), Material.OBSIDIAN);
        bp23.put(new Vector(0, 4, 0), Material.FIRE);
        blueprints.add(bp23);


        Map<Vector, Material> bp24 = new HashMap<>();

        bp24.put(new Vector(0, 0, 0), Material.COBBLED_DEEPSLATE);
        bp24.put(new Vector(1, 0, 0), Material.COBBLED_DEEPSLATE);
        bp24.put(new Vector(2, 0, 0), Material.COBBLED_DEEPSLATE);
        bp24.put(new Vector(0, 1, 0), Material.COBBLED_DEEPSLATE);
        bp24.put(new Vector(1, 1, 0), Material.COBBLED_DEEPSLATE);
        bp24.put(new Vector(2, 1, 0), Material.COBBLED_DEEPSLATE);

        bp24.put(new Vector(0, 1, 1), Material.REDSTONE_TORCH);
        bp24.put(new Vector(2, 1, 1), Material.REDSTONE_TORCH);
        bp24.put(new Vector(1, 0, 1), Material.REDSTONE_TORCH);
        blueprints.add(bp24);


        Map<Vector, Material> bp25 = new HashMap<>();

        bp25.put(new Vector(-1, 0, -1), Material.BLACKSTONE);
        bp25.put(new Vector(0, 0, -1), Material.BLACKSTONE);
        bp25.put(new Vector(1, 0, -1), Material.BLACKSTONE);
        bp25.put(new Vector(-1, 0, 0), Material.BLACKSTONE);
        bp25.put(new Vector(0, 0, 0), Material.SOUL_SAND);
        bp25.put(new Vector(1, 0, 0), Material.BLACKSTONE);
        bp25.put(new Vector(-1, 0, 1), Material.BLACKSTONE);
        bp25.put(new Vector(0, 0, 1), Material.BLACKSTONE);
        bp25.put(new Vector(1, 0, 1), Material.BLACKSTONE);

        bp25.put(new Vector(0, 1, 0), Material.FIRE);
        blueprints.add(bp25);


        Map<Vector, Material> bp26 = new HashMap<>();
        bp26.put(new Vector(0, 0, 0), Material.DEEPSLATE_BRICKS);
        bp26.put(new Vector(1, 0, 0), Material.DEEPSLATE_BRICKS);
        bp26.put(new Vector(0, 1, 0), Material.CHISELED_DEEPSLATE);
        bp26.put(new Vector(1, 1, 0), Material.CHISELED_DEEPSLATE);
        blueprints.add(bp26);

        Map<Vector, Material> bp27_revised = new HashMap<>();
        bp27_revised.put(new Vector(0, 0, 0), Material.BLACK_CONCRETE);
        bp27_revised.put(new Vector(1, 1, 0), Material.IRON_BARS);
        bp27_revised.put(new Vector(-1, 1, 0), Material.IRON_BARS);
        bp27_revised.put(new Vector(0, 1, 1), Material.IRON_BARS);
        bp27_revised.put(new Vector(0, 1, -1), Material.IRON_BARS);
        bp27_revised.put(new Vector(1, 2, 0), Material.IRON_BARS);
        bp27_revised.put(new Vector(-1, 2, 0), Material.IRON_BARS);
        bp27_revised.put(new Vector(0, 2, 1), Material.IRON_BARS);
        bp27_revised.put(new Vector(0, 2, -1), Material.IRON_BARS);
        bp27_revised.put(new Vector(0, 1, 0), Material.WITHER_SKELETON_SKULL);
        blueprints.add(bp27_revised);


        STRUCTURE_BLUEPRINTS = Collections.unmodifiableList(blueprints);

    }

    private static double getMaxHorizontalOffset(@NotNull Map<Vector, Material> blueprint) {
        double minYOffset = 0;
        double maxYOffset = 0;
        double maxHorizontalOffset = 10;
        for (Vector offset : blueprint.keySet()) {
            if (offset.getY() < minYOffset) minYOffset = offset.getY();
            if (offset.getY() > maxYOffset) maxYOffset = offset.getY();
            double hDist = Math.sqrt(offset.getX() * offset.getX() + offset.getZ() * offset.getZ());
            if (hDist > maxHorizontalOffset) maxHorizontalOffset = hDist;
        }
        return maxHorizontalOffset;
    }

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


        World world = player.getWorld();
        return player.isOnline() && (
                world.getEnvironment() == World.Environment.NORMAL || world.getEnvironment() == World.Environment.NETHER || world.getEnvironment() == World.Environment.THE_END
        );
    }

    /**
     * Triggers the visual structure event.
     * Finds a suitable location, sends BLOCK_CHANGE packets to create a small structure,
     * and schedules a revert task to restore original blocks.
     *
     * @param player The player to show the structure to.
     * @param plugin The main plugin instance.
     */
    @Override
    public void trigger(Player player, Desync plugin) {
        Logger logger = plugin.getPluginLogger();
        ProtocolManager protocolManager = plugin.getProtocolManager();
        ThreadLocalRandom random = ThreadLocalRandom.current();

        if (STRUCTURE_BLUEPRINTS.isEmpty()) {
            logger.warn("VisualStructure trigger for {}: STRUCTURE_BLUEPRINTS list is empty!", player.getName());
            return;
        }


        Map<Vector, Material> blueprint = STRUCTURE_BLUEPRINTS.get(random.nextInt(STRUCTURE_BLUEPRINTS.size()));


        Location placementBase = findPlacementLocation(player, blueprint, plugin);
        if (placementBase == null) {
            logger.debug("VisualStructure trigger for {}: Could not find a suitable placement location.", player.getName());
            return;
        }

        logger.debug("Triggering VisualStructure for {} near {}", player.getName(), placementBase.toVector());


        Map<Location, BlockData> originalBlockData = new HashMap<>();
        List<PacketContainer> placePackets = new ArrayList<>();


        for (Map.Entry<Vector, Material> entry : blueprint.entrySet()) {
            Vector offset = entry.getKey();
            Material materialToPlace = entry.getValue();


            Location blockLoc = placementBase.clone().add(offset);


            if (blockLoc.getWorld() == null || !blockLoc.getWorld().isChunkLoaded(blockLoc.getChunk())) {
                logger.debug("Skipping block placement for {} at {} - chunk not loaded.", materialToPlace, blockLoc.toVector());
                continue;
            }

            Block blockAtLocation = blockLoc.getBlock();

            originalBlockData.put(blockLoc.clone(), blockAtLocation.getBlockData());


            try {
                PacketContainer blockChangePacket = protocolManager.createPacket(PacketType.Play.Server.BLOCK_CHANGE);
                blockChangePacket.getBlockPositionModifier().write(0, new BlockPosition(blockLoc.getBlockX(), blockLoc.getBlockY(), blockLoc.getBlockZ()));

                BlockData blockDataToPlace;

                if (materialToPlace == Material.OAK_DOOR) {


                    if (offset.getY() == 1) {
                        blockDataToPlace = Bukkit.createBlockData(materialToPlace, "[facing=south,half=upper,hinge=left,open=false]");
                    } else {
                        blockDataToPlace = Bukkit.createBlockData(materialToPlace, "[facing=south,half=lower,hinge=left,open=false]");
                    }

                } else {
                    blockDataToPlace = Bukkit.createBlockData(materialToPlace);
                }


                blockChangePacket.getBlockData().write(0, WrappedBlockData.createData(blockDataToPlace));
                placePackets.add(blockChangePacket);
                logger.debug("Prepared packet to place {} at {}", materialToPlace, blockLoc.toVector());

            } catch (Exception e) {
                logger.error("Failed to prepare BLOCK_CHANGE packet for {} at {}: {}", materialToPlace, blockLoc.toVector(), e.getMessage());


            }
        }


        if (placePackets.isEmpty()) {
            logger.debug("VisualStructure trigger for {}: No valid place packets generated.", player.getName());
            return;
        }


        try {
            for (PacketContainer packet : placePackets) {
                protocolManager.sendServerPacket(player, packet);
            }
            logger.debug("Sent {} placement packets to {}", placePackets.size(), player.getName());


            final Map<Location, BlockData> finalOriginalBlockData = originalBlockData;

            new BukkitRunnable() {
                @Override
                public void run() {
                    if (!player.isOnline()) {
                        logger.debug("VisualStructure skipping revert for {} - player offline.", player.getName());
                        return;
                    }


                    for (Map.Entry<Location, BlockData> entry : finalOriginalBlockData.entrySet()) {
                        Location loc = entry.getKey();
                        BlockData data = entry.getValue();


                        if (loc.getWorld() == null || !loc.getWorld().isChunkLoaded(loc.getChunk())) {
                            logger.debug("Skipping revert for block at {} - chunk not loaded.", loc.toVector());
                            continue;
                        }

                        try {
                            EffectUtils.sendBlockChange(player, loc, data, plugin);
                            logger.debug("VisualStructure reverted block at {} clientside.", loc.toVector());

                        } catch (Exception e) {
                            logger.error("Failed to send VisualStructure revert packet for {} to {}", loc.toVector(), player.getName(), e);
                        }
                    }
                    logger.debug("VisualStructure revert complete for {}", player.getName());
                }
            }.runTaskLater(plugin, STRUCTURE_DURATION_TICKS);


        } catch (Exception e) {
            logger.error("Failed to send initial VisualStructure placement packets to {}", player.getName(), e);

        }
    }

    /**
     * Helper to find a suitable location near the player to place a structure blueprint.
     * Checks for solid ground under the base block and enough air space above the blueprint's extent.
     */
    private Location findPlacementLocation(@NotNull Player player, @NotNull Map<Vector, Material> blueprint, @NotNull Desync plugin) {
        Logger logger = plugin.getPluginLogger();
        Location playerLoc = player.getLocation();
        World world = player.getWorld();
        ThreadLocalRandom random = ThreadLocalRandom.current();

        int attempts = 0;
        int maxAttempts = 50;


        double maxHorizontalOffset = getMaxHorizontalOffset(blueprint);


        Location playerEyeLoc = player.getEyeLocation();


        while (attempts < maxAttempts) {
            attempts++;


            double angle = random.nextDouble() * 2 * Math.PI;
            double distance = random.nextDouble() * (SEARCH_RADIUS - maxHorizontalOffset + 5);
            if (distance < 0) distance = 0;

            double randomX = Math.cos(angle) * distance;
            double randomZ = Math.sin(angle) * distance;


            Location potentialBaseHorizontal = playerLoc.clone().add(randomX, 0, randomZ);


            int startY = potentialBaseHorizontal.getBlockY();
            int verticalSearchRange = 10;
            int minYSearch = Math.max(world.getMinHeight(), startY - verticalSearchRange);
            int maxYSearch = Math.min(world.getMaxHeight(), startY + verticalSearchRange);


            for (int currentY = maxYSearch; currentY >= minYSearch; currentY--) {
                Location potentialBaseAtY = new Location(world,
                        potentialBaseHorizontal.getBlockX() + 0.5,
                        currentY,
                        potentialBaseHorizontal.getBlockZ() + 0.5
                );


                double foundGroundYAbove = EffectUtils.findGroundY(potentialBaseAtY.clone().add(0, 1.0, 0));


                Location potentialBase = new Location(world, potentialBaseAtY.getX(), foundGroundYAbove, potentialBaseAtY.getZ());


                if (potentialBase.getY() < world.getMinHeight() || potentialBase.getY() >= world.getMaxHeight() ||
                        potentialBase.getWorld() == null || !potentialBase.getWorld().isChunkLoaded(potentialBase.getChunk())) {
                    logger.debug("Attempt {}: Location {} outside world bounds or not loaded.", attempts, potentialBase.toVector());
                    continue;
                }


                Block blockBelowBase = potentialBase.clone().subtract(0, 1.0, 0).getBlock();
                if (blockBelowBase.isPassable() || blockBelowBase.isLiquid() || blockBelowBase.getType().hasGravity() || !blockBelowBase.getType().isSolid()) {

                    logger.debug("Attempt {}: Location {} does not have solid ground below base (Block Below: {}).", attempts, potentialBase.toVector(), blockBelowBase.getType());
                    continue;
                }


                boolean isSpaceClear = true;
                double maxBlueprintHeightOffset = 0;
                for (Vector offset : blueprint.keySet()) {
                    if (offset.getY() > 0 && offset.getY() > maxBlueprintHeightOffset)
                        maxBlueprintHeightOffset = offset.getY();
                }


                for (int checkYOffset = 1; checkYOffset <= maxBlueprintHeightOffset; checkYOffset++) {

                    for (Vector offset : blueprint.keySet()) {
                        if (offset.getY() == checkYOffset) {
                            Location checkLoc = potentialBase.clone().add(offset.getX(), checkYOffset, offset.getZ());

                            if (checkLoc.getY() < world.getMinHeight() || checkLoc.getY() >= world.getMaxHeight()) {
                                isSpaceClear = false;
                                logger.debug("Attempt {}: Blueprint block at offset {} is outside world height bounds during space clear check.", attempts, offset);
                                break;
                            }
                            Block blockAtOffset = checkLoc.getBlock();

                            if (blockAtOffset.getType().isSolid() || blockAtOffset.isLiquid()) {
                                isSpaceClear = false;
                                logger.debug("Attempt {}: Space for blueprint block at offset {} is blocked by {} at {}.", attempts, offset, blockAtOffset.getType(), checkLoc.toVector());
                                break;
                            }
                        }
                    }
                    if (!isSpaceClear) break;
                }


                if (isSpaceClear) {


                    if (EffectUtils.hasLineOfSight(playerEyeLoc, potentialBase, plugin)) {

                        logger.debug("Found structure placement location after {} attempts: {}", attempts, potentialBase.toVector());
                        return potentialBase;
                    } else {
                        logger.debug("Attempt {}: Location {} has ground and space but no LOS from player.", attempts, potentialBase.toVector());
                    }
                }
            }
        }

        logger.debug("Could not find suitable VisualStructure placement location for {} after {} attempts.", player.getName(), maxAttempts);
        return null;
    }
}