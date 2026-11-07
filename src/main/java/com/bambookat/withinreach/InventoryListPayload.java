package com.bambookat.withinreach;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.item.Item;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;

import java.util.List;

public record InventoryListPayload(List<Entry> entries) implements CustomPacketPayload {

    public record Source(Component name, int count) {
        public static final StreamCodec<RegistryFriendlyByteBuf, Source> CODEC = StreamCodec.composite(
                ComponentSerialization.STREAM_CODEC, Source::name,
                ByteBufCodecs.VAR_INT, Source::count,
                Source::new);
    }

    public record Entry(Item item, int count, List<Source> sources) {
        public static final StreamCodec<RegistryFriendlyByteBuf, Entry> CODEC = StreamCodec.composite(
                ByteBufCodecs.registry(Registries.ITEM), Entry::item,
                ByteBufCodecs.VAR_INT, Entry::count,
                Source.CODEC.apply(ByteBufCodecs.list()), Entry::sources,
                Entry::new);
    }

    public static final CustomPacketPayload.Type<InventoryListPayload> TYPE =
            new CustomPacketPayload.Type<>(WithinReach.id("inventory_list"));

    public static final StreamCodec<RegistryFriendlyByteBuf, InventoryListPayload> CODEC =
            StreamCodec.composite(
                    Entry.CODEC.apply(ByteBufCodecs.list()), InventoryListPayload::entries,
                    InventoryListPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}