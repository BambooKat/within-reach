package com.bambookat.withinreach;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.RandomizableContainer;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.ShulkerBoxBlock;
import java.util.Collections;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.world.inventory.PlayerEnderChestContainer;
import net.minecraft.network.chat.Component;

public class ContainerScanner {

    // Per ogni giocatore e tipo di item: da quali chest l'ha preso (vale fino al riavvio)
    private static final Map<UUID, Map<Item, Set<BlockPos>>> ORIGINS = new HashMap<>();

    private static final TagKey<Block> STORAGE = TagKey.create(Registries.BLOCK, WithinReach.id("storage"));
    private static final TagKey<Block> MACHINES = TagKey.create(Registries.BLOCK, WithinReach.id("machines"));
    private static final BlockPos ENDER_KEY = new BlockPos(0, -2048, 0);

    private static boolean isStorage(BlockEntity be) {
        if (be instanceof TrappedChestBlockEntity) return false;
        return be instanceof ChestBlockEntity || be instanceof BarrelBlockEntity
                || be instanceof ShulkerBoxBlockEntity || be.getBlockState().is(STORAGE);
    }

    private static boolean isMachine(BlockEntity be) {
        return be.getBlockState().is(MACHINES);
    }

    private static boolean isMachineContainer(Container c) {
        return c instanceof BlockEntity be && isMachine(be);
    }

    // per le macchine: solo gli slot che un imbuto sotto potrebbe estrarre
    private static boolean readable(Container c, int slot) {
        if (!isMachineContainer(c)) return true;
        if (!(c instanceof WorldlyContainer w)) return false;
        for (int s : w.getSlotsForFace(Direction.DOWN)) {
            if (s == slot) return w.canTakeItemThroughFace(slot, c.getItem(slot), Direction.DOWN);
        }
        return false;
    }

    public static List<Container> find(ServerPlayer player, int radius) {
        ServerLevel level = (ServerLevel) player.level();
        BlockPos center = player.blockPosition();
        List<Container> result = new ArrayList<>();
        boolean enderNear = false;
        int range = (radius >> 4) + 1;
        int cx0 = center.getX() >> 4;
        int cz0 = center.getZ() >> 4;

        for (int cx = cx0 - range; cx <= cx0 + range; cx++) {
            for (int cz = cz0 - range; cz <= cz0 + range; cz++) {
                LevelChunk chunk = level.getChunkSource().getChunkNow(cx, cz);
                if (chunk == null) continue;
                for (BlockEntity be : chunk.getBlockEntities().values()) {
                    if (be instanceof EnderChestBlockEntity) {
                        BlockPos ep = be.getBlockPos();
                        if (Math.abs(ep.getX() - center.getX()) <= radius
                                && Math.abs(ep.getY() - center.getY()) <= radius
                                && Math.abs(ep.getZ() - center.getZ()) <= radius) {
                            enderNear = true;
                        }
                        continue;
                    }
                    if (!(be instanceof Container)) continue;
                    if (!isStorage(be) && !isMachine(be)) continue;
                    BlockPos p = be.getBlockPos();
                    if (Math.abs(p.getX() - center.getX()) > radius
                            || Math.abs(p.getY() - center.getY()) > radius
                            || Math.abs(p.getZ() - center.getZ()) > radius) continue;
                    if (be instanceof BaseContainerBlockEntity locked && !locked.canOpen(player)) continue;
                    if (be instanceof RandomizableContainer loot && loot.getLootTable() != null) continue;
                    result.add((Container) be);
                }
            }
        }
        if (enderNear) result.add(player.getEnderChestInventory());

        return result;
    }

    public static List<InventoryListPayload.Entry> summarize(List<Container> containers) {
        Map<Item, Integer> totals = new LinkedHashMap<>();
        Map<Item, Map<Component, Integer>> sources = new HashMap<>();
        for (Container c : containers) {
            Component name = nameOf(c);
            for (int i = 0; i < c.getContainerSize(); i++) {
                ItemStack s = c.getItem(i);
                if (!s.isEmpty() && readable(c, i)) {
                    totals.merge(s.getItem(), s.getCount(), Integer::sum);
                    sources.computeIfAbsent(s.getItem(), k -> new LinkedHashMap<>())
                            .merge(name, s.getCount(), Integer::sum);
                }
            }
        }
        List<InventoryListPayload.Entry> entries = new ArrayList<>();
        totals.forEach((item, count) -> {
            List<InventoryListPayload.Source> list = new ArrayList<>();
            sources.get(item).forEach((n, cnt) -> list.add(new InventoryListPayload.Source(n, cnt)));
            entries.add(new InventoryListPayload.Entry(item, count, list));
        });
        return entries;
    }

    private static Component nameOf(Container c) {
        if (c instanceof PlayerEnderChestContainer) return Component.translatable("container.enderchest");
        if (c instanceof BaseContainerBlockEntity named) return named.getDisplayName();
        return Component.translatable("container.chest");
    }

