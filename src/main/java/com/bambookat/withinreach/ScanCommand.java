package com.bambookat.withinreach;

import com.mojang.brigadier.CommandDispatcher;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;

public class ScanCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("wrscan").executes(ctx -> {
            ServerPlayer player = ctx.getSource().getPlayer();
            if (player == null) return 0;
            List<InventoryListPayload.Entry> entries =
                    ContainerScanner.summarize(ContainerScanner.find(player, WithinReach.RADIUS));
            ServerPlayNetworking.send(player, new InventoryListPayload(entries));
            return entries.size();
        }));
    }
}