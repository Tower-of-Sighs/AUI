package com.sighs.apricityui.element;

import com.sighs.apricityui.ApricityUI;
import com.sighs.apricityui.event.Event;
import com.sighs.apricityui.event.MouseEvent;
import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.loader.ClientLoader;
import com.sighs.apricityui.loader.Loader;
import com.sighs.apricityui.registry.annotation.ElementRegister;
import com.sighs.apricityui.render.Drawer;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Consumer;

/**
 * 内置主题切换器：页面里写一个标签 {@code <theme-switcher></theme-switcher>} 即可。
 *
 * <p>它是一个悬浮按钮（{@code position:fixed}），按住可以拖到视口任意位置，点开是内置主题清单，
 * 选中即换肤。定位与拖拽由 Java 负责，外观直接复用当前主题的 {@code .icon-button} 与
 * {@code .menu}——换到什么主题，按钮和清单就长成那个主题的控件，不另画一套。</p>
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

    /**
     * 换肤完成事件名，{@code detail} 为主题 id；在按钮上派发，冒泡到 document。
     */
    public static final String THEME_CHANGE_EVENT = "aui-theme-change";

    /**
     * 主题样式表标记：与预览台同约定，双方都能认出并清掉对方的旧表。
     */
    private static final String SHEET_MARK = "data-theme-css";

    /**
     * 逻辑资源根：主题目录固定为 {@code /apricityui/theme/}，作用域类固定为 {@code <name>-theme}。
     */
    private static final String THEME_ROOT = "apricityui/theme/";
    private static final String SCOPE_SUFFIX = "-theme";

    private static final String PANEL_CLASS = "aui-theme-switcher-panel";
    private static final String ROW_CLASS = "aui-theme-switcher-row";

    private static final int Z_INDEX = 9990;
    private static final int BUTTON_SIZE = 44;
    private static final int PANEL_WIDTH = 240;
    private static final int MIN_PANEL_HEIGHT = 96;
    /** 上方剩余空间小于这个值时优先翻到按钮下方。 */
    private static final int PANEL_MIN_ABOVE = 160;
    private static final int PANEL_GAP = 10;
    private static final int VIEWPORT_MARGIN = 8;
    /**
     * 判定"这是一次拖动而不是点击"的位移阈值（px）。
     */
    private static final double DRAG_THRESHOLD = 3;

    /**
     * 按钮只保留定位与尺寸，外观全部交给当前主题的 {@code .icon-button}。
     * 这样 t02-create 下是黄铜像素按钮，Modrinth 下是圆角按钮，不再另画一套。
     */
    private static final String BUTTON_STYLE =
            "position:fixed;right:18px;bottom:18px;"
                    + "width:" + BUTTON_SIZE + "px;height:" + BUTTON_SIZE + "px;min-width:0;min-height:0;"
                    + "box-sizing:border-box;display:flex;align-items:center;justify-content:center;padding:0;"
                    + "z-index:" + Z_INDEX + ";cursor:pointer;overflow:hidden;";

    /**
     * 面板用主题的 {@code .menu}：边框、底纹、行高、激活条都跟当前主题走。
     * 这里只负责定位、限宽和滚动。
     */
    private static final String PANEL_STYLE =
            "position:fixed;z-index:" + Z_INDEX + ";box-sizing:border-box;"
                    + "width:" + PANEL_WIDTH + "px;height:auto;min-width:0;"
                    + "max-width:calc(100vw - " + (2 * VIEWPORT_MARGIN) + "px);"
                    + "margin:0;overflow-x:hidden;overflow-y:auto;";

    /**
     * 按钮里的图标：一件衬衫。换肤 = 换装；颜色跟随当前主题的 {@code --ink}，
     * 不依赖字体，也不写死颜色。
     */
    private static final String ICON_STYLE =
            "width:22px;height:22px;box-sizing:border-box;"
                    + "background-color:var(--ink, #dfe3e8);"
                    + "clip-path:polygon("
                    + "16.7% 20.8%, 37.5% 20.8%, 50% 33.3%, 62.5% 20.8%, 83.3% 20.8%,"
                    + "95.8% 37.5%, 83.3% 50%, 70.8% 41.7%, 70.8% 87.5%, 29.2% 87.5%,"
                    + "29.2% 41.7%, 16.7% 50%, 4.2% 37.5%"
                    + ");"
                    + "pointer-events:none;";

    private static final String ROW_NAME_LAYOUT =
            "flex:1 1 auto;min-width:0;overflow:hidden;white-space:nowrap;text-overflow:ellipsis;";

    private static final String ROW_ID_LAYOUT =
            "flex:0 1 auto;max-width:52%;margin-left:auto;overflow:hidden;white-space:nowrap;text-overflow:ellipsis;";

    /**
     * 目录扫描失败时的兜底清单。正常运行时主题列表来自
     * {@code /apricityui/theme/} 下所有 t 开头的文件夹；只有资源管理器尚未就绪
     * 或纯单测环境才退回这里。不带 t 的旧主题（ore / ae）不参与。
     */
    private static final List<String> FALLBACK_THEME_IDS = List.of(
            "t01-ore",
            "t02-create",
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
            "t16-terminal");

    /**
     * 变体主题 → 基主题：自己的 css 只写覆盖，作用域类要连基主题一起挂（与预览台的 baseScope 同义）。
     */
    private static final Map<String, String> VARIANT_BASE_IDS = Map.of(
            "t09-arwes-transparent", "t08-arwes");

    private final List<Element> rows = new ArrayList<>();
    /**
     * 按钮的基准样式：默认造型 + 页面在标签上写的内联样式（作者的写在后，可以覆盖）。
     */
    private String buttonBaseStyle = BUTTON_STYLE;
    /**
     * 拖动后写死的定位片段；非空表示按钮已经被拖过，不再吃 right/bottom。
     */
    private String positionStyle = "";
    private boolean dragging;
    /**
     * 本次按下期间位移是否超过阈值；超过就抑制随后的 click，避免拖动结束时把面板弹开。
     */
    private boolean dragged;
    private double grabOffsetX;
    private double grabOffsetY;
    private double pressX;
    private double pressY;
    /**
     * 按下时量一次按钮尺寸就够了：拖动只改 left/top，尺寸不变。
     */
    private double dragWidth = BUTTON_SIZE;
    private double dragHeight = BUTTON_SIZE;
    /**
     * 递增令牌：连点两次时让先到的回调作废，只有最后一次选择能落地。
     */
    private int switchToken;
    private Element panel;

    public ThemeSwitcher(Document document) {
        super(document, TAG_NAME);
        // DOM 解析期不触发挂载钩子，内建交互在这里登记；Element.init 换实例时会取下内建监听器，
        // 所以它们必须挂在构造过程里（同 Input/Select 的做法）。
        addInternalEventListener("mousedown", this::handleMouseDown);
        addInternalEventListener("mousemove", this::handleMouseMove);
        addInternalEventListener("mouseup", this::handleMouseUp);
        addInternalEventListener("click", this::handleClick);
    }    private final Consumer<Event> outsidePointerListener = this::handleOutsidePointer;

    private static void detach(Element element) {
        if (element != null && element.parentNode != null) element.remove();
    }

    /**
     * 扫描主题目录，而不是维护一张写死的清单：
     *
     * <p>只收 {@code /apricityui/theme/t*} 的文件夹，并且必须存在同名的
     * {@code <id>/<id>.css}。路径来源是 {@link ClientLoader#listFinalStaticResources()}，
     * 它合并了 mod 资源包、游戏目录与 dev 资源根，所以 jar 内主题和开发中的新主题
     * 都会在资源重载后自动出现。目录扫描失败时退回 {@link #FALLBACK_THEME_IDS}。</p>
     *
     * <p>按 id 排序，变体主题的基主题自然排在前面（{@link #currentThemeId()} 依赖这一点）。</p>
     */
    static List<Theme> allThemes() {
        List<String> ids = new ArrayList<>(discoverThemeIds());
        if (ids.isEmpty()) {
            ApricityUI.LOGGER.debug("[AUI Theme] no theme folders discovered under {}; using fallback list", THEME_ROOT);
            ids.addAll(FALLBACK_THEME_IDS);
        }
        List<Theme> built = new ArrayList<>(ids.size());
        for (String id : ids) {
            String baseId = VARIANT_BASE_IDS.get(id);
            built.add(new Theme(
                    id,
                    labelOf(id),
                    id + SCOPE_SUFFIX,
                    baseId,
                    baseId == null ? null : baseId + SCOPE_SUFFIX));
        }
        return List.copyOf(built);
    }

    /**
     * 从静态资源表中挑出 t 开头的主题文件夹。
     * 主题目录下的 css / 图片 / 示例页都可能是资源；只有
     * {@code theme/<id>/<id>.css} 这一条算主题入口。
     */
    private static Set<String> discoverThemeIds() {
        Set<String> ids = new TreeSet<>();
        for (Loader.StaticResourceEntry entry : ClientLoader.listFinalStaticResources()) {
            String path = normalizeThemePath(entry.path());
            if (!path.startsWith(THEME_ROOT)) continue;
            String rest = path.substring(THEME_ROOT.length());
            int slash = rest.indexOf('/');
            if (slash <= 0) continue;
            String id = rest.substring(0, slash);
            if (!id.startsWith("t")) continue;
            if (!rest.substring(slash + 1).equals(id + ".css")) continue;
            ids.add(id);
        }
        return ids;
    }

    private static String normalizeThemePath(String path) {
        if (path == null) return "";
        String normalized = path.replace('\\', '/');
        while (normalized.startsWith("/")) normalized = normalized.substring(1);
        while (normalized.startsWith("./")) normalized = normalized.substring(2);
        return normalized;
    }

    private static boolean sheetExists(String id) {
        try (InputStream stream = ClientLoader.getResourceStream(sheetPath(id))) {
            return stream != null;
        } catch (IOException exception) {
            return false;
        }
    }

    // ------------------------------------------------------------------
    // 拖动与展开
    // ------------------------------------------------------------------

    /**
     * 逻辑资源路径（相对资源根），用于探测资源是否存在。
     */
    private static String sheetPath(String id) {
        return THEME_ROOT + id + "/" + id + ".css";
    }

    /**
     * {@code <link href>}：写绝对逻辑路径，任何页面（不只在 theme/ 目录下）都能解析到。
     */
    private static String sheetHref(String id) {
        return "/" + sheetPath(id);
    }

    /**
     * {@code t09-arwes-transparent} → {@code Arwes Transparent}：清单里显示得好看一点。
     */
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

    private static double clamp(double value, double min, double max) {
        if (max < min) max = min;
        return value < min ? min : Math.min(value, max);
    }

    private static String px(double value) {
        return String.format(Locale.ROOT, "%.1fpx", value);
    }

    @Override
    protected void onInitFromDom(Element origin) {
        String authorStyle = getAttribute("style");
        buttonBaseStyle = authorStyle == null || authorStyle.isBlank()
                ? BUTTON_STYLE
                : BUTTON_STYLE + authorStyle;
        // 与页面同化：外观交给当前主题的 .icon-button，自己只保留定位/尺寸。
        Element.DOMTokenList classes = getClassList();
        if (!classes.contains("icon-button")) classes.add("icon-button");
        if (!classes.contains("aui-theme-switcher-button")) classes.add("aui-theme-switcher-button");
        refreshButtonStyle();
        // 衬衫图标：换肤 = 换装；颜色走当前主题的 --ink。
        Element icon = Element.init(document.createElement("div"));
        icon.setAttribute("style", ICON_STYLE);
        appendChild(icon);
    }

    @Override
    public void onDisconnectedFromDocument() {
        closePanel();
    }

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

    // ------------------------------------------------------------------
    // 换肤
    // ------------------------------------------------------------------

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
        // 外观由主题的 .icon-button 负责；这里只维护位置。
        setAttribute("style", buttonBaseStyle + positionStyle);
    }

    private void openPanel() {
        Document doc = document;
        if (doc == null || doc.body == null) return;
        List<Theme> themes = availableThemes();
        if (themes.isEmpty()) return;

        Element.DOMRect anchor = measure();
        double viewportWidth = doc.getViewport().layoutWidth();
        double viewportHeight = doc.getViewport().layoutHeight();
        double buttonHeight = anchor.height > 0 ? anchor.height : BUTTON_SIZE;

        // 面板高度交给主题的行高，这里只按剩余空间给 max-height：上方不够就翻到按钮下面。
        double spaceAbove = anchor.y - VIEWPORT_MARGIN - PANEL_GAP;
        double spaceBelow = viewportHeight - (anchor.y + buttonHeight) - VIEWPORT_MARGIN - PANEL_GAP;
        boolean below = spaceAbove < PANEL_MIN_ABOVE && spaceBelow > spaceAbove;
        double maxHeight = Math.max(MIN_PANEL_HEIGHT, below ? spaceBelow : spaceAbove);
        double left = clamp(anchor.x, VIEWPORT_MARGIN, viewportWidth - PANEL_WIDTH - VIEWPORT_MARGIN);

        StringBuilder layout = new StringBuilder(PANEL_STYLE)
                .append("left:").append(px(left)).append(';')
                .append("max-height:").append(px(maxHeight)).append(';');
        if (below) {
            layout.append("top:").append(px(anchor.y + buttonHeight + PANEL_GAP)).append(';')
                    .append("bottom:auto;");
        } else {
            layout.append("bottom:").append(px(viewportHeight - anchor.y + PANEL_GAP)).append(';')
                    .append("top:auto;");
        }

        Element created = Element.init(doc.createElement("div"));
        created.setAttribute("class", "menu " + PANEL_CLASS);
        created.setTopLayer(true);
        created.setAttribute("style", layout.toString());
        created.addEventListener("mousedown", Event::stopPropagation);
        created.addEventListener("contextmenu", event -> {
            event.preventDefault();
            event.stopPropagation();
        });
        // 面板开着的时候别的换肤路径（预览台的 <select>）也可能改主题，指针进面板时对一次高亮。
        created.addEventListener("mouseenter", event -> refreshHighlight());

        rows.clear();
        for (Theme theme : themes) {
            // 行本身也交给主题的 .menu-item：hover / active 都由主题 CSS 画。
            Element row = Element.init(doc.createElement("div"));
            row.setAttribute("class", "menu-item " + ROW_CLASS);
            row.setAttribute("data-theme-id", theme.id());
            row.addEventListener("click", event -> requestTheme(theme));
            Element name = Element.init(doc.createElement("span"));
            name.setAttribute("style", ROW_NAME_LAYOUT);
            name.setTextContent(theme.label());
            Element id = Element.init(doc.createElement("span"));
            id.setAttribute("class", "menu-shortcut");
            id.setAttribute("style", ROW_ID_LAYOUT);
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
    // 主题清单与几何
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

    private void refreshHighlight() {
        String currentId = currentThemeId();
        for (Element row : rows) applyRowState(row, currentId);
    }

    private void applyRowState(Element row, String currentId) {
        boolean active = currentId != null && currentId.equals(row.getAttribute("data-theme-id"));
        Element.DOMTokenList classes = row.getClassList();
        if (active) classes.add("active");
        else classes.remove("active");
        row.setAttribute("aria-selected", active ? "true" : "false");
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

    /**
     * 只列出资源真的在的主题；一个都查不到（比如没有 loader 的环境）就退回全表。
     * 不做实例级缓存：资源重载后新加的主题要能在下一次打开面板时出现。
     */
    private List<Theme> availableThemes() {
        List<Theme> all = allThemes();
        List<Theme> found = new ArrayList<>();
        for (Theme theme : all) {
            if (sheetExists(theme.id())) found.add(theme);
        }
        if (found.isEmpty()) found.addAll(all);
        return found;
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

    /**
     * 一个内置主题：id、显示名、作用域类，以及变体主题的基主题。
     */
    record Theme(String id, String label, String scope, String baseId, String baseScope) {
    }

    /**
     * 一次换肤的簿记：等所有新表 load 到齐，再落地（换类 → 摘旧表 → 通知）。
     */
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


}
