package com.bambookat.withinreach.client.mixin;

import com.bambookat.withinreach.client.ChestPanelWidget;
import com.bambookat.withinreach.client.WithinReachClient;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.AbstractRecipeBookScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Per le schermate larghe (mercanti, macchine di altre mod) a scale GUI alte: se il pannello non ci sta
 * accanto, sposta la finestra a sinistra della differenza. Il ricettario ha già il suo meccanismo.
 */
@Mixin(AbstractContainerScreen.class)
public abstract class AbstractContainerScreenShiftMixin {

    @Unique
    private int withinreach$lastReduced = -1;

    @Inject(method = "init", at = @At("HEAD"))
    private void withinreach$makeRoom(CallbackInfo ci) {
        if (!ChestPanelWidget.open) return;
        Object self = this;
        if (self instanceof AbstractRecipeBookScreen || self instanceof CreativeModeInventoryScreen) return;
        ScreenAccessor screen = (ScreenAccessor) self;
        int w = screen.getScreenWidth();
        if (w == withinreach$lastReduced) return;
        // conta anche i widget di altre mod attaccati a destra (es. upgrade di Traveler's Backpack)
        int shift = ChestPanelWidget.extraShift(w, ((AbstractContainerScreenAccessor) self).getImageWidth(),
                WithinReachClient.foreignExtra(self));
        if (shift <= 0) return;
        int reduced = w - 2 * shift;
        screen.setScreenWidth(reduced);
        withinreach$lastReduced = reduced;
    }
}
