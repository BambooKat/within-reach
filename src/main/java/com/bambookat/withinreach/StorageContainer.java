package com.bambookat.withinreach;

import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.SlottedStorage;
import net.fabricmc.fabric.api.transfer.v1.storage.StorageView;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Container;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * Fa sembrare un Container vanilla i depositi delle mod che usano la Transfer API di Fabric
 * (es. gli zaini di Traveler's Backpack), così il resto dello scanner non deve cambiare.
 * Lettura e prelievo passano dagli slot; il deposito usa insert(), perché getItem() restituisce copie.
 */
public class StorageContainer implements Container {
    private final SlottedStorage<ItemVariant> storage;
    private final BlockEntity blockEntity;

    public StorageContainer(SlottedStorage<ItemVariant> storage, BlockEntity blockEntity) {
        this.storage = storage;
        this.blockEntity = blockEntity;
    }

    public BlockEntity blockEntity() {
        return blockEntity;
    }

    public Component name() {
        if (blockEntity instanceof MenuProvider named) return named.getDisplayName();
        return blockEntity.getBlockState().getBlock().getName();
    }

    /** Inserisce quanto possibile e restituisce quanti item sono entrati. */
    public int insert(ItemStack stack) {
        if (stack.isEmpty()) return 0;
        try (Transaction t = Transaction.openOuter()) {
            long inserted = storage.insert(ItemVariant.of(stack), stack.getCount(), t);
            t.commit();
            if (inserted > 0) blockEntity.setChanged();
            return (int) inserted;
        }
    }

    @Override
    public int getContainerSize() {
        return storage.getSlotCount();
    }

    @Override
    public boolean isEmpty() {
        for (int i = 0; i < getContainerSize(); i++) {
            if (!getItem(i).isEmpty()) return false;
        }
        return true;
    }

    @Override
    public ItemStack getItem(int slot) {
        StorageView<ItemVariant> view = storage.getSlot(slot);
        if (view.isResourceBlank() || view.getAmount() <= 0) return ItemStack.EMPTY;
        return view.getResource().toStack((int) view.getAmount());
    }

    @Override
    public ItemStack removeItem(int slot, int amount) {
        StorageView<ItemVariant> view = storage.getSlot(slot);
        if (view.isResourceBlank() || amount <= 0) return ItemStack.EMPTY;
        ItemVariant resource = view.getResource();
        try (Transaction t = Transaction.openOuter()) {
            long extracted = view.extract(resource, amount, t);
            t.commit();
            if (extracted > 0) blockEntity.setChanged();
            return resource.toStack((int) extracted);
        }
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        return removeItem(slot, getItem(slot).getCount());
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
        // Non usato: per depositare c'è insert(). Qui solo per completezza, su slot vuoti.
        if (stack.isEmpty() || !getItem(slot).isEmpty()) return;
        try (Transaction t = Transaction.openOuter()) {
            storage.getSlot(slot).insert(ItemVariant.of(stack), stack.getCount(), t);
            t.commit();
        }
    }

    @Override
    public void setChanged() {
        blockEntity.setChanged();
    }

    @Override
    public boolean stillValid(Player player) {
        return !blockEntity.isRemoved();
    }

    @Override
    public void clearContent() {
        // mai svuotare un deposito di un'altra mod
    }
}
