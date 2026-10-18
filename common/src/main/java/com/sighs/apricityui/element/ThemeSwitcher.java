package com.sighs.apricityui.element;

import com.sighs.apricityui.event.Event;
import com.sighs.apricityui.event.MouseEvent;
import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.loader.ClientLoader;
import com.sighs.apricityui.registry.annotation.ElementRegister;
import com.sighs.apricityui.render.Drawer;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;

/**
 * 内置主题切换器：页面里写一个标签 {@code <theme-switcher></theme-switcher>} 即可。
 *
 * <p>它是一个悬浮圆钮（{@code position:fixed}），按住可以拖到视口任意位置，点开是内置主题清单，
 * 选中即换肤。整个控件由 Java 自己建 DOM、自己画（内联样式），不要求页面提供任何 CSS，
 * 也不跟随主题变色——主题样式再坏都能操作。</p>
 *
 * <p>标签写成对写、别写自闭合：浏览器里的 HTML 解析器忽略自定义标签上的自闭合斜杠，
 * {@code <theme-switcher/>} 会把后面的内容全吞成它的子节点（引擎两种写法都认）。</p>
 *
 * <p>换肤走的是主题规范的通用约定：样式表 {@code /apricityui/theme/<name>/<name>.css}，
 * 作用域类 {@code .<name>-theme} 挂在文档根元素上，变体主题（如 t09 吃 t08）额外挂基主题的类。
 * 切换顺序沿用预览台 {@code theme/t00-example.html}：<em>先挂新表 → 等 load 到齐 → 换作用域类 →
 * 最后摘掉旧表</em>，换类那一瞬间新旧两张表都在场但作用域类互相隔离，所以中间不会露出没皮肤的
 * 空白帧；新表没加载成功就原地不动，当前主题继续可用。</p>
 *
 * <p>样式表统一带 {@code data-theme-css} 标记，和预览台脚本用的是同一个约定，因此两条切换路径
 * 可以共存：谁切都会按标记清掉对方留下的旧表，并按内置主题表清掉根元素上的旧作用域类。
 * 换肤完成后在本元素上派发冒泡的 {@link #THEME_CHANGE_EVENT}（{@code detail} 是主题 id），
 * 页面脚本可据此把自己的主题状态同步过来。</p>
 */
@ElementRegister(ThemeSwitcher.TAG_NAME)
public class ThemeSwitcher extends Element {
    public static final String TAG_NAME = "THEME-SWITCHER";

    /** 换肤完成事件名，{@code detail} 为主题 id；在按钮上派发，冒泡到 document。 */
    public static final String THEME_CHANGE_EVENT = "aui-theme-change";

    /** 主题样式表标记：与预览台同约定，双方都能认出并清掉对方的旧表。 */
    private static final String SHEET_MARK = "data-theme-css";

    /** 逻辑资源根：主题目录固定为 {@code /apricityui/theme/}，作用域类固定为 {@code <name>-theme}。 */
    private static final String THEME_ROOT = "apricityui/theme/";
    private static final String SCOPE_SUFFIX = "-theme";

    private static final String PANEL_CLASS = "aui-theme-switcher-panel";
    private static final String ROW_CLASS = "aui-theme-switcher-row";

    private static final int Z_INDEX = 9990;
    private static final int BUTTON_SIZE = 44;
    private static final int PANEL_WIDTH = 200;
    private static final int ROW_HEIGHT = 26;
    private static final int PANEL_PADDING = 6;
    private static final int PANEL_BORDER = 2;
    private static final int PANEL_GAP = 10;
    private static final int VIEWPORT_MARGIN = 8;
    /** 判定"这是一次拖动而不是点击"的位移阈值（px）。 */
    private static final double DRAG_THRESHOLD = 3;

    private static final String BUTTON_BACKGROUND = "#23252a";
    private static final String BUTTON_BACKGROUND_HOVER = "#33363d";
    private static final String ROW_BACKGROUND = "#23252a";
    private static final String ROW_BACKGROUND_HOVER = "#33363d";
    private static final String ROW_BACKGROUND_ACTIVE = "#3c8527";
    private static final String ROW_FOREGROUND = "#dfe3e8";
    private static final String ROW_FOREGROUND_ACTIVE = "#ffffff";
    private static final String[] DOT_COLORS = {"#3c8527", "#7345e5", "#f0b92d", "#2d78a8"};

