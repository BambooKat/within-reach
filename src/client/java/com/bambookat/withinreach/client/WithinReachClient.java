package com.bambookat.withinreach.client;

import com.bambookat.withinreach.InventoryListPayload;
import com.bambookat.withinreach.RequestListPayload;
import com.bambookat.withinreach.client.mixin.AbstractContainerScreenAccessor;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenKeyboardEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.AbstractFurnaceScreen;
import net.minecraft.client.gui.screens.inventory.CraftingScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import com.bambookat.withinreach.client.mixin.CreativeInventoryScreenAccessor;
import net.minecraft.world.item.CreativeModeTab;

public class WithinReachClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			if (client.player == null || !ChestPanelWidget.open) return;
			var screen = client.gui.screen();
			if (!(screen instanceof AbstractContainerScreen<?>) || !panelAllowed(screen)) return;
			if (!ClientPlayNetworking.canSend(RequestListPayload.TYPE)) return;
			if (client.player.tickCount % 10 == 0) {
				ClientPlayNetworking.send(new RequestListPayload());
			}
		});

		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) ->
				ChestPanelWidget.resetOnDisconnect());

		ClientPlayNetworking.registerGlobalReceiver(InventoryListPayload.TYPE, (payload, context) ->
				ChestPanelWidget.setEntries(payload.entries()));

		ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
			if (!(screen instanceof AbstractContainerScreen<?>)) return;
			if (!ClientPlayNetworking.canSend(RequestListPayload.TYPE)) return;

			boolean recipeScreen;
			int bx, by;
			if (screen instanceof InventoryScreen) {
				recipeScreen = true;
				bx = 128;
				by = 61;
			} else if (screen instanceof CraftingScreen) {
				recipeScreen = true;
				bx = 5;
				by = 54;
			} else if (screen instanceof AbstractFurnaceScreen<?>) {
				recipeScreen = true;
				bx = 20;
				by = 54;
			} else {
				recipeScreen = false;
				bx = -22;
				by = 4;
			}

			var accessor = (AbstractContainerScreenAccessor) screen;

			ChestPanelWidget panel = new ChestPanelWidget(0, 0);

			EditBox searchBox = new EditBox(client.font, 0, 0, 80, 14, Component.literal("Search"));
			searchBox.setHint(Component.literal("Search..."));
			searchBox.setMaxLength(40);
			searchBox.setValue(ChestPanelWidget.getFilter());
			searchBox.setResponder(ChestPanelWidget::setFilter);
			searchBox.setBordered(false);
			searchBox.setTextColor(0xFFFFFFFF);

			WrButton button = new WrButton(() -> {
						if (recipeScreen) {
							ChestPanelWidget.open = !ChestPanelWidget.open;
							client.execute(() -> screen.init(scaledWidth, scaledHeight));
						} else {
							panel.toggle();
							if (ChestPanelWidget.open) {
								ClientPlayNetworking.send(new RequestListPayload());
							}
						}
					});

			Runnable reposition = () -> {
				boolean allowed = panelAllowed(screen);
				panel.visible = ChestPanelWidget.open && allowed;
				button.visible = allowed;
				int fullWidth = client.getWindow().getGuiScaledWidth();
				int left = accessor.getLeftPos();
				int top = accessor.getTopPos();
				int panelX = left + accessor.getImageWidth() + 4;
				int panelY = Math.max(0, top - ChestPanelWidget.TABS_H);
				panel.layout(fullWidth - panelX - 4);
				panel.setX(panelX);
				panel.setY(panelY);
				searchBox.setX(panelX + 11);
				searchBox.setY(panelY + ChestPanelWidget.TABS_H + 20);
				searchBox.setWidth(panel.searchWidth() - 8);
				searchBox.visible = panel.visible;
				button.setX(left + bx);
				button.setY(top + by);
			};
			reposition.run();

			Screens.getWidgets(screen).add(panel);
			Screens.getWidgets(screen).add(searchBox);
			Screens.getWidgets(screen).add(button);

			// mentre scrivi nella barra, i tasti (come E) non devono chiudere la schermata
			ScreenKeyboardEvents.allowKeyPress(screen).register((s, event) -> {
				if (searchBox.visible && searchBox.canConsumeInput() && event.key() != 256) {
					searchBox.keyPressed(event);
					return false;
				}
				return true;
			});

			// il rilascio del mouse sopra il pannello non deve arrivare alla schermata vanilla (butterebbe l'oggetto in mano)
			ScreenMouseEvents.allowMouseRelease(screen).register((s, event) ->
					!(panel.visible && panel.isMouseOver(event.x(), event.y())));

			ScreenEvents.beforeExtract(screen).register((s, graphics, mouseX, mouseY, delta) -> {
				reposition.run();
				int fullWidth = client.getWindow().getGuiScaledWidth();
				if (fullWidth > s.width) {
					graphics.fillGradient(s.width, 0, fullWidth, s.height, 0xC0101010, 0xD0101010);
				}
			});

			ScreenEvents.remove(screen).register(s -> ChestPanelWidget.reset());

			if (ChestPanelWidget.open) {
				ClientPlayNetworking.send(new RequestListPayload());
			}
		});
	}

	private static boolean panelAllowed(net.minecraft.client.gui.screens.Screen screen) {
		if (!(screen instanceof CreativeModeInventoryScreen)) return true;
		return CreativeInventoryScreenAccessor.getSelectedTab().getType() == CreativeModeTab.Type.INVENTORY;
	}
}