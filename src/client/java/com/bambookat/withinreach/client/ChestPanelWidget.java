package com.bambookat.withinreach.client;

import com.bambookat.withinreach.DepositPayload;
import com.bambookat.withinreach.InventoryListPayload;
import com.bambookat.withinreach.TakeItemPayload;
import com.bambookat.withinreach.client.mixin.CreativeModeTabsAccessor;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class ChestPanelWidget extends AbstractWidget {

    public static final int TABS_H = 24;

    private static final int SLOT = 18;
    private static final int MIN_COLUMNS = 5;
    private static final int MAX_COLUMNS = 9;
    private static final int ROWS = 6;
    private static final int GRID_TOP = 34;
    private static final int BODY_H = GRID_TOP + ROWS * SLOT + 26;
    private static final int ARROW_W = 16;
    private static final int ARROW_H = 14;
    private static final int TAB_W = 18;
    private static final int TAB_H_SEL = 22;
    private static final int TAB_H_UNSEL = 19;
    private static final int TAB_ARROW_W = 12;

    private record TabInfo(ItemStack icon, Component name, CreativeModeTab tab) {}

    private record Placed(int index, int row, int col) {}

    private static final TabInfo ALL_TAB =
            new TabInfo(new ItemStack(Items.COMPASS), Component.literal("All"), null);

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

    // ---- categorie (le stesse del creativo) ----

    private static List<TabInfo> tabList() {
        if (tabs == null) {
            var level = Minecraft.getInstance().level;
            if (level == null) return List.of(ALL_TAB);
            CreativeModeTabs.tryRebuildTabContents(level.enabledFeatures(), false, level.registryAccess());
            CreativeModeTabsAccessor.setCachedParameters(null);
            List<TabInfo> list = new ArrayList<>();
            list.add(ALL_TAB);
            for (CreativeModeTab t : CreativeModeTabs.allTabs()) {
                if (t.getType() != CreativeModeTab.Type.CATEGORY) continue;
                if (!t.shouldDisplay()) continue;
                list.add(new TabInfo(t.getIconItem(), t.getDisplayName(), t));
            }
            tabs = list;
        }
        return tabs;
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
        return Math.max(1, width / (TAB_W + 1));
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
        return getX() + col * (TAB_W + 1);
    }

    private int tabY(int row, boolean selected) {
        if (row == 0) return getY() + (selected ? 2 : 5);
        return bodyBottom();
    }

    private int tabHitY(int row) {
        return row == 0 ? getY() + 2 : bodyBottom();
    }

    private int searchArrowsW() {
        return needTabArrows() ? 2 * TAB_ARROW_W + 3 : 0;
    }

    /** Larghezza della barra di ricerca (lascia posto alle frecce delle tab). */
    public int searchWidth() {
        return width - 14 - searchArrowsW();
    }

    private int arrowPrevX() {
        return getX() + width - 7 - 2 * TAB_ARROW_W - 1;
    }

    private int arrowNextX() {
        return getX() + width - 7 - TAB_ARROW_W;
    }

    private int arrowY() {
        return bodyY() + 17;
    }

    private static boolean inside(int mx, int my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    @Override
    public boolean isMouseOver(double mouseX, double mouseY) {
        if (!this.visible) return false;
        // la barra di ricerca (titolo + campo) gestisce i suoi click da sola
        if (mouseY >= bodyY() && mouseY < bodyY() + GRID_TOP - 2) {
            if (mouseX < getX() + 7 + searchWidth()) return false;
        }
        return super.isMouseOver(mouseX, mouseY);
    }

    // ---- disegno ----

    private void drawTab(GuiGraphicsExtractor g, Placed p, TabInfo info, boolean selected) {
        int x = tabX(p.col());
        int y = tabY(p.row(), selected);
        int h = selected ? TAB_H_SEL : TAB_H_UNSEL;
        g.fill(x, y, x + TAB_W, y + h, selected ? 0xFFC6C6C6 : 0xFF8B8B8B);
        g.outline(x, y, TAB_W, h, 0xFF000000);
        if (selected) {
            if (p.row() == 0) {
                g.fill(x + 1, bodyY(), x + TAB_W - 1, bodyY() + 1, 0xFFC6C6C6);
            } else {
                g.fill(x + 1, bodyBottom() - 1, x + TAB_W - 1, bodyBottom(), 0xFFC6C6C6);
            }
        }
        int iconY = y + (selected ? 3 : 2);
        g.item(info.icon(), x + 1, iconY);
    }

    private void drawSmallArrow(GuiGraphicsExtractor g, Font font, int x, int y, String label,
                                boolean enabled, int mx, int my) {
        boolean hover = enabled && inside(mx, my, x, y, TAB_ARROW_W, ARROW_H);
        g.fill(x, y, x + TAB_ARROW_W, y + ARROW_H, hover ? 0xFFA8A8A8 : 0xFF8B8B8B);
        g.outline(x, y, TAB_ARROW_W, ARROW_H, 0xFF000000);
        g.text(font, label, x + TAB_ARROW_W / 2 - font.width(label) / 2, y + 3,
                enabled ? 0xFFFFFFFF : 0xFF606060, false);
    }

    private void drawArrow(GuiGraphicsExtractor g, Font font, int x, int y, String label,
                           boolean enabled, int mx, int my) {
        boolean hover = enabled && inside(mx, my, x, y, ARROW_W, ARROW_H);
        g.fill(x, y, x + ARROW_W, y + ARROW_H, hover ? 0xFFA8A8A8 : 0xFF8B8B8B);
        g.outline(x, y, ARROW_W, ARROW_H, 0xFF000000);
        g.text(font, label, x + ARROW_W / 2 - font.width(label) / 2, y + 3,
                enabled ? 0xFFFFFFFF : 0xFF606060, false);
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

        // tab non selezionate (dietro al pannello)
        for (Placed p : placed) {
            if (p.index() != selectedTab) drawTab(graphics, p, list.get(p.index()), false);
        }

        // pannello
        graphics.fill(x0, by, x0 + width, by + BODY_H, 0xFFC6C6C6);
        graphics.outline(x0, by, width, BODY_H, 0xFF000000);
        graphics.text(font, "Within Reach", x0 + 7, by + 6, 0xFF404040, false);

        // tab selezionata (sopra il pannello, collegata dal suo lato)
        for (Placed p : placed) {
            if (p.index() == selectedTab) drawTab(graphics, p, list.get(p.index()), true);
        }

        // sfondo del campo di ricerca, incassato come gli slot
        int sx = x0 + 7;
        int sy = by + 17;
        int sw = searchWidth();
        int sh = 14;
        graphics.fill(sx, sy, sx + sw, sy + sh, 0xFF555555);
        graphics.fill(sx, sy, sx + sw, sy + 1, 0xFF373737);
        graphics.fill(sx, sy, sx + 1, sy + sh, 0xFF373737);
        graphics.fill(sx, sy + sh - 1, sx + sw, sy + sh, 0xFFFFFFFF);
        graphics.fill(sx + sw - 1, sy, sx + sw, sy + sh, 0xFFFFFFFF);

        // frecce per scorrere le tab, nella riga della ricerca
        if (needTabArrows()) {
            drawSmallArrow(graphics, font, arrowPrevX(), arrowY(), "<", tabPage > 0, mouseX, mouseY);
            drawSmallArrow(graphics, font, arrowNextX(), arrowY(), ">",
                    tabPage < tabPageCount() - 1, mouseX, mouseY);
        }

        if (view.isEmpty()) {
            graphics.text(font, "No results", x0 + 7, by + GRID_TOP + 4, 0xFF404040, false);
        }

        ItemStack hovered = ItemStack.EMPTY;
        int start = page * perPage();
        int end = Math.min(view.size(), start + perPage());

        for (int i = start; i < end; i++) {
            InventoryListPayload.Entry e = view.get(i);
            int local = i - start;
            int x = x0 + 7 + (local % columns) * SLOT;
            int y = by + GRID_TOP + (local / columns) * SLOT;
            ItemStack stack = new ItemStack(e.item());

            graphics.fill(x, y, x + SLOT - 1, y + SLOT - 1, 0xFF8B8B8B);
            graphics.item(stack, x + 1, y + 1);

            if (e.count() > 1) {
                String text = String.valueOf(e.count());
                graphics.text(font, text, x + SLOT - 1 - font.width(text), y + 9, 0xFFFFFFFF, true);
            }

            if (inside(mouseX, mouseY, x, y, SLOT - 1, SLOT - 1)) {
                graphics.fill(x, y, x + SLOT - 1, y + SLOT - 1, 0x80FFFFFF);
                hovered = stack;
            }
        }

        int pages = pageCount();
        drawArrow(graphics, font, prevX(), footerY(), "<", page > 0, mouseX, mouseY);
        drawArrow(graphics, font, nextX(), footerY(), ">", page < pages - 1, mouseX, mouseY);
        String label = (page + 1) + "/" + pages;
        graphics.text(font, label, x0 + width / 2 - font.width(label) / 2, footerY() + 3, 0xFF404040, false);

        if (!hovered.isEmpty()) {
            graphics.setTooltipForNextFrame(font, hovered, mouseX, mouseY + 20);
        } else {
            for (Placed p : placed) {
                if (inside(mouseX, mouseY, tabX(p.col()), tabHitY(p.row()), TAB_W, TAB_H_SEL)) {
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
                if (inside(mx, my, tabX(p.col()), tabHitY(p.row()), TAB_W, TAB_H_SEL)) {
                    selectedTab = p.index();
                    page = 0;
                    rebuildView();
                    return true;
                }
            }
            return false;
        }

        // riga della ricerca: frecce delle tab, il resto lo gestisce la barra
        if (my < by + GRID_TOP - 2) {
            if (needTabArrows()) {
                if (inside(mx, my, arrowPrevX(), arrowY(), TAB_ARROW_W, ARROW_H)) {
                    if (tabPage > 0) tabPage--;
                    return true;
                }
                if (inside(mx, my, arrowNextX(), arrowY(), TAB_ARROW_W, ARROW_H)) {
                    if (tabPage < tabPageCount() - 1) tabPage++;
                    return true;
                }
            }
            return false;
        }

        if (inside(mx, my, prevX(), footerY(), ARROW_W, ARROW_H)) {
            if (page > 0) page--;
            return true;
        }
        if (inside(mx, my, nextX(), footerY(), ARROW_W, ARROW_H)) {
            if (page < pageCount() - 1) page++;
            return true;
        }

        var player = Minecraft.getInstance().player;
        if (player != null && !player.containerMenu.getCarried().isEmpty()) {
            ClientPlayNetworking.send(new DepositPayload(event.button() == 0));
            return true;
        }

        int relX = mx - (getX() + 7);
        int relY = my - (by + GRID_TOP);
        if (relX >= 0 && relY >= 0) {
            int col = relX / SLOT;
            int row = relY / SLOT;
            int index = page * perPage() + row * columns + col;
            if (col < columns && row < ROWS && index < view.size()) {
                boolean wholeStack = event.button() == 0;
                ClientPlayNetworking.send(new TakeItemPayload(view.get(index).item(), wholeStack));
            }
        }
        return true;
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput builder) {
    }
}