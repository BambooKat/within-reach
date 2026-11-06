package com.bambookat.withinreach.client.mixin;

import com.bambookat.withinreach.client.ChestPanelWidget;
import net.minecraft.client.gui.screens.inventory.AbstractRecipeBookScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AbstractRecipeBookScreen.class)
public abstract class AbstractRecipeBookScreenMixin {

    @Unique
    private int withinreach$lastReduced = -1;

    @Inject(method = "init", at = @At("HEAD"))
    private void withinreach$shrinkWidth(CallbackInfo ci) {
        if (!ChestPanelWidget.open) return;
        ScreenAccessor screen = (ScreenAccessor) (Object) this;
        int w = screen.getScreenWidth();
        if (w != withinreach$lastReduced) {
            int reduced = w - 80;
            screen.setScreenWidth(reduced);
            withinreach$lastReduced = reduced;
        }
    }
}