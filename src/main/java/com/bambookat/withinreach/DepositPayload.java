package com.bambookat.withinreach;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record DepositPayload(boolean wholeStack) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<DepositPayload> TYPE =
            new CustomPacketPayload.Type<>(WithinReach.id("deposit"));

    public static final StreamCodec<RegistryFriendlyByteBuf, DepositPayload> CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.BOOL, DepositPayload::wholeStack,
                    DepositPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}