    public static void take(ServerPlayer player, Item item, int amount, int radius, boolean toHand) {
        int remaining = amount;
        for (Container c : find(player, radius)) {
            boolean tookFromHere = false;
            for (int i = 0; i < c.getContainerSize() && remaining > 0; i++) {
                ItemStack s = c.getItem(i);
                if (s.isEmpty() || !s.is(item) || !readable(c, i)) continue;
                ItemStack taken = c.removeItem(i, Math.min(remaining, s.getCount()));
                remaining -= taken.getCount();
                c.setChanged();
                tookFromHere = true;
                give(player, taken, toHand);
            }
            if (tookFromHere && !isMachineContainer(c)) remember(player, item, c);
            if (remaining <= 0) break;
        }
    }

    private static void give(ServerPlayer player, ItemStack taken, boolean toHand) {
        if (toHand) {
            ItemStack carried = player.containerMenu.getCarried();
            if (carried.isEmpty()) {
                player.containerMenu.setCarried(taken);
                return;
            }
            if (ItemStack.isSameItemSameComponents(carried, taken)) {
                int n = Math.min(taken.getCount(), carried.getMaxStackSize() - carried.getCount());
                carried.grow(n);
                taken.shrink(n);
            }
            if (taken.isEmpty()) return;
        }
        if (!player.getInventory().add(taken)) {
            player.drop(taken, false);
        }
    }

    private static void remember(ServerPlayer player, Item item, Container c) {
        BlockPos pos;
        if (c instanceof PlayerEnderChestContainer) pos = ENDER_KEY;
        else if (c instanceof BlockEntity be) pos = be.getBlockPos().immutable();
        else return;
        Set<BlockPos> set = ORIGINS.computeIfAbsent(player.getUUID(), k -> new HashMap<>())
                .computeIfAbsent(item, k -> new LinkedHashSet<>());
        set.remove(pos);
        set.add(pos); // l'ultimo usato sta in fondo
    }

    public static void forget(UUID playerId) {
        ORIGINS.remove(playerId);
    }

    public static void load(Path file) {
        ORIGINS.clear();
        if (!Files.exists(file)) return;
        try {
            JsonObject root = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
            for (var playerEntry : root.entrySet()) {
                UUID player = UUID.fromString(playerEntry.getKey());
                Map<Item, Set<BlockPos>> items = new HashMap<>();
                for (var itemEntry : playerEntry.getValue().getAsJsonObject().entrySet()) {
                    Identifier id = Identifier.tryParse(itemEntry.getKey());
                    if (id == null || !BuiltInRegistries.ITEM.containsKey(id)) continue;
                    Set<BlockPos> positions = new LinkedHashSet<>();
                    for (JsonElement el : itemEntry.getValue().getAsJsonArray()) {
                        positions.add(BlockPos.of(el.getAsLong()));
                    }
                    items.put(BuiltInRegistries.ITEM.getValue(id), positions);
                }
                ORIGINS.put(player, items);
            }
        } catch (IOException | RuntimeException e) {
            WithinReach.LOGGER.warn("Could not read withinreach_origins.json", e);
        }
    }

    public static void save(Path file) {
        JsonObject root = new JsonObject();
        ORIGINS.forEach((player, items) -> {
            JsonObject obj = new JsonObject();
            items.forEach((item, positions) -> {
                JsonArray arr = new JsonArray();
                positions.forEach(p -> arr.add(p.asLong()));
                obj.add(BuiltInRegistries.ITEM.getKey(item).toString(), arr);
            });
            root.add(player.toString(), obj);
        });
        try {
            Files.writeString(file, root.toString());
        } catch (IOException e) {
            WithinReach.LOGGER.warn("Could not write withinreach_origins.json", e);
        }
    }

    public static ItemStack deposit(ServerPlayer player, ItemStack stack, int radius) {
        ItemStack remaining = stack.copy();
        List<Container> all = find(player, radius);
        all.removeIf(ContainerScanner::isMachineContainer);

        // 1) le chest da cui l'hai preso
        Set<BlockPos> origins = ORIGINS.getOrDefault(player.getUUID(), Map.of())
                .getOrDefault(stack.getItem(), Set.of());
        List<BlockPos> order = new ArrayList<>(origins);
        Collections.reverse(order); // dal più recente
        List<Container> originContainers = new ArrayList<>();
        for (BlockPos pos : order) {
            for (Container c : all) {
                if ((c instanceof BlockEntity be && be.getBlockPos().equals(pos))
                        || (pos.equals(ENDER_KEY) && c instanceof PlayerEnderChestContainer)) {
                    originContainers.add(c);
                }
            }
        }
        for (Container c : originContainers) {
            if (remaining.isEmpty()) break;
            insertInto(List.of(c), remaining);
        }

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

    private static boolean canPlace(Container c, int slot, ItemStack stack) {
        if (c instanceof ShulkerBoxBlockEntity && Block.byItem(stack.getItem()) instanceof ShulkerBoxBlock) return false;
        if (c instanceof WorldlyContainer w) return w.canPlaceItemThroughFace(slot, stack, Direction.UP);
        return c.canPlaceItem(slot, stack);
    }

    private static void insertInto(List<Container> containers, ItemStack remaining) {
        // completa gli stack già presenti
        for (Container c : containers) {
            for (int i = 0; i < c.getContainerSize() && !remaining.isEmpty(); i++) {
                ItemStack s = c.getItem(i);
                if (!s.isEmpty() && ItemStack.isSameItemSameComponents(s, remaining) && canPlace(c, i, remaining)) {
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
                if (c.getItem(i).isEmpty() && canPlace(c, i, remaining)) {
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