    private static final String BUTTON_STYLE =
            "position:fixed;right:18px;bottom:18px;"
                    + "width:" + BUTTON_SIZE + "px;height:" + BUTTON_SIZE + "px;box-sizing:border-box;"
                    + "display:flex;flex-wrap:wrap;align-content:center;justify-content:center;gap:3px;padding:8px;"
                    + "z-index:" + Z_INDEX + ";border:2px solid #0b0c0e;border-radius:" + (BUTTON_SIZE / 2) + "px;"
                    + "background:" + BUTTON_BACKGROUND + ";box-shadow:0 2px 8px rgba(0,0,0,0.45);"
                    + "cursor:pointer;overflow:hidden;";

    private static final String PANEL_STYLE =
            "position:fixed;z-index:" + Z_INDEX + ";box-sizing:border-box;"
                    + "padding:" + PANEL_PADDING + "px;border:2px solid #0b0c0e;border-radius:10px;"
                    + "background:" + BUTTON_BACKGROUND + ";box-shadow:0 6px 20px rgba(0,0,0,0.55);"
                    + "color:" + ROW_FOREGROUND + ";overflow-x:hidden;overflow-y:auto;";

    private static final String ROW_STYLE =
            "display:flex;align-items:center;box-sizing:border-box;height:" + ROW_HEIGHT + "px;"
                    + "padding:0 8px;border-radius:6px;cursor:pointer;font-size:12px;";

    private static final String ROW_NAME_STYLE =
            "flex:1 1 auto;overflow:hidden;white-space:nowrap;";

    private static final String ROW_ID_STYLE =
            "flex:0 0 auto;margin-left:8px;font-size:10px;color:#9aa1ab;";

    /**
     * 内置主题清单：与预览台 {@code t00-example.html} 的 THEMES 同源，加内置主题时这里补一行。
     * 作用域类按规范推导为 {@code <name>-theme}，不必单独写。
     * 变体主题必须排在它的基主题之后（下面反查当前主题时取最后一个命中的）。
     */
    private static final List<String> BUILT_IN_THEME_IDS = List.of(
            "t01-ore",
            "t03-claude-square",
            "t04-claude-round",
            "t05-curseforge",
            "t06-modrinth-light",
            "t07-modrinth-dark",
            "t08-arwes",
            "t09-arwes-transparent",
            "t10-neobrutalist",
            "t11-hand-drawn-doodle",
            "t12-retro-vintage",
            "t13-vaporwave",
            "t14-impressionist-oil",
            "t15-mecha",
            "ore");

    /** 变体主题 → 基主题：自己的 css 只写覆盖，作用域类要连基主题一起挂（与预览台的 baseScope 同义）。 */
    private static final Map<String, String> VARIANT_BASE_IDS = Map.of(
            "t09-arwes-transparent", "t08-arwes");

    private static volatile List<Theme> cachedThemes;

    /** 按钮的基准样式：默认造型 + 页面在标签上写的内联样式（作者的写在后，可以覆盖）。 */
    private String buttonBaseStyle = BUTTON_STYLE;
    /** 拖动后写死的定位片段；非空表示按钮已经被拖过，不再吃 right/bottom。 */
    private String positionStyle = "";
    private boolean hovering;
    private boolean dragging;
    /** 本次按下期间位移是否超过阈值；超过就抑制随后的 click，避免拖动结束时把面板弹开。 */
    private boolean dragged;
    private double grabOffsetX;
    private double grabOffsetY;
    private double pressX;
    private double pressY;
    /** 按下时量一次按钮尺寸就够了：拖动只改 left/top，尺寸不变。 */
    private double dragWidth = BUTTON_SIZE;
    private double dragHeight = BUTTON_SIZE;
    /** 递增令牌：连点两次时让先到的回调作废，只有最后一次选择能落地。 */
    private int switchToken;
    private List<Theme> cachedAvailableThemes;
    private Element panel;
    private final List<Element> rows = new ArrayList<>();

    private final Consumer<Event> outsidePointerListener = this::handleOutsidePointer;

