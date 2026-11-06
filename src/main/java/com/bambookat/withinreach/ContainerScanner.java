package com.bambookat.withinreach;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class ContainerScanner {

    // Per ogni giocatore e tipo di item: da quali chest l'ha preso (vale fino al riavvio)
    private static final Map<UUID, Map<Item, Set<BlockPos>>> ORIGINS = new HashMap<>();

    public static List<Container> find(ServerPlayer player, int radius) {
        ServerLevel level = (ServerLevel) player.level();
        BlockPos center = player.blockPosition();
        List<Container> result = new ArrayList<>();
        int range = (radius >> 4) + 1;
        int cx0 = center.getX() >> 4;
        int cz0 = center.getZ() >> 4;

        for (int cx = cx0 - range; cx <= cx0 + range; cx++) {
            for (int cz = cz0 - range; cz <= cz0 + range; cz++) {
                LevelChunk chunk = level.getChunkSource().getChunkNow(cx, cz);
                if (chunk == null) continue;
                for (BlockEntity be : chunk.getBlockEntities().values()) {
                    if (!(be instanceof ChestBlockEntity || be instanceof BarrelBlockEntity)) continue;
                    BlockPos p = be.getBlockPos();
                    if (Math.abs(p.getX() - center.getX()) > radius
                            || Math.abs(p.getY() - center.getY()) > radius
                            || Math.abs(p.getZ() - center.getZ()) > radius) continue;
                    result.add((Container) be);
                }
            }
        }
        return result;
    }

    public static List<InventoryListPayload.Entry> summarize(List<Container> containers) {
        Map<Item, Integer> totals = new LinkedHashMap<>();
        for (Container c : containers) {
            for (int i = 0; i < c.getContainerSize(); i++) {
                ItemStack s = c.getItem(i);
                if (!s.isEmpty()) totals.merge(s.getItem(), s.getCount(), Integer::sum);
            }
        }
        List<InventoryListPayload.Entry> entries = new ArrayList<>();
        totals.forEach((item, count) -> entries.add(new InventoryListPayload.Entry(item, count)));
        return entries;
    }

    public static void take(ServerPlayer player, Item item, int amount, int radius) {
        int remaining = amount;
        for (Container c : find(player, radius)) {
            boolean tookFromHere = false;
            for (int i = 0; i < c.getContainerSize() && remaining > 0; i++) {
                ItemStack s = c.getItem(i);
                if (s.isEmpty() || !s.is(item)) continue;
                ItemStack taken = c.removeItem(i, Math.min(remaining, s.getCount()));
                remaining -= taken.getCount();
                c.setChanged();
                tookFromHere = true;
                if (!player.getInventory().add(taken)) {
                    player.drop(taken, false);
                }
            }
            if (tookFromHere) remember(player, item, c);
            if (remaining <= 0) break;
        }
    }

    private static void remember(ServerPlayer player, Item item, Container c) {
        if (!(c instanceof BlockEntity be)) return;
        ORIGINS.computeIfAbsent(player.getUUID(), k -> new HashMap<>())
                .computeIfAbsent(item, k -> new LinkedHashSet<>())
                .add(be.getBlockPos().immutable());
    }

    public static ItemStack deposit(ServerPlayer player, ItemStack stack, int radius) {
        ItemStack remaining = stack.copy();
        List<Container> all = find(player, radius);

        // 1) le chest da cui l'hai preso
        Set<BlockPos> origins = ORIGINS.getOrDefault(player.getUUID(), Map.of())
                .getOrDefault(stack.getItem(), Set.of());
        List<Container> originContainers = new ArrayList<>();
        for (Container c : all) {
            if (c instanceof BlockEntity be && origins.contains(be.getBlockPos())) {
                originContainers.add(c);
            }
        }
        insertInto(originContainers, remaining);

        // 2) ripiego: prima chest che hanno già l'item, poi tutte le altre
        if (!remaining.isEmpty()) {
            List<Container> others = new ArrayList<>(all);
            others.removeAll(originContainers);
            List<Container> ordered = new ArrayList<>();
            for (Container c : others) {
                if (containsItem(c, remaining)) ordered.add(c);
            }
            for (Container c : others) {
                if (!ordered.contains(c)) ordered.add(c);
            }
            insertInto(ordered, remaining);
        }
        return remaining;
    }

    private static void insertInto(List<Container> containers, ItemStack remaining) {
        // completa gli stack già presenti
        for (Container c : containers) {
            for (int i = 0; i < c.getContainerSize() && !remaining.isEmpty(); i++) {
                ItemStack s = c.getItem(i);
                if (!s.isEmpty() && ItemStack.isSameItemSameComponents(s, remaining)) {
                    int space = Math.min(c.getMaxStackSize(), s.getMaxStackSize()) - s.getCount();
                    int n = Math.min(space, remaining.getCount());
                    if (n > 0) {
                        s.grow(n);
                        remaining.shrink(n);
                        c.setChanged();
                    }
                }
            }
        }
        // poi gli slot vuoti
        for (Container c : containers) {
            for (int i = 0; i < c.getContainerSize() && !remaining.isEmpty(); i++) {
                if (c.getItem(i).isEmpty()) {
                    int n = Math.min(remaining.getCount(), remaining.getMaxStackSize());
                    c.setItem(i, remaining.split(n));
                    c.setChanged();
                }
            }
        }
    }

    private static boolean containsItem(Container c, ItemStack stack) {
        for (int i = 0; i < c.getContainerSize(); i++) {
            ItemStack s = c.getItem(i);
            if (!s.isEmpty() && s.is(stack.getItem())) return true;
        }
        return false;
    }
}