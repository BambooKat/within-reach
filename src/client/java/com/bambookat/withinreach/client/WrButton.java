package com.bambookat.withinreach.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import com.bambookat.withinreach.WithinReach;

/** Bottone del pannello: stessa sprite e dimensione del bottone del ricettario, con una chest sopra. */
public class WrButton extends AbstractWidget {
    private static final Identifier BACKGROUND = WithinReach.id("textures/gui/wrbutton.png");

    private final Runnable onPress;
    private ItemStack icon; // creato al primo disegno: gli item non esistono ancora nel menu principale

    public WrButton(Runnable onPress) {
        super(0, 0, 20, 18, Component.literal("Within Reach"));
        this.onPress = onPress;
    }

    @Override
    protected void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        boolean hover = isMouseOver(mouseX, mouseY);
        int x = getX();
        int y = getY();
        graphics.blit(RenderPipelines.GUI_TEXTURED, BACKGROUND, x, y, 0, 0, 20, 18, 20, 18);
        if (hover) graphics.fill(x + 1, y + 1, x + 19, y + 17, 0x40FFFFFF);
        if (icon == null) icon = new ItemStack(Items.CHEST);
        graphics.item(icon, x + 2, y+1);
        if (hover) {
            graphics.setTooltipForNextFrame(Minecraft.getInstance().font,
                    Component.literal("Within Reach"), mouseX, mouseY);
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (!this.visible || !this.active || event.button() != 0) return false;
        if (!isMouseOver(event.x(), event.y())) return false;
        playDownSound(Minecraft.getInstance().getSoundManager());
        onPress.run();
        return true;
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput builder) {
    }
}