    public ThemeSwitcher(Document document) {
        super(document, TAG_NAME);
        // DOM 解析期不触发挂载钩子，内建交互在这里登记；Element.init 换实例时会取下内建监听器，
        // 所以它们必须挂在构造过程里（同 Input/Select 的做法）。
        addInternalEventListener("mousedown", this::handleMouseDown);
        addInternalEventListener("mousemove", this::handleMouseMove);
        addInternalEventListener("mouseup", this::handleMouseUp);
        addInternalEventListener("click", this::handleClick);
        addInternalEventListener("mouseenter", event -> {
            hovering = true;
            refreshButtonStyle();
        });
        addInternalEventListener("mouseleave", event -> {
            hovering = false;
            refreshButtonStyle();
        });
    }

    @Override
    protected void onInitFromDom(Element origin) {
        String authorStyle = getAttribute("style");
        buttonBaseStyle = authorStyle == null || authorStyle.isBlank()
                ? BUTTON_STYLE
                : BUTTON_STYLE + authorStyle;
        refreshButtonStyle();
        // 按钮里放四颗主题色小圆点，不铺文字：任何字体、任何主题下都画得出来。
        for (String color : DOT_COLORS) {
            Element dot = Element.init(document.createElement("div"));
            dot.setAttribute("style", "width:10px;height:10px;border-radius:5px;background-color:"
                    + color + ";");
            appendChild(dot);
        }
    }

    @Override
    public void onDisconnectedFromDocument() {
        closePanel();
    }

    // ------------------------------------------------------------------
    // 拖动与展开
    // ------------------------------------------------------------------

    private void handleMouseDown(Event raw) {
        if (!(raw instanceof MouseEvent event) || event.button != 0) return;
        Element.DOMRect rect = measure();
        grabOffsetX = event.clientX - rect.x;
        grabOffsetY = event.clientY - rect.y;
        dragWidth = rect.width > 0 ? rect.width : BUTTON_SIZE;
        dragHeight = rect.height > 0 ? rect.height : BUTTON_SIZE;
        pressX = event.clientX;
        pressY = event.clientY;
        dragging = true;
        dragged = false;
        // 指针移出按钮后仍要收到 move/up：引擎把这两个事件按"按下元素"重定向。
        if (document != null) document.setPressedElement(this);
    }

    private void handleMouseMove(Event raw) {
        if (!dragging || !(raw instanceof MouseEvent event)) return;
        Document doc = document;
        if (doc == null) return;
        if (doc.getPressedElement() != this) {
            // 抬起事件丢了（比如指针离开窗口）就别再跟手，否则悬停会把按钮拖走。
            dragging = false;
            return;
        }
        if (!dragged
                && (Math.abs(event.clientX - pressX) > DRAG_THRESHOLD
                || Math.abs(event.clientY - pressY) > DRAG_THRESHOLD)) {
            dragged = true;
            // 拖起来就先收起面板：面板是按按钮位置算出来的，跟着跑只会碍事。
            closePanel();
        }
        if (!dragged) return;
        double left = clamp(event.clientX - grabOffsetX, 0, doc.getViewport().layoutWidth() - dragWidth);
        double top = clamp(event.clientY - grabOffsetY, 0, doc.getViewport().layoutHeight() - dragHeight);
        positionStyle = "left:" + px(left) + ";top:" + px(top) + ";right:auto;bottom:auto;";
        refreshButtonStyle();
    }

    private void handleMouseUp(Event raw) {
        dragging = false;
    }

    private void handleClick(Event raw) {
        // 按下到抬起的位移超过阈值就是一次拖动的收尾，不当作点击。
        // 这里按"当次按下点 vs 抬起点"判断，不靠标志位：抬起落在按钮外时不会有这次 click，
        // 标志位就会一直挂着，把下一次真正的点击吃掉。
        if (raw instanceof MouseEvent event
                && (Math.abs(event.clientX - pressX) > DRAG_THRESHOLD
                || Math.abs(event.clientY - pressY) > DRAG_THRESHOLD)) {
            return;
        }
        if (panel != null) closePanel();
        else openPanel();
    }

    private void refreshButtonStyle() {
        StringBuilder style = new StringBuilder(buttonBaseStyle);
        if (hovering) style.append("background:").append(BUTTON_BACKGROUND_HOVER).append(';');
        style.append(positionStyle);
        setAttribute("style", style.toString());
    }

