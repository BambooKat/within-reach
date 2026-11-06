package com.bambookat.withinreach;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class WithinReach implements ModInitializer {
	public static final String MOD_ID = "withinreach";
	public static int RADIUS = WithinReachConfig.DEFAULT_RADIUS;

	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	private static final Map<UUID, Long> LAST_REQUEST = new HashMap<>();

	@Override
	public void onInitialize() {

		WithinReachConfig.load();
		RADIUS = WithinReachConfig.radius();

		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			ContainerScanner.forget(handler.getPlayer().getUUID());
			LAST_REQUEST.remove(handler.getPlayer().getUUID());
		});

		PayloadTypeRegistry.clientboundPlay().register(
				InventoryListPayload.TYPE, InventoryListPayload.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(
				TakeItemPayload.TYPE, TakeItemPayload.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(
				DepositPayload.TYPE, DepositPayload.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(
				RequestListPayload.TYPE, RequestListPayload.CODEC);

		ServerPlayNetworking.registerGlobalReceiver(TakeItemPayload.TYPE, (payload, context) -> {
			ServerPlayer player = context.player();
			if (!allowed(player)) return;
			int amount = payload.wholeStack() ? new ItemStack(payload.item()).getMaxStackSize() : 1;
			ContainerScanner.take(player, payload.item(), amount, RADIUS);
			sendList(player);
		});

		ServerPlayNetworking.registerGlobalReceiver(DepositPayload.TYPE, (payload, context) -> {
			ServerPlayer player = context.player();
			if (!allowed(player)) return;
			ItemStack carried = player.containerMenu.getCarried();
			if (carried.isEmpty()) return;

			if (payload.wholeStack()) {
				ItemStack rest = ContainerScanner.deposit(player, carried, RADIUS);
				player.containerMenu.setCarried(rest);
			} else {
				ItemStack rest = ContainerScanner.deposit(player, carried.copyWithCount(1), RADIUS);
				if (rest.isEmpty()) carried.shrink(1);
			}
			player.containerMenu.broadcastChanges();
			sendList(player);
		});

		ServerPlayNetworking.registerGlobalReceiver(RequestListPayload.TYPE, (payload, context) -> {
			if (allowed(context.player())) sendList(context.player());
		});
	}

	private static boolean allowed(ServerPlayer player) {
		long now = System.nanoTime();
		Long last = LAST_REQUEST.get(player.getUUID());
		if (last != null && now - last < 50_000_000L) return false;
		LAST_REQUEST.put(player.getUUID(), now);
		return true;
	}

	private static void sendList(ServerPlayer player) {
		ServerPlayNetworking.send(player, new InventoryListPayload(
				ContainerScanner.summarize(ContainerScanner.find(player, RADIUS))));
	}

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}
}