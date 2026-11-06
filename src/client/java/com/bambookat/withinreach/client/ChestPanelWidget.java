package com.bambookat.withinreach.client;

import com.bambookat.withinreach.DepositPayload;
import com.bambookat.withinreach.InventoryListPayload;
import com.bambookat.withinreach.TakeItemPayload;
import com.bambookat.withinreach.client.mixin.CreativeModeTabsAccessor;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class ChestPanelWidget extends AbstractWidget {

    public static final int TABS_H = 28;
    public static final int BODY_H = 166;

    private static final int SLOT = 18;
    private static final int MIN_COLUMNS = 5;
    private static final int MAX_COLUMNS = 9;
    private static final int ROWS = 6;
    private static final int GRID_TOP = 34;
    private static final int ARROW_W = 16;
    private static final int ARROW_H = 14;
    private static final int TAB_W = 26;
    private static final int TAB_PITCH = 27;
    private static final int TAB_HIT_H = 28;
    private static final int TAB_ARROW_W = 11;
    private static final int TAB_ARROW_H = 12;

    private record TabInfo(ItemStack icon, Component name, CreativeModeTab tab) {}

    private record Placed(int index, int row, int col) {}

    private static final TabInfo ALL_TAB =
            new TabInfo(new ItemStack(Items.COMPASS), Component.literal("All"), null);

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

    /** Quanto stringere la larghezza della schermata per tenere tutto centrato. */
    public static int shiftFor(int fullWidth) {
        int cols = Math.max(MIN_COLUMNS, Math.min(MAX_COLUMNS, (fullWidth - 380) / SLOT));
        int panelW = cols * SLOT + 14;
        return Math.max(0, Math.min(panelW - 20, fullWidth - 379));
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

    // frecce delle tab: nella riga del titolo, in alto a destra (come nel creativo)
    private int arrowPrevX() {
        return getX() + width - 7 - 2 * TAB_ARROW_W - 1;
    }

    private int arrowNextX() {
        return getX() + width - 7 - TAB_ARROW_W;
    }

    private int arrowY() {
        return bodyY() + 3;
    }

    private static boolean inside(int mx, int my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    private boolean overTabArrow(double mx, double my) {
        if (!needTabArrows()) return false;
        return inside((int) mx, (int) my, arrowPrevX(), arrowY(), TAB_ARROW_W, TAB_ARROW_H)
                || inside((int) mx, (int) my, arrowNextX(), arrowY(), TAB_ARROW_W, TAB_ARROW_H);
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
        int s = SLOT;
        g.fill(x, y, x + s, y + s, 0xFF8B8B8B);
        g.outline(x, y, s, s, 0xFFA83232);
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

    private void drawTinyTriangle(GuiGraphicsExtractor g, int x, int y, boolean right, int color) {
        for (int i = 0; i < 4; i++) {
            int cx = right ? x + i : x + 3 - i;
            g.fill(cx, y + i, cx + 1, y + 7 - i, color);
        }
    }

    /** Le due frecce delle tab come nel creativo: un blocco unico incassato. */
    private void drawTabArrows(GuiGraphicsExtractor g, boolean prevOn, boolean nextOn,
                               boolean hoverPrev, boolean hoverNext) {
        int x = arrowPrevX();
        int y = arrowY();
        int w = arrowNextX() + TAB_ARROW_W - x;
        int h = TAB_ARROW_H;

        g.fill(x, y, x + w, y + h, 0xFF8B8B8B);
        g.fill(x, y, x + w, y + 1, 0xFF373737);
        g.fill(x, y, x + 1, y + h, 0xFF373737);
        g.fill(x, y + h - 1, x + w, y + h, 0xFFFFFFFF);
        g.fill(x + w - 1, y, x + w, y + h, 0xFFFFFFFF);
        g.fill(arrowNextX() - 1, y + 1, arrowNextX(), y + h - 1, 0xFF555555);

        int on = 0xFF373737;
        int hover = 0xFF000000;
        int off = 0xFFA0A0A0;
        drawTinyTriangle(g, arrowPrevX() + 3, y + 2, false,
                !prevOn ? off : hoverPrev ? hover : on);
        drawTinyTriangle(g, arrowNextX() + 4, y + 2, true,
                !nextOn ? off : hoverNext ? hover : on);
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
            // sprite tagliato a 28 righe, e giunzione aperta sul bordo del pannello
            g.blit(RenderPipelines.GUI_TEXTURED, tex, x, getY(), 0, 0, TAB_W, 28, TAB_W, 32);
            g.fill(x + 1, bodyY(), x + TAB_W - 1, bodyY() + 2, 0xFFC6C6C6);
            g.item(info.icon(), x + 5, getY() + 8);
        } else {
            int y = bodyBottom();
            g.blit(RenderPipelines.GUI_TEXTURED, tex, x, y, 0, 4, TAB_W, 28, TAB_W, 32);
            g.fill(x + 1, bodyBottom() - 2, x + TAB_W - 1, bodyBottom(), 0xFFC6C6C6);
            g.item(info.icon(), x + 5, y + 6);
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

        // frecce per scorrere le tab (riga del titolo, in alto a destra)
        if (needTabArrows()) {
            boolean hp = inside(mouseX, mouseY, arrowPrevX(), arrowY(), TAB_ARROW_W, TAB_ARROW_H);
            boolean hn = inside(mouseX, mouseY, arrowNextX(), arrowY(), TAB_ARROW_W, TAB_ARROW_H);
            drawTabArrows(graphics, tabPage > 0, tabPage < tabPageCount() - 1, hp, hn);
        }

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
        int start = page * perPage();
        int end = Math.min(view.size(), start + perPage());

        for (int i = start; i < end; i++) {
            InventoryListPayload.Entry e = view.get(i);
            int local = i - start;
            int x = x0 + 7 + (local % columns) * SLOT;
            int y = by + GRID_TOP + (local / columns) * SLOT;
            ItemStack stack = new ItemStack(e.item());

            drawSlot(graphics, x, y);
            graphics.item(stack, x + 1, y + 1);

            if (e.count() > 1) {
                String text = String.valueOf(e.count());
                graphics.text(font, text, x + SLOT - 1 - font.width(text), y + 9, 0xFFFFFFFF, true);
            }

            if (inside(mouseX, mouseY, x, y, SLOT, SLOT)) {
                graphics.fill(x + 1, y + 1, x + SLOT - 1, y + SLOT - 1, 0x80FFFFFF);
                hovered = stack;
            }
        }

        // pagine: triangoli bianchi come nel ricettario
        int pages = pageCount();
        boolean hp = inside(mouseX, mouseY, prevX(), footerY(), ARROW_W, ARROW_H);
        boolean hn = inside(mouseX, mouseY, nextX(), footerY(), ARROW_W, ARROW_H);
        drawPageArrow(graphics, prevX() + 2, footerY() - 1, false, page > 0, hp);
        drawPageArrow(graphics, nextX() + 2, footerY() - 1, true, page < pages - 1, hn);
        String label = (page + 1) + "/" + pages;
        graphics.text(font, label, x0 + width / 2 - font.width(label) / 2, footerY() + 3, 0xFFFFFFFF, false);

        if (!hovered.isEmpty()) {
            graphics.setTooltipForNextFrame(font, hovered, mouseX, mouseY + 20);
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
        if (needTabArrows()) {
            if (inside(mx, my, arrowPrevX(), arrowY(), TAB_ARROW_W, TAB_ARROW_H)) {
                if (tabPage > 0) tabPage--;
                return true;
            }
            if (inside(mx, my, arrowNextX(), arrowY(), TAB_ARROW_W, TAB_ARROW_H)) {
                if (tabPage < tabPageCount() - 1) tabPage++;
                return true;
            }
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