    private void openPanel() {
        Document doc = document;
        if (doc == null || doc.body == null) return;
        List<Theme> themes = availableThemes();
        if (themes.isEmpty()) return;

        Element.DOMRect anchor = measure();
        double viewportWidth = doc.getViewport().layoutWidth();
        double viewportHeight = doc.getViewport().layoutHeight();
        // 行高、内边距、描边都是本类写死的常量，所以面板高度可以直接算出来，不用等布局。
        double panelHeight = themes.size() * ROW_HEIGHT + 2 * (PANEL_PADDING + PANEL_BORDER);
        panelHeight = Math.min(panelHeight, Math.max(ROW_HEIGHT, viewportHeight - 2 * VIEWPORT_MARGIN));
        double left = clamp(anchor.x, VIEWPORT_MARGIN, viewportWidth - PANEL_WIDTH - VIEWPORT_MARGIN);
        double top = anchor.y - panelHeight - PANEL_GAP;
        if (top < VIEWPORT_MARGIN) {
            // 上方放不下就翻到按钮下面。
            top = anchor.y + (anchor.height > 0 ? anchor.height : BUTTON_SIZE) + PANEL_GAP;
        }
        top = clamp(top, VIEWPORT_MARGIN, viewportHeight - panelHeight - VIEWPORT_MARGIN);

        Element created = Element.init(doc.createElement("div"));
        created.setAttribute("class", PANEL_CLASS);
        created.setTopLayer(true);
        created.setAttribute("style", PANEL_STYLE
                + "left:" + px(left) + ";top:" + px(top)
                + ";width:" + PANEL_WIDTH + "px;height:" + px(panelHeight) + ";");
        created.addEventListener("mousedown", Event::stopPropagation);
        created.addEventListener("contextmenu", event -> {
            event.preventDefault();
            event.stopPropagation();
        });
        // 面板开着的时候别的换肤路径（预览台的 <select>）也可能改主题，指针进面板时对一次高亮。
        created.addEventListener("mouseenter", event -> refreshHighlight());

        rows.clear();
        for (Theme theme : themes) {
            Element row = Element.init(doc.createElement("div"));
            row.setAttribute("class", ROW_CLASS);
            row.setAttribute("data-theme-id", theme.id());
            row.addEventListener("mouseenter", event -> setRowHover(row, true));
            row.addEventListener("mouseleave", event -> setRowHover(row, false));
            row.addEventListener("click", event -> requestTheme(theme));
            Element name = Element.init(doc.createElement("div"));
            name.setAttribute("style", ROW_NAME_STYLE);
            name.setTextContent(theme.label());
            Element id = Element.init(doc.createElement("div"));
            id.setAttribute("style", ROW_ID_STYLE);
            id.setTextContent(theme.id());
            row.appendChild(name);
            row.appendChild(id);
            rows.add(row);
            created.appendChild(row);
        }

        panel = created;
        doc.body.appendChild(created);
        doc.addEventListener("mousedown", outsidePointerListener, true);
        refreshHighlight();
        markDirty();
    }

    private void closePanel() {
        Element closing = panel;
        panel = null;
        rows.clear();
        Document doc = document;
        if (doc != null) doc.removeEventListener("mousedown", outsidePointerListener, true);
        if (closing != null && closing.parentNode != null) closing.remove();
        if (closing != null) markDirty();
    }

    private void handleOutsidePointer(Event event) {
        if (panel == null) return;
        Object target = event.target;
        if (target instanceof Element element && (panel.contains(element) || contains(element))) {
            return;
        }
        closePanel();
    }

    // ------------------------------------------------------------------
    // 换肤
    // ------------------------------------------------------------------

    void requestTheme(Theme theme) {
        Document doc = document;
        if (doc == null || doc.head == null || theme == null) return;
        int token = ++switchToken;
        List<String> sheets = new ArrayList<>(2);
        if (theme.baseId() != null) sheets.add(theme.baseId());
        sheets.add(theme.id());

        SwitchRequest request = new SwitchRequest(token, theme, sheets.size());
        for (String sheetId : sheets) {
            Element link = Element.init(doc.createElement("link"));
            link.setAttribute("rel", "stylesheet");
            link.setAttribute(SHEET_MARK, "1");
            link.setAttribute("href", sheetHref(sheetId));
            link.addEventListener("load", event -> request.settle(link, true));
            link.addEventListener("error", event -> request.settle(link, false));
            // 先登记再挂：href 解析不出时会同步派发 error，那次 settle 要能认领这张表。
            request.track(link);
            doc.head.appendChild(link);
            // 同一轮里前一张表就同步失败、整次已经作废时，刚挂上的这张要跟着收回。
            if (request.failed()) detach(link);
        }
    }

