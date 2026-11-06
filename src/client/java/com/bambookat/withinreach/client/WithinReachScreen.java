package com.bambookat.withinreach.client;

import com.bambookat.withinreach.InventoryListPayload;
import com.bambookat.withinreach.TakeItemPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.List;

public class WithinReachScreen extends Screen {

    private static final int SLOT = 18;
    private static final int COLUMNS = 9;

    private List<InventoryListPayload.Entry> entries;
    private final Screen parent;
    private int left, top;

    public WithinReachScreen(List<InventoryListPayload.Entry> entries, Screen parent) {
        super(Component.literal("Within Reach"));
        this.entries = entries;
        this.parent = parent;
    }

    public void setEntries(List<InventoryListPayload.Entry> entries) {
        this.entries = entries;
    }

    @Override
    public void onClose() {
        this.minecraft.gui.setScreen(this.parent);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        super.extractRenderState(graphics, mouseX, mouseY, delta);

        int rows = Math.max(1, (entries.size() + COLUMNS - 1) / COLUMNS);
        int panelW = COLUMNS * SLOT + 14;
        int panelH = rows * SLOT + 28;
        this.left = (this.width - panelW) / 2;
        this.top = (this.height - panelH) / 2;

        graphics.fill(left, top, left + panelW, top + panelH, 0xFFC6C6C6);
        graphics.outline(left, top, panelW, panelH, 0xFF000000);
        graphics.text(this.font, "Within Reach", left + 7, top + 6, 0xFF404040, false);

        ItemStack hovered = ItemStack.EMPTY;
        int hoverX = 0, hoverY = 0;

        for (int i = 0; i < entries.size(); i++) {
            InventoryListPayload.Entry e = entries.get(i);
            int x = left + 7 + (i % COLUMNS) * SLOT;
            int y = top + 20 + (i / COLUMNS) * SLOT;
            ItemStack stack = new ItemStack(e.item());

            graphics.fill(x, y, x + SLOT - 1, y + SLOT - 1, 0xFF8B8B8B);
            graphics.item(stack, x + 1, y + 1);

            if (e.count() > 1) {
                String text = String.valueOf(e.count());
                int w = this.font.width(text);
                graphics.text(this.font, text, x + SLOT - 1 - w, y + 9, 0xFFFFFFFF, true);
            }

            if (mouseX >= x && mouseX < x + SLOT - 1 && mouseY >= y && mouseY < y + SLOT - 1) {
                graphics.fill(x, y, x + SLOT - 1, y + SLOT - 1, 0x80FFFFFF);
                hovered = stack;
                hoverX = mouseX;
                hoverY = mouseY;
            }
        }

        if (!hovered.isEmpty()) {
            graphics.setTooltipForNextFrame(this.font, hovered, hoverX, hoverY + 20);
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        int relX = (int) event.x() - (left + 7);
        int relY = (int) event.y() - (top + 20);
        if (relX >= 0 && relY >= 0) {
            int col = relX / SLOT;
            int index = (relY / SLOT) * COLUMNS + col;
            if (col < COLUMNS && index < entries.size()) {
                boolean wholeStack = event.button() == 0; // 0 = sinistro, 1 = destro
                ClientPlayNetworking.send(
                        new TakeItemPayload(entries.get(index).item(), wholeStack));
                return true;
            }
        }
        return super.mouseClicked(event, doubleClick);
    }
}