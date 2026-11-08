package com.bambookat.withinreach.client;

import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Rettangoli {x, y, w, h} occupati da roba di altre mod nella schermata:
 * - widget vanilla (AbstractWidget);
 * - widget "fatti in casa" che espongono getWidgetSizeAndPos() (es. Traveler's Backpack: upgrade, impostazioni);
 * - slot degli upgrade di Traveler's Backpack (campo pubblico upgradeSlots).
 */
final class ForeignWidgets {
    private static final Map<String, Optional<Method>> METHODS = new ConcurrentHashMap<>();

    private ForeignWidgets() {}

    static List<int[]> rects(Screen screen, AbstractWidget... mine) {
        List<int[]> out = new ArrayList<>();
        outer:
        for (GuiEventListener child : screen.children()) {
            for (AbstractWidget m : mine) if (child == m) continue outer;
            if (child instanceof AbstractWidget w) {
                if (w.visible) out.add(new int[]{w.getX(), w.getY(), w.getWidth(), w.getHeight()});
                continue;
            }
            int[] r = rect(child, "getWidgetSizeAndPos");
            if (r != null) out.add(r);
        }
        try {
            if (screen.getClass().getField("upgradeSlots").get(screen) instanceof List<?> slots) {
                for (Object slot : slots) {
                    if (Boolean.TRUE.equals(call(slot, "isHidden"))) continue;
                    int[] r = rect(slot, "getUpgradeSlotSizeAndPos");
                    if (r != null) out.add(r);
                }
            }
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            // nessun campo upgradeSlots: non è TB, niente da fare
        }
        return out;
    }

    private static int[] rect(Object target, String name) {
        return call(target, name) instanceof int[] r && r.length >= 4 && r[2] > 0 && r[3] > 0 ? r : null;
    }

    private static Object call(Object target, String name) {
        Optional<Method> m = METHODS.computeIfAbsent(target.getClass().getName() + "#" + name, k -> {
            try {
                return Optional.of(target.getClass().getMethod(name));
            } catch (NoSuchMethodException | RuntimeException e) {
                return Optional.empty();
            }
        });
        if (m.isEmpty()) return null;
        try {
            return m.get().invoke(target);
        } catch (ReflectiveOperationException | RuntimeException e) {
            return null;
        }
    }
}
