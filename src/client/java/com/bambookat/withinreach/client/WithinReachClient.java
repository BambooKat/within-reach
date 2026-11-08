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
import net.minecraft.client.gui.components.AbstractWidget;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.AbstractFurnaceScreen;
import net.minecraft.client.gui.screens.inventory.CraftingScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import com.bambookat.withinreach.client.mixin.CreativeInventoryScreenAccessor;
import net.minecraft.world.item.CreativeModeTab;
import com.bambookat.withinreach.WithinReach;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;

public class WithinReachClient implements ClientModInitializer {
	public static KeyMapping TOGGLE_KEY;

	// larghezza in più occupata a destra da widget di altre mod (es. upgrade di TB), per la schermata corrente
	private static net.minecraft.client.gui.screens.Screen extraScreen;
	private static int extraWidth;
	// i nostri widget dell'ultima init: servono per non ritrovarceli doppi
	private static AbstractWidget[] ours = new AbstractWidget[0];

	/** Usato anche dal mixin che sposta la finestra: larghezza "vera" = imageWidth + questo. */
	public static int foreignExtra(Object screen) {
		return screen == extraScreen ? extraWidth : 0;
	}

	@Override
	public void onInitializeClient() {

		KeyMapping.Category category = KeyMapping.Category.register(WithinReach.id("main"));
		TOGGLE_KEY = KeyMappingHelper.registerKeyMapping(
				new KeyMapping("key.withinreach.toggle", InputConstants.KEY_G, category));

		ButtonPositions.load();

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
				by = 53;
			} else if (screen instanceof AbstractFurnaceScreen<?>) {
				recipeScreen = true;
				bx = 20;
				by = 53;
			} else {
				recipeScreen = false;
				bx = -22;
				by = 4;
			}

			var accessor = (AbstractContainerScreenAccessor) screen;

			ChestPanelWidget panel = new ChestPanelWidget(0, 0);

			EditBox searchBox = new EditBox(client.font, 0, 0, 80, 14, Component.translatable("withinreach.search"));
			searchBox.setHint(Component.translatable("withinreach.search.hint"));
			searchBox.setMaxLength(40);
			searchBox.setValue(ChestPanelWidget.getFilter());
			searchBox.setResponder(ChestPanelWidget::setFilter);
			searchBox.setBordered(false);
			searchBox.setTextColor(0xFFFFFFFF);

			Runnable toggle = () -> {
				boolean needsRoom = ChestPanelWidget.extraShift(
						client.getWindow().getGuiScaledWidth(), accessor.getImageWidth(), foreignExtra(screen)) > 0;
				if (recipeScreen || needsRoom) {
					ChestPanelWidget.open = !ChestPanelWidget.open;
					client.execute(() -> screen.init(scaledWidth, scaledHeight));
				} else {
					panel.toggle();
					if (ChestPanelWidget.open) {
						ClientPlayNetworking.send(new RequestListPayload());
					}
				}
			};

			String posKey = positionKey(screen);
			WrButton button = new WrButton(toggle, () -> ButtonPositions.reset(posKey));

			Runnable reposition = () -> {
				boolean allowed = panelAllowed(screen) && ChestPanelWidget.extraShift(
						client.getWindow().getGuiScaledWidth(), accessor.getImageWidth(), foreignExtra(screen)) >= 0;
				panel.visible = ChestPanelWidget.open && allowed;
				button.visible = allowed;
				int fullWidth = client.getWindow().getGuiScaledWidth();
				int left = accessor.getLeftPos();
				int top = accessor.getTopPos();
				// alcune mod (es. le schede upgrade di Traveler's Backpack) attaccano widget al lato destro: il pannello va dopo
				int baseRight = left + accessor.getImageWidth();
				int guiRight = foreignRightEdge(screen, baseRight, top,
						accessor.getImageHeight(), panel, searchBox, button);
				int rawExtra = guiRight - baseRight;
				// se non c'è abbastanza spazio per tutto, meglio coprire un pezzo di linguetta che far sparire il pannello
				int room = fullWidth - accessor.getImageWidth() - ChestPanelWidget.minPanelWidth(fullWidth) - 8;
				int extra = Math.max(0, Math.min(rawExtra, room));
				if (extra != foreignExtra(screen)) {
					// cambiato (es. hai aperto/chiuso un upgrade di TB): se il pannello è aperto, rifai spazio spostando la finestra
					WithinReach.LOGGER.info("[WR] spazio extra a destra: {} (misurato {}, disponibile {})", extra, rawExtra, room);
					extraScreen = screen;
					extraWidth = extra;
					if (ChestPanelWidget.open && !recipeScreen && !(screen instanceof CreativeModeInventoryScreen)) {
						client.execute(() -> screen.init(scaledWidth, scaledHeight));
					}
				}
				int panelX = baseRight + extra + 4;
				int panelY = Math.max(0, top - ChestPanelWidget.TABS_H);
				panel.layout(fullWidth - panelX - 4);
				panel.setX(panelX);
				panel.setY(panelY);
				searchBox.setX(panelX + 11);
				searchBox.setY(panelY + ChestPanelWidget.TABS_H + 20);
				searchBox.setWidth(panel.searchWidth() - 8);
				searchBox.visible = panel.visible;
				if (button.dragging) return; // durante il trascinamento la posizione la decide il mouse
				int[] saved = ButtonPositions.get(posKey);
				int[] pos = saved != null ? saved
						: autoPosition(screen, bx, by, recipeScreen, left, top,
								accessor.getImageWidth(), accessor.getImageHeight(), panel, searchBox, button);
				button.setX(left + pos[0]);
				button.setY(top + pos[1]);
			};
			reposition.run();

