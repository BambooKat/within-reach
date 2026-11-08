package com.bambookat.withinreach;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.lang.reflect.Method;

/**
 * Zaino indossato di Traveler's Backpack, letto via reflection: nessuna dipendenza in compilazione,
 * e se TB non c'è (o cambia API) semplicemente non fa nulla.
 */
public final class TravelersBackpackCompat {
    private static final boolean LOADED = FabricLoader.getInstance().isModLoaded("travelersbackpack");
    private static boolean initDone, broken;
    private static Method getWrapper;   // AttachmentUtils.getBackpackWrapper(Player)
    private static Method getStorageIO; // BackpackWrapper.getStorageForInputOutput()
    private static Method getStack;     // BackpackWrapper.getBackpackStack()

    private TravelersBackpackCompat() {}

    private static void init() {
        initDone = true;
        try {
            Class<?> utils = Class.forName("com.tiviacz.travelersbackpack.attachment.AttachmentUtils");
            getWrapper = utils.getMethod("getBackpackWrapper", Player.class);
            Class<?> wrapper = getWrapper.getReturnType();
            getStorageIO = wrapper.getMethod("getStorageForInputOutput");
            getStack = wrapper.getMethod("getBackpackStack");
        } catch (ReflectiveOperationException | RuntimeException e) {
            broken = true;
            WithinReach.LOGGER.warn("Traveler's Backpack found, but its API changed: worn backpack won't be listed", e);
        }
    }

    /** Lo zaino che il giocatore ha addosso, come Container; null se non ce l'ha o se TB non c'è. */
    public static Container worn(ServerPlayer player) {
        if (!LOADED) return null;
        if (!initDone) init();
        if (broken) return null;
        try {
            Object wrapper = getWrapper.invoke(null, player);
            if (wrapper == null) return null;
            Object storage = getStorageIO.invoke(wrapper);
            if (!(storage instanceof Container c)) return null;
            ItemStack stack = (ItemStack) getStack.invoke(wrapper);
            return new Worn(c, stack.getHoverName());
        } catch (ReflectiveOperationException | RuntimeException e) {
            broken = true;
            WithinReach.LOGGER.warn("Could not read the worn Traveler's Backpack", e);
            return null;
        }
    }

    /** Involucro riconoscibile (per nome e "rimetti dove l'hai preso"), delega tutto allo zaino. */
    public record Worn(Container inner, Component name) implements Container {
        @Override public int getContainerSize() { return inner.getContainerSize(); }
        @Override public boolean isEmpty() { return inner.isEmpty(); }
        @Override public ItemStack getItem(int slot) { return inner.getItem(slot); }
        @Override public ItemStack removeItem(int slot, int amount) { return inner.removeItem(slot, amount); }
        @Override public ItemStack removeItemNoUpdate(int slot) { return inner.removeItemNoUpdate(slot); }
        @Override public void setItem(int slot, ItemStack stack) { inner.setItem(slot, stack); }
        @Override public int getMaxStackSize() { return inner.getMaxStackSize(); }
        @Override public boolean canPlaceItem(int slot, ItemStack stack) { return inner.canPlaceItem(slot, stack); }
        @Override public void setChanged() { inner.setChanged(); }
        @Override public boolean stillValid(Player player) { return inner.stillValid(player); }
        @Override public void clearContent() { }
    }
}
