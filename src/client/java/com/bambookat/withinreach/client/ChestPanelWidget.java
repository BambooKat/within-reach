package com.bambookat.withinreach.client;

import com.bambookat.withinreach.DepositPayload;
import com.bambookat.withinreach.InventoryListPayload;
import com.bambookat.withinreach.TakeItemPayload;
import com.bambookat.withinreach.client.mixin.CreativeModeTabsAccessor;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class ChestPanelWidget extends AbstractWidget {

    public static final int TABS_H = 28;
    public static final int BODY_H = 166;

    private static int SLOT = 25;
    private static final int MIN_COLUMNS = 5;
    private static final int MAX_COLUMNS = 9;
    private static int ROWS = 4;
    private static boolean compact = false;
    private static final int GRID_TOP = 34;
    private static final int ARROW_W = 16;
    private static final int ARROW_H = 14;
    private static final int TAB_W = 26;
    private static final int TAB_PITCH = 27;
    private static final int TAB_HIT_H = 28;
    private static final int ARROWS_W = 20;
    private static final int ARROWS_H = 12;

    private record TabInfo(ItemStack icon, Component name, CreativeModeTab tab) {}

    private record Placed(int index, int row, int col) {}

    private static TabInfo allTab;

    private static TabInfo allTab() {
        if (allTab == null) {
            allTab = new TabInfo(new ItemStack(Items.COMPASS), Component.literal("All"), null);
        }
        return allTab;
    }

    private static final Map<String, Identifier> SPRITES = new HashMap<>();

    private static List<TabInfo> tabs = null;
    private static int selectedTab = 0;
    private static int tabPage = 0;

    private static List<InventoryListPayload.Entry> entries = List.of();
    private static List<InventoryListPayload.Entry> view = List.of();
    private static String filter = "";
    private static int page = 0;
    private static int columns = MIN_COLUMNS;
    public static boolean open = false;

    public ChestPanelWidget(int x, int y) {
        super(x, y, columns * SLOT + 14, TABS_H + BODY_H + TABS_H, Component.literal("Within Reach"));
        this.visible = open;
    }

    public static void setEntries(List<InventoryListPayload.Entry> newEntries) {
        entries = newEntries;
        rebuildView();
    }

    public static void setFilter(String newFilter) {
        filter = newFilter;
        page = 0;
        rebuildView();
    }

    public static String getFilter() {
        return filter;
    }

    public static void reset() {
        filter = "";
        selectedTab = 0;
        tabPage = 0;
        page = 0;
        rebuildView();
    }

    public static void resetOnDisconnect() {
        tabs = null;
        entries = List.of();
        view = List.of();
        selectedTab = 0;
        tabPage = 0;
        page = 0;
        open = false;
    }

    public static void updateMode(int fullWidth) {
        compact = fullWidth < 505;
        SLOT = compact ? 21 : 25;
        ROWS = compact ? 5 : 4;
    }

    /** Quanto stringere la larghezza della schermata per tenere tutto centrato. */
    public static int shiftFor(int fullWidth) {
        updateMode(fullWidth);
        int cols = Math.max(MIN_COLUMNS, Math.min(MAX_COLUMNS, (fullWidth - 380) / SLOT));
        int panelW = cols * SLOT + 14;
        return Math.max(0, Math.min(panelW - 20, fullWidth - 379));
    }

    // ---- categorie (le stesse del creativo) ----

    private static List<TabInfo> tabList() {
        if (tabs == null) {
            var level = Minecraft.getInstance().level;
            if (level == null) return List.of(allTab());
            CreativeModeTabs.tryRebuildTabContents(level.enabledFeatures(), false, level.registryAccess());
            CreativeModeTabsAccessor.setCachedParameters(null);
            List<TabInfo> list = new ArrayList<>();
            list.add(allTab());
            for (CreativeModeTab t : CreativeModeTabs.allTabs()) {
                if (t.getType() != CreativeModeTab.Type.CATEGORY) continue;
                if (!t.shouldDisplay()) continue;
                list.add(new TabInfo(t.getIconItem(), t.getDisplayName(), t));
            }
            tabs = list;
        }
        return tabs;
    }

    private static String formatCount(int n) {
        if (n < 1000) return String.valueOf(n);
        if (n < 1_000_000) return (n / 1000) + "k";
        return (n / 1_000_000) + "M";
    }

    private static void rebuildView() {
        List<TabInfo> list = tabList();
        if (selectedTab >= list.size()) selectedTab = 0;
        CreativeModeTab current = list.get(selectedTab).tab();

        String q = filter.trim().toLowerCase(Locale.ROOT);
        List<InventoryListPayload.Entry> result = new ArrayList<>();
        for (InventoryListPayload.Entry e : entries) {
            ItemStack stack = new ItemStack(e.item());
            if (current != null && !current.contains(stack)) continue;
            if (!q.isEmpty() && !stack.getHoverName().getString().toLowerCase(Locale.ROOT).contains(q)) continue;
            result.add(e);
        }
        view = result;
        clampPage();
    }

    // ---- layout ----

    public void toggle() {
        open = !open;
        this.visible = open;
    }

    /** Adatta la larghezza allo spazio disponibile a destra della schermata. */
    public void layout(int availableWidth) {
        updateMode(Minecraft.getInstance().getWindow().getGuiScaledWidth());
        clampPage();
        int cols = Math.max(MIN_COLUMNS, Math.min(MAX_COLUMNS, (availableWidth - 14) / SLOT));
        if (cols != columns) {
            columns = cols;
            clampPage();
        }
        this.setWidth(columns * SLOT + 14);
    }

    private static int perPage() {
        return columns * ROWS;
    }

    private static int pageCount() {
        return Math.max(1, (view.size() + perPage() - 1) / perPage());
    }

    private static void clampPage() {
        page = Math.max(0, Math.min(page, pageCount() - 1));
    }

    private int bodyY() {
        return getY() + TABS_H;
    }

    private int bodyBottom() {
        return bodyY() + BODY_H;
    }

    private int footerY() {
        return bodyBottom() - 20;
    }

    private int prevX() {
        return getX() + 7;
    }

    private int nextX() {
        return getX() + width - 7 - ARROW_W;
    }

    // ---- tab: due file (sopra e sotto), "All" sempre fissa in alto a sinistra ----

    private int tabsPerRow() {
        return Math.max(1, width / TAB_PITCH);
    }

    private int tabPageSize() {
        return Math.max(1, Math.min(tabList().size() - 1, 2 * tabsPerRow() - 1));
    }

    private boolean needTabArrows() {
        return tabList().size() - 1 > 2 * tabsPerRow() - 1;
    }

    private int tabPageCount() {
        return Math.max(1, (tabList().size() - 1 + tabPageSize() - 1) / tabPageSize());
    }

    private List<Placed> placedTabs() {
        List<TabInfo> list = tabList();
        int n = tabsPerRow();
        int size = tabPageSize();
        List<Placed> out = new ArrayList<>();
        out.add(new Placed(0, 0, 0));
        for (int j = 0; j < size; j++) {
            int idx = 1 + tabPage * size + j;
            if (idx >= list.size()) break;
            int slot = j + 1;
            out.add(new Placed(idx, slot / n, slot % n));
        }
        return out;
    }

    private int tabX(int col) {
        return getX() + col * TAB_PITCH;
    }

    private int tabHitY(int row) {
        return row == 0 ? getY() : bodyBottom();
    }

    /** Larghezza della barra di ricerca. */
    public int searchWidth() {
        return width - 14;
    }

    public boolean needsTabArrows() {
        return needTabArrows();
    }

    public boolean canTabPrev() {
        return tabPage > 0;
    }

    public boolean canTabNext() {
        return tabPage < tabPageCount() - 1;
    }

    public void tabPrev() {
        if (canTabPrev()) tabPage--;
    }

    public void tabNext() {
        if (canTabNext()) tabPage++;
    }

    // frecce delle tab: stessa forma di quelle del creativo (cornice 20x12, due bottoncini 9x10)
    private int arrowsX() {
        return getX() + width - 5 - ARROWS_W;
    }

    private int arrowsY() {
        return bodyY() + 2;
    }

    private static boolean inside(int mx, int my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    private boolean overPrevArrow(int mx, int my) {
        return needTabArrows() && inside(mx, my, arrowsX() + 1, arrowsY() + 1, 9, 10);
    }

    private boolean overNextArrow(int mx, int my) {
        return needTabArrows() && inside(mx, my, arrowsX() + 10, arrowsY() + 1, 9, 10);
    }

    private boolean overTabArrow(double mx, double my) {
        return overPrevArrow((int) mx, (int) my) || overNextArrow((int) mx, (int) my);
    }

    @Override
    public boolean isMouseOver(double mouseX, double mouseY) {
        if (!this.visible) return false;
        // titolo e barra di ricerca: la gestisce la barra, tranne le frecce delle tab
        if (mouseY >= bodyY() && mouseY < bodyY() + GRID_TOP - 2) {
            return overTabArrow(mouseX, mouseY);
        }
        return super.isMouseOver(mouseX, mouseY);
    }

    // ---- disegno ----

    private static Identifier sprite(boolean top, boolean selected, int n) {
        String key = (top ? "top" : "bottom") + (selected ? "_selected_" : "_unselected_") + n;
        return SPRITES.computeIfAbsent(key, k -> Identifier.withDefaultNamespace(
                "textures/gui/sprites/container/creative_inventory/tab_" + k + ".png"));
    }

    private void drawPanel(GuiGraphicsExtractor g, int x, int y, int w, int h) {
        g.fill(x, y, x + w, y + h, 0xFFC6C6C6);
        g.fill(x + 1, y + 1, x + w - 1, y + 2, 0xFFFFFFFF);
        g.fill(x + 1, y + 1, x + 2, y + h - 1, 0xFFFFFFFF);
        g.fill(x + 2, y + h - 2, x + w - 1, y + h - 1, 0xFF555555);
        g.fill(x + w - 2, y + 2, x + w - 1, y + h - 1, 0xFF555555);
        g.outline(x, y, w, h, 0xFF000000);
    }

    private void drawSlot(GuiGraphicsExtractor g, int x, int y) {

        // cella 25x25: linea nera di 1px, poi slot 24x24 come nel ricettario
        g.fill(x, y, x + SLOT, y + SLOT, 0xFF000000);
        int sx = x + 1;
        int sy = y + 1;
        int s = SLOT - 1;
        g.fill(sx, sy, sx + s, sy + s, 0xFFCCCCCC);
        g.fill(sx + 1, sy + 1, sx + s - 1, sy + s - 1, 0xFF8B8B8B);
        g.fill(sx + 1, sy + 1, sx + s - 1, sy + 2, 0xFF373737);
        g.fill(sx + 1, sy + 1, sx + 2, sy + s - 1, 0xFF373737);
        g.fill(sx + 1, sy + s - 2, sx + s - 1, sy + s - 1, 0xFFFFFFFF);
        g.fill(sx + s - 2, sy + 1, sx + s - 1, sy + s - 1, 0xFFFFFFFF);
    }

    private void drawInset(GuiGraphicsExtractor g, int x, int y, int w, int h) {
        g.fill(x, y, x + w, y + h, 0xFF373737);
        g.fill(x, y + h - 1, x + w, y + h, 0xFFFFFFFF);
        g.fill(x + w - 1, y, x + w, y + h, 0xFFFFFFFF);
    }

    private void drawTriangle(GuiGraphicsExtractor g, int x, int y, boolean right,
                              boolean enabled, boolean hover) {
        int color = !enabled ? 0xFF606060 : hover ? 0xFFFFFF55 : 0xFFFFFFFF;
        for (int i = 0; i < 6; i++) {
            int cx = right ? x + i : x + 5 - i;
            g.fill(cx, y + i, cx + 1, y + 11 - i, color);
        }
    }

    private void drawPageArrow(GuiGraphicsExtractor g, int x, int y, boolean forward,
                               boolean enabled, boolean hover) {
        if (!enabled) return;
        String name = (forward ? "page_forward" : "page_backward") + (hover ? "_highlighted" : "");
        Identifier id = SPRITES.computeIfAbsent("recipe_" + name, k -> Identifier.withDefaultNamespace(
                "textures/gui/sprites/recipe_book/" + name + ".png"));
        g.blit(RenderPipelines.GUI_TEXTURED, id, x, y, 0, 0, 12, 17, 12, 17);
    }

    private void drawArrowButton(GuiGraphicsExtractor g, int x, int y, boolean right, boolean enabled) {
        int gray = 0xFF8B8B8B;
        int dark = 0xFF555555;
        if (enabled) {
            g.fill(x, y, x + 9, y + 10, 0xFFC6C6C6);
            g.fill(x, y, x + 8, y + 1, 0xFFFFFFFF);
            g.fill(x + 8, y, x + 9, y + 1, gray);
            g.fill(x, y, x + 1, y + 9, 0xFFFFFFFF);
            g.fill(x, y + 9, x + 1, y + 10, gray);
            g.fill(x + 1, y + 9, x + 9, y + 10, dark);
            g.fill(x + 8, y + 1, x + 9, y + 10, dark);
        } else {
            g.fill(x, y, x + 9, y + 10, gray);
        }
        int color = enabled ? 0xFFFFFFFF : dark;
        for (int i = 0; i < 3; i++) {
            int h = 6 - 2 * i;
            int col = right ? 3 + i : 5 - i;
            g.fill(x + col, y + 5 - h / 2, x + col + 1, y + 5 + h / 2, color);
        }
    }

    /** Le due frecce delle tab come nel creativo: cornice incassata con due bottoncini. */
    private void drawTabArrows(GuiGraphicsExtractor g) {
        int x = arrowsX();
        int y = arrowsY();
        g.fill(x, y, x + ARROWS_W, y + ARROWS_H, 0xFF8B8B8B);
        g.fill(x, y, x + 19, y + 1, 0xFF373737);
        g.fill(x, y, x + 1, y + 11, 0xFF373737);
        g.fill(x + 1, y + 11, x + 20, y + 12, 0xFFFFFFFF);
        g.fill(x + 19, y + 1, x + 20, y + 12, 0xFFFFFFFF);
        drawArrowButton(g, x + 1, y + 1, false, canTabPrev());
        drawArrowButton(g, x + 10, y + 1, true, canTabNext());
    }

    private void drawTab(GuiGraphicsExtractor g, Placed p, TabInfo info, boolean selected) {
        boolean top = p.row() == 0;
        int n = tabsPerRow();
        int variant = p.col() == 0 ? 1 : (p.col() == n - 1 ? 7 : 2);
        int x = tabX(p.col());
        Identifier tex = sprite(top, selected, variant);

        if (!selected) {
            // dietro al pannello: il suo bordo ne copre la parte che entra
            int y = top ? getY() : bodyBottom() - 4;
            g.blit(RenderPipelines.GUI_TEXTURED, tex, x, y, 0, 0, TAB_W, 32, TAB_W, 32);
            g.item(info.icon(), x + 5, top ? y + 8 : y + 10);
        } else if (top) {
            // sprite intero: copre il bordo del pannello e si fonde con lui, come nel creativo
            g.blit(RenderPipelines.GUI_TEXTURED, tex, x, getY(), 0, 0, TAB_W, 32, TAB_W, 32);
            g.item(info.icon(), x + 5, getY() + 8);
        } else {
            int y = bodyBottom() - 4;
            g.blit(RenderPipelines.GUI_TEXTURED, tex, x, y, 0, 0, TAB_W, 32, TAB_W, 32);
            g.item(info.icon(), x + 5, y + 10);
        }
    }

    @Override
    protected void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        clampPage();
        int x0 = getX();
        int by = bodyY();
        var font = Minecraft.getInstance().font;

        List<TabInfo> list = tabList();
        if (selectedTab >= list.size()) selectedTab = 0;
        tabPage = Math.max(0, Math.min(tabPage, tabPageCount() - 1));
        List<Placed> placed = placedTabs();

        // tab non selezionate: dietro al pannello
        for (Placed p : placed) {
            if (p.index() != selectedTab) drawTab(graphics, p, list.get(p.index()), false);
        }

        // pannello con interno scuro incassato, come il ricettario
        drawPanel(graphics, x0, by, width, BODY_H);
        graphics.text(font, "Within Reach", x0 + 7, by + 5, 0xFF404040, false);
        drawInset(graphics, x0 + 4, by + 15, width - 8, BODY_H - 19);

        // tab selezionata: sopra il pannello, si fonde con lui
        for (Placed p : placed) {
            if (p.index() == selectedTab) drawTab(graphics, p, list.get(p.index()), true);
        }

        if (needTabArrows()) drawTabArrows(graphics);

        // campo di ricerca: nero con bordo chiaro
        int sx = x0 + 7;
        int sy = by + 17;
        int sw = searchWidth();
        int sh = 14;
        graphics.fill(sx, sy, sx + sw, sy + sh, 0xFF000000);
        graphics.outline(sx, sy, sw, sh, 0xFFA0A0A0);

        if (view.isEmpty()) {
            graphics.text(font, "No results", x0 + 7, by + GRID_TOP + 4, 0xFFFFFFFF, false);
        }

        ItemStack hovered = ItemStack.EMPTY;
        int hoveredCount = 0;
        List<InventoryListPayload.Source> hoveredSources = List.of();
        int start = page * perPage();
        int end = Math.min(view.size(), start + perPage());

        for (int i = start; i < end; i++) {
            InventoryListPayload.Entry e = view.get(i);
            int local = i - start;
            int x = x0 + 7 + (local % columns) * SLOT;
            int y = by + GRID_TOP + (local / columns) * SLOT;
            ItemStack stack = new ItemStack(e.item());

            drawSlot(graphics, x, y);
            graphics.item(stack, x + (compact ? 3 : 5), y + (compact ? 3 : 5));

            if (e.count() > 1) {
                String text = formatCount(e.count());
                float scale = 0.7f;
                graphics.pose().pushMatrix();
                graphics.pose().translate(x + SLOT - (compact ? 2 : 3), y + SLOT - (compact ? 2 : 3) - 8 * scale);
                graphics.pose().scale(scale, scale);
                graphics.text(font, text, -font.width(text), 0, 0xFFFFFFFF, true);
                graphics.pose().popMatrix();
            }

            if (inside(mouseX, mouseY, x, y, SLOT, SLOT)) {
                graphics.fill(x + 3, y + 3, x + SLOT - 2, y + SLOT - 2, 0x80FFFFFF);
                hovered = stack;
                hoveredCount = e.count();
                hoveredSources = e.sources();
            }
        }

        // pagine: triangoli bianchi come nel ricettario
        int pages = pageCount();
        boolean hp = inside(mouseX, mouseY, prevX(), footerY(), ARROW_W, ARROW_H);
        boolean hn = inside(mouseX, mouseY, nextX(), footerY(), ARROW_W, ARROW_H);
        drawPageArrow(graphics, prevX() + 2, footerY() - 3, false, page > 0, hp);
        drawPageArrow(graphics, nextX() + 2, footerY() - 3, true, page < pages - 1, hn);
        String label = (page + 1) + "/" + pages;
        graphics.text(font, label, x0 + width / 2 - font.width(label) / 2, footerY() + 3, 0xFFFFFFFF, false);

        if (!hovered.isEmpty()) {
            var mc = Minecraft.getInstance();
            List<Component> lines = new ArrayList<>(hovered.getTooltipLines(
                    Item.TooltipContext.of(mc.level), mc.player, TooltipFlag.Default.NORMAL));
            if (hoveredCount >= 1000) {
                lines.add(Component.literal("Total: " + String.format(Locale.ROOT, "%,d", hoveredCount))
                        .withStyle(ChatFormatting.GRAY));
            }
            int shown = 0;
            for (InventoryListPayload.Source src : hoveredSources) {
                if (shown == 8) {
                    lines.add(Component.literal("  +" + (hoveredSources.size() - 8) + " ...")
                            .withStyle(style -> style.withColor(0x888888)));
                    break;
                }
                lines.add(Component.literal("  ").append(src.name().copy())
                        .append(": " + String.format(Locale.ROOT, "%,d", src.count()))
                        .withStyle(style -> style.withColor(0x888888)));
                shown++;
            }
            graphics.setComponentTooltipForNextFrame(font, lines, mouseX, mouseY + 20);
        } else if (overTabArrow(mouseX, mouseY)) {
            graphics.setTooltipForNextFrame(font,
                    Component.literal("Page " + (tabPage + 1) + "/" + tabPageCount()),
                    mouseX, mouseY + 20);
        } else {
            for (Placed p : placed) {
                if (inside(mouseX, mouseY, tabX(p.col()), tabHitY(p.row()), TAB_W, TAB_HIT_H)) {
                    graphics.setTooltipForNextFrame(font, list.get(p.index()).name(),
                            mouseX, mouseY + 20);
                    break;
                }
            }
        }
    }

    // ---- click ----

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (!this.visible) return false;
        int mx = (int) event.x();
        int my = (int) event.y();
        if (!inside(mx, my, getX(), getY(), width, height)) return false;

        int by = bodyY();

        // fila di tab sopra o sotto
        if (my < by || my >= bodyBottom()) {
            for (Placed p : placedTabs()) {
                if (inside(mx, my, tabX(p.col()), tabHitY(p.row()), TAB_W, TAB_HIT_H)) {
                    selectedTab = p.index();
                    page = 0;
                    rebuildView();
                    return true;
                }
            }
            return false;
        }

        // frecce delle tab (riga del titolo)
        if (overPrevArrow(mx, my)) {
            tabPrev();
            return true;
        }
        if (overNextArrow(mx, my)) {
            tabNext();
            return true;
        }

        // titolo e barra di ricerca: li gestisce la barra
        if (my < by + GRID_TOP - 2) return false;

        if (inside(mx, my, prevX(), footerY(), ARROW_W, ARROW_H)) {
            if (page > 0) page--;
            return true;
        }
        if (inside(mx, my, nextX(), footerY(), ARROW_W, ARROW_H)) {
            if (page < pageCount() - 1) page++;
            return true;
        }

        var player = Minecraft.getInstance().player;
        net.minecraft.world.item.ItemStack carried = player != null
                ? player.containerMenu.getCarried() : net.minecraft.world.item.ItemStack.EMPTY;

        int relX = mx - (getX() + 7);
        int relY = my - (by + GRID_TOP);
        int index = -1;
        if (relX >= 0 && relY >= 0) {
            int col = relX / SLOT;
            int row = relY / SLOT;
            int i = page * perPage() + row * columns + col;
            if (col < columns && row < ROWS && i < view.size()) index = i;
        }

        if (!carried.isEmpty()) {
            boolean sameItem = index >= 0 && carried.is(view.get(index).item())
                    && carried.getCount() < carried.getMaxStackSize();
            if (!sameItem) {
                ClientPlayNetworking.send(new DepositPayload(event.button() == 0));
                return true;
            }
        }

        if (index >= 0) {
            boolean wholeStack = event.button() == 0;
            ClientPlayNetworking.send(new TakeItemPayload(view.get(index).item(), wholeStack, !event.hasShiftDown()));
        }
        return true;
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput builder) {
    }
}