			Screens.getWidgets(screen).removeAll(java.util.Arrays.asList(ours)); // niente doppioni se la schermata non si è svuotata
			ours = new AbstractWidget[]{panel, searchBox, button};
			Screens.getWidgets(screen).add(panel);
			Screens.getWidgets(screen).add(searchBox);
			Screens.getWidgets(screen).add(button);

			// mentre scrivi nella barra, i tasti (come E) non devono chiudere la schermata
			ScreenKeyboardEvents.allowKeyPress(screen).register((s, event) -> {
				if (searchBox.visible && searchBox.canConsumeInput() && event.key() != 256) {
					searchBox.keyPressed(event);
					return false;
				}
				// Scorciatoia da tastiera (non mentre scrivi in una casella di testo, es. incudine o ricerca del creativo)
				if (button.visible && TOGGLE_KEY.matches(event)
						&& !(s.getFocused() instanceof EditBox box && box.canConsumeInput())) {
					toggle.run();
					return false;
				}
				return true;
			});

			// il rilascio del mouse sopra il pannello non deve arrivare alla schermata vanilla (butterebbe l'oggetto in mano)
			ScreenMouseEvents.allowMouseRelease(screen).register((s, event) ->
					!(panel.visible && panel.isMouseOver(event.x(), event.y())));

			// fine trascinamento: salva la posizione relativa all'inventario
			ScreenMouseEvents.allowMouseRelease(screen).register((s, event) -> {
				if (!button.dragging) return true;
				button.dragging = false;
				ButtonPositions.set(posKey,
						button.getX() - accessor.getLeftPos(), button.getY() - accessor.getTopPos());
				return false;
			});

			ScreenEvents.beforeExtract(screen).register((s, graphics, mouseX, mouseY, delta) -> {
				// alcune mod (es. TB quando apri/chiudi un upgrade) svuotano i widget e rifanno init() da sole,
				// senza passare dagli eventi di Fabric: rimettiamo i nostri
				var widgets = Screens.getWidgets(s);
				if (!widgets.contains(panel)) {
					widgets.add(panel);
					widgets.add(searchBox);
					widgets.add(button);
				}
				if (button.dragging) {
					int maxX = client.getWindow().getGuiScaledWidth() - button.getWidth();
					int maxY = client.getWindow().getGuiScaledHeight() - button.getHeight();
					button.setX(Math.max(0, Math.min(maxX, mouseX - button.grabX)));
					button.setY(Math.max(0, Math.min(maxY, mouseY - button.grabY)));
				}
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

	private static String positionKey(net.minecraft.client.gui.screens.Screen screen) {
		if (screen instanceof CreativeModeInventoryScreen) return "creative";
		if (screen instanceof InventoryScreen) return "inventory";
		if (screen instanceof CraftingScreen) return "crafting";
		if (screen instanceof AbstractFurnaceScreen<?>) return "furnace";
		return "container";
	}

	/** Prima posizione libera tra i candidati; relativa all'angolo dell'inventario. */
	private static int[] autoPosition(net.minecraft.client.gui.screens.Screen screen, int bx, int by,
			boolean recipeScreen, int left, int top, int imageW, int imageH,
			AbstractWidget... mine) {
		// IPN disegna i suoi bottoni (ordina, profili) proprio accanto al ricettario e non sono widget "veri"
		boolean ipn = FabricLoader.getInstance().isModLoaded("inventoryprofilesnext");
		int[][] candidates = {
				{bx, by},                // posizione normale
				{imageW - 20, -20},      // sopra l'inventario, a destra
				{0, -20},                // sopra l'inventario, a sinistra
				{imageW - 20, imageH + 2}, // sotto l'inventario, a destra
				{-22, 4},                // fuori a sinistra (non con il ricettario, che sta li')
		};
		for (int i = 0; i < candidates.length; i++) {
			int[] c = candidates[i];
			if (i == 0 && ipn && recipeScreen) continue;
			if (i == 4 && recipeScreen) continue;
			int x = left + c[0], y = top + c[1];
			if (y < 0 || x < 0) continue;
			if (!occupied(screen, x, y, mine)) return c;
		}
		return candidates[0];
	}

	/** Bordo destro "reale": allarga quello della GUI ai widget di altre mod attaccati a destra. */
	private static int foreignRightEdge(net.minecraft.client.gui.screens.Screen screen, int right, int top, int imageH,
			AbstractWidget... mine) {
		java.util.List<int[]> rects = ForeignWidgets.rects(screen, mine);
		int guiRight = right;
		boolean changed = true;
		while (changed) {
			changed = false;
			for (int[] r : rects) {
				int rRight = r[0] + r[2];
				// parte dal bordo della GUI (o da dopo), non da dentro la GUI, e si attacca a quello che abbiamo già contato
				boolean touches = r[0] >= guiRight - 10 && r[0] <= right + 2 && rRight > right;
				boolean beside = r[1] < top + imageH && r[1] + r[3] > top;
				if (touches && beside && rRight - right <= 140) { // oltre 140 px non è una "linguetta", lo ignoriamo
					right = rRight;
					changed = true;
				}
			}
		}
		return right;
	}

	private static boolean occupied(net.minecraft.client.gui.screens.Screen screen, int x, int y, AbstractWidget... mine) {
		for (int[] r : ForeignWidgets.rects(screen, mine)) {
			if (x < r[0] + r[2] && x + 20 > r[0] && y < r[1] + r[3] && y + 18 > r[1]) return true;
		}
		return false;
	}

	private static boolean panelAllowed(net.minecraft.client.gui.screens.Screen screen) {
		if (!(screen instanceof CreativeModeInventoryScreen)) return true;
		return CreativeInventoryScreenAccessor.getSelectedTab().getType() == CreativeModeTab.Type.INVENTORY;
	}
}