    /** 一次换肤的簿记：等所有新表 load 到齐，再落地（换类 → 摘旧表 → 通知）。 */
    private final class SwitchRequest {
        private final int token;
        private final Theme theme;
        private final List<Element> links = new ArrayList<>(2);
        private final List<Element> loaded = new ArrayList<>(2);
        private int pending;
        private boolean failed;

        SwitchRequest(int token, Theme theme, int pending) {
            this.token = token;
            this.theme = theme;
            this.pending = pending;
        }

        void track(Element link) {
            links.add(link);
        }

        boolean failed() {
            return failed;
        }

        void settle(Element link, boolean ok) {
            if (token != switchToken) {
                // 已被更晚的一次选择取代：把这张表摘掉，交给后来者收尾。
                detach(link);
                return;
            }
            if (ok) loaded.add(link);
            else failed = true;
            if (--pending > 0) return;
            if (failed) {
                // 有一张表没加载成功就整次作废：把这次插的表全部收回，当前主题继续可用。
                for (Element sheet : links) detach(sheet);
                return;
            }
            adopt();
        }

        private void adopt() {
            Document doc = document;
            if (doc == null || doc.body == null) return;
            // 先摘掉根元素上所有"别的主题"的作用域类：皮肤类的约定就是以 -theme 结尾，
            // 内置主题之外的皮肤（预览台脚本挂的、页面静态挂的 ore 之类）也要摘干净，
            // 不然会和新皮肤叠在一起。本次要挂的两个类除外。
            List<String> staleScopes = new ArrayList<>(2);
            for (String name : doc.body.getClassNames()) {
                if (!name.endsWith(SCOPE_SUFFIX)) continue;
                if (name.equals(theme.scope()) || name.equals(theme.baseScope())) continue;
                staleScopes.add(name);
            }
            Element.DOMTokenList classList = doc.body.getClassList();
            classList.remove(staleScopes.toArray(new String[0]));
            // 基主题的类先挂，作用域类后挂。
            if (theme.baseScope() != null) classList.add(theme.baseScope());
            classList.add(theme.scope());
            // 新表已经生效，这时才摘旧表：静态 <head> 里那条、预览台脚本插的、上次切换插的，
            // 统一按标记属性清掉（作用域隔离保证换类那一瞬间不会串皮肤）。
            for (Element child : new ArrayList<>(doc.head.children)) {
                if (!"LINK".equals(child.tagName) || !child.hasAttribute(SHEET_MARK)) continue;
                if (loaded.contains(child)) continue;
                detach(child);
            }
            refreshHighlight();
            notifyThemeChanged();
        }

        private void notifyThemeChanged() {
            Event event = new Event.CustomEvent(THEME_CHANGE_EVENT, theme.id(), true);
            event.target = ThemeSwitcher.this;
            event.currentTarget = ThemeSwitcher.this;
            Event.tiggerEvent(event);
        }
    }

    private static void detach(Element element) {
        if (element != null && element.parentNode != null) element.remove();
    }

    private void refreshHighlight() {
        String currentId = currentThemeId();
        for (Element row : rows) applyRowState(row, currentId);
    }

    private void setRowHover(Element row, boolean hover) {
        if (hover) row.setAttribute("data-hover", "1");
        else row.removeAttribute("data-hover");
        applyRowState(row, currentThemeId());
    }

    private void applyRowState(Element row, String currentId) {
        boolean active = currentId != null && currentId.equals(row.getAttribute("data-theme-id"));
        boolean hover = !active && "1".equals(row.getAttribute("data-hover"));
        row.setAttribute("style", ROW_STYLE
                + "background-color:" + (active ? ROW_BACKGROUND_ACTIVE : (hover ? ROW_BACKGROUND_HOVER : ROW_BACKGROUND)) + ";"
                + "color:" + (active ? ROW_FOREGROUND_ACTIVE : ROW_FOREGROUND) + ";");
    }

