package com.bambookat.withinreach;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record RequestListPayload() implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<RequestListPayload> TYPE =
            new CustomPacketPayload.Type<>(WithinReach.id("request_list"));

    public static final StreamCodec<RegistryFriendlyByteBuf, RequestListPayload> CODEC =
            StreamCodec.unit(new RequestListPayload());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}