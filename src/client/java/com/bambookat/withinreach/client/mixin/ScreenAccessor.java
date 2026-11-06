package com.bambookat.withinreach.client.mixin;

import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(Screen.class)
public interface ScreenAccessor {
    @Accessor("width")
    int getScreenWidth();

    @Accessor("width")
    void setScreenWidth(int width);
}