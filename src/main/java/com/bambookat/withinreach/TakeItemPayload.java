package com.bambookat.withinreach;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.item.Item;

public record TakeItemPayload(Item item, boolean wholeStack) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<TakeItemPayload> TYPE =
            new CustomPacketPayload.Type<>(WithinReach.id("take_item"));

    public static final StreamCodec<RegistryFriendlyByteBuf, TakeItemPayload> CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.registry(Registries.ITEM), TakeItemPayload::item,
                    ByteBufCodecs.BOOL, TakeItemPayload::wholeStack,
                    TakeItemPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}