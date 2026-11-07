package com.bambookat.withinreach.client;

import com.bambookat.withinreach.WithinReachConfig;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

public class RadiusConfigScreen extends Screen {
    private final Screen parent;
    private int radius = WithinReachConfig.radius();

    public RadiusConfigScreen(Screen parent) {
        super(Component.translatable("withinreach.config.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int w = 200;
        int x = this.width / 2 - w / 2;
        int y = this.height / 2 - 30;

        addRenderableWidget(new StringWidget(x, y - 24, w, 9, this.title, this.font));
        addRenderableWidget(new RadiusSlider(x, y, w));
        addRenderableWidget(new StringWidget(x, y + 26, w, 9,
                Component.translatable("withinreach.config.hint"), this.font));
        addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, b -> onClose())
                .bounds(x, y + 50, w, 20).build());
    }

    @Override
    public void onClose() {
        WithinReachConfig.setRadius(radius);
        this.minecraft.gui.setScreen(parent);
    }

    private class RadiusSlider extends AbstractSliderButton {
        RadiusSlider(int x, int y, int w) {
            super(x, y, w, 20, Component.empty(),
                    (radius - 1) / (double) (WithinReachConfig.MAX_RADIUS - 1));
            updateMessage();
        }

        @Override
        protected void updateMessage() {
            setMessage(Component.translatable("withinreach.config.radius", radius));
        }

        @Override
        protected void applyValue() {
            radius = 1 + (int) Math.round(this.value * (WithinReachConfig.MAX_RADIUS - 1));
        }
    }
}