    /**
     * 当前主题从文档根元素上的作用域类读出来，而不是自己记一份：这样预览台脚本换过主题也能跟上。
     * 变体主题会和基主题的类同时在场上（t09 挂 t09 + t08），所以取最后一个命中的
     * ——内置主题表里基主题排在变体前面。
     */
    private String currentThemeId() {
        Document doc = document;
        if (doc == null || doc.body == null) return null;
        Element.DOMTokenList classList = doc.body.getClassList();
        String current = null;
        for (Theme theme : allThemes()) {
            if (classList.contains(theme.scope())) current = theme.id();
        }
        return current;
    }

    // ------------------------------------------------------------------
    // 主题清单与几何
    // ------------------------------------------------------------------

    /** 内置主题表（惰性缓存，内置主题随 jar 走，不会变）。 */
    static List<Theme> allThemes() {
        List<Theme> cached = cachedThemes;
        if (cached != null) return cached;
        List<Theme> built = new ArrayList<>(BUILT_IN_THEME_IDS.size());
        for (String id : BUILT_IN_THEME_IDS) {
            String baseId = VARIANT_BASE_IDS.get(id);
            built.add(new Theme(
                    id,
                    labelOf(id),
                    id + SCOPE_SUFFIX,
                    baseId,
                    baseId == null ? null : baseId + SCOPE_SUFFIX));
        }
        List<Theme> immutable = List.copyOf(built);
        cachedThemes = immutable;
        return immutable;
    }

    /** 只列出资源真的在的主题；一个都查不到（比如没有 loader 的环境）就退回全表。 */
    private List<Theme> availableThemes() {
        if (cachedAvailableThemes != null) return cachedAvailableThemes;
        List<Theme> found = new ArrayList<>();
        for (Theme theme : allThemes()) {
            if (sheetExists(theme.id())) found.add(theme);
        }
        if (found.isEmpty()) found.addAll(allThemes());
        cachedAvailableThemes = found;
        return cachedAvailableThemes;
    }

    private static boolean sheetExists(String id) {
        try (InputStream stream = ClientLoader.getResourceStream(sheetPath(id))) {
            return stream != null;
        } catch (IOException exception) {
            return false;
        }
    }

    /** 逻辑资源路径（相对资源根），用于探测资源是否存在。 */
    private static String sheetPath(String id) {
        return THEME_ROOT + id + "/" + id + ".css";
    }

    /** {@code <link href>}：写绝对逻辑路径，任何页面（不只在 theme/ 目录下）都能解析到。 */
    private static String sheetHref(String id) {
        return "/" + sheetPath(id);
    }

    /** {@code t09-arwes-transparent} → {@code Arwes Transparent}：清单里显示得好看一点。 */
    private static String labelOf(String id) {
        String name = id.replaceFirst("^t\\d+-", "");
        StringBuilder label = new StringBuilder(name.length());
        boolean upperNext = true;
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            if (c == '-') {
                label.append(' ');
                upperNext = true;
                continue;
            }
            label.append(upperNext ? Character.toUpperCase(c) : c);
            upperNext = false;
        }
        return label.toString();
    }

    /**
     * 读按钮的已提交几何。布局提交在 20Hz tick，而输入事件可能早于它，
     * 所以先把待提交的样式重算/渲染状态推平再读（同 SelectPopup 读锚点的做法）。
     */
    private Element.DOMRect measure() {
        Document doc = document;
        if (doc != null && doc.isActive()) {
            doc.commitPendingStyleRecalcForRender();
            if (!doc.getPaintList().isEmpty() && doc.hasPendingRenderState()) {
                doc.commitRenderState();
            }
        }
        return getBoundingClientRect();
    }

    private void markDirty() {
        if (document != null && document.body != null) {
            document.markDirty(document.body,
                    Drawer.RELAYOUT | Drawer.REPAINT | Drawer.REORDER | Drawer.HITTEST);
        }
    }

    private static double clamp(double value, double min, double max) {
        if (max < min) max = min;
        return value < min ? min : Math.min(value, max);
    }

    private static String px(double value) {
        return String.format(Locale.ROOT, "%.1fpx", value);
    }

    /** 一个内置主题：id、显示名、作用域类，以及变体主题的基主题。 */
    record Theme(String id, String label, String scope, String baseId, String baseScope) {
    }
}
