package com.sighs.apricityui.ui;

import com.sighs.apricityui.event.MouseEvent;
import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.render.Drawer;
import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.event.Event;

/** Reusable document modal: title drag is always enabled; resize is opt-in. */
public final class DialogWindow {
    public record Options(String title, double width, double height, boolean resizable,
                          String overlayClass, String windowClass, String headingClass,
                          String titleClass, String closeClass, String contentClass,
                          String titleIconClass, boolean maximizable) {
        public Options(String title, double width, double height, boolean resizable,
                       String overlayClass, String windowClass, String headingClass,
                       String titleClass, String closeClass) {
            this(title, width, height, resizable, overlayClass, windowClass, headingClass,
                    titleClass, closeClass, "aui-dialog-content", "", false);
        }

        public Options(String title, double width, double height, boolean resizable,
                       String overlayClass, String windowClass, String headingClass,
                       String titleClass, String closeClass, String contentClass,
                       String titleIconClass) {
            this(title, width, height, resizable, overlayClass, windowClass, headingClass,
                    titleClass, closeClass, contentClass, titleIconClass, false);
        }

        public static Options of(String title, double width, double height, boolean resizable) {
            return new Options(title, width, height, resizable, "aui-dialog-overlay", "aui-dialog-window",
                    "aui-dialog-heading", "aui-dialog-title", "aui-dialog-close",
                    "aui-dialog-content", "aui-dialog-title-icon", false);
        }
    }
    private final Document document;
    private final Options options;
    private final Runnable onClose;
    private Element overlay, window, content;
    private Element maximizeButton;
    private double x, y, width, height, startX, startY, startWidth, startHeight, startLeft, startTop;
    private double restoredX, restoredY, restoredWidth, restoredHeight;
    private boolean maximized;
    private Mode mode = Mode.NONE;

    private DialogWindow(Document document, Options options, Runnable onClose) {
        this.document = document; this.options = options; this.onClose = onClose;
    }
    public static DialogWindow open(Document document, Options options, Runnable onClose) {
        DialogWindow result = new DialogWindow(document, options, onClose); result.create(); return result;
    }
    public Element content() { return content; }
    public Element window() { return window; }
    public boolean isOpen() { return overlay != null && overlay.isConnected(); }
    public void close() {
        if (overlay != null) overlay.remove();
        overlay = window = content = null;
        maximizeButton = null;
        maximized = false;
        if (onClose != null) onClose.run();
    }
    private void create() {
        double vw = document.getViewport().layoutWidth(), vh = document.getViewport().layoutHeight();
        width = options.width() > 0 ? options.width() : Math.min(720, vw - 48);
        height = options.height() > 0 ? options.height() : 0;
        x = Math.max(12, (vw - width) / 2); y = height > 0 ? Math.max(12, (vh - height) / 2) : 48;
        overlay = el("DIV", options.overlayClass());
        overlay.setTopLayer(true);
        overlay.setAttribute("style", "position:fixed;inset:0;z-index:9000;");
        window = el("DIV", options.windowClass()); applyBounds();
        Element heading = el("DIV", options.headingClass());
        heading.setAttribute("style", "cursor:move;user-select:none;");
        Element title = el("DIV", options.titleClass()); title.setAttribute("style", "user-select:none;");
        if (options.titleIconClass() != null && !options.titleIconClass().isBlank()) {
            title.append(el("DIV", options.titleIconClass()));
        }
        Element titleText = el("SPAN", "aui-dialog-title-text"); titleText.setTextContent(options.title()); title.append(titleText);
        Element controls = el("DIV", "aui-dialog-controls");
        controls.setAttribute("style", "display:flex;align-items:center;gap:6px;position:relative;z-index:1;flex-shrink:0;");
        if (options.maximizable()) {
            maximizeButton = el("BUTTON", "dialog-maximize");
            maximizeButton.setAttribute("type", "button");
            maximizeButton.addEventListener("mousedown", e -> e.stopPropagation());
            maximizeButton.addEventListener("click", e -> { e.stopPropagation(); toggleMaximized(); });
            updateMaximizeButton();
            controls.append(maximizeButton);
        }
        Element close = el("BUTTON", options.closeClass()); close.setTextContent("\u2715");
        close.setAttribute("type", "button");
        close.addEventListener("mousedown", e -> e.stopPropagation());
        close.addEventListener("click", e -> { e.stopPropagation(); close(); });
        controls.append(close);
        heading.addEventListener("mousedown", e -> begin(e, Mode.MOVE)); heading.append(title); heading.append(controls); window.append(heading);
        content = el("DIV", options.contentClass()); content.setAttribute("style", height > 0 ? "position:relative;flex:1;min-height:0;" : "position:relative;"); window.append(content);
        if (options.resizable()) for (Mode resize : new Mode[]{Mode.N,Mode.NE,Mode.E,Mode.SE,Mode.S,Mode.SW,Mode.W,Mode.NW}) handle(resize);
        overlay.addEventListener("mousemove", this::move); overlay.addEventListener("mouseup", e -> mode = Mode.NONE);
        overlay.append(window); document.body.append(overlay); dirty();
    }
    private void handle(Mode mode) { Element e=el("DIV", "aui-dialog-resize"); e.setAttribute("style", "position:absolute;z-index:2;"+mode.handleStyle()); e.addEventListener("mousedown", v->begin(v,mode)); window.append(e); }
    private void begin(Event event, Mode next) { if (maximized || !(event instanceof MouseEvent e)) return; mode=next; startX=e.clientX;startY=e.clientY;startLeft=x;startTop=y;startWidth=width;startHeight=height;event.stopPropagation(); }
    private void move(Event event) { if (maximized || mode==Mode.NONE || !(event instanceof MouseEvent e)) return; double dx=e.clientX-startX,dy=e.clientY-startY; if(mode.move){x=startLeft+dx;y=startTop+dy;} if(mode.e){width=Math.max(360,startWidth+dx);} if(mode.s){height=Math.max(240,startHeight+dy);} if(mode.w){width=Math.max(360,startWidth-dx);x=startLeft+startWidth-width;} if(mode.n){height=Math.max(240,startHeight-dy);y=startTop+startHeight-height;} applyBounds();dirty();event.stopPropagation(); }
    private void toggleMaximized() {
        if (!options.maximizable()) return;
        if (maximized) {
            x = restoredX;
            y = restoredY;
            width = restoredWidth;
            height = restoredHeight;
            maximized = false;
        } else {
            restoredX = x;
            restoredY = y;
            restoredWidth = width;
            restoredHeight = height;
            x = 0;
            y = 0;
            width = Math.max(0d, document.getViewport().layoutWidth());
            height = Math.max(0d, document.getViewport().layoutHeight());
            maximized = true;
        }
        mode = Mode.NONE;
        applyBounds();
        updateMaximizeButton();
        dirty();
    }
    private void updateMaximizeButton() {
        if (maximizeButton == null) return;
        maximizeButton.setTextContent(maximized ? "\u25A3" : "\u25A1");
        maximizeButton.setAttribute("class", maximized ? "dialog-maximize is-maximized" : "dialog-maximize");
        maximizeButton.setAttribute("aria-label", maximized ? "Restore window" : "Maximize window");
        maximizeButton.setAttribute("title", maximized ? "Restore window" : "Maximize window");
    }
    private void applyBounds() { String style="position:absolute;left:"+px(x)+";top:"+px(y)+";width:"+px(width)+";pointer-events:auto;"; if(height>0) style+="height:"+px(height)+";display:flex;flex-direction:column;"; window.setAttribute("class", options.windowClass() + (maximized ? " maximized" : "")); window.setAttribute("style",style); }
    private Element el(String tag,String cls){Element e=Element.init(document.createElement(tag));e.setAttribute("class",cls);return e;}
    private void dirty(){if(document.body!=null)document.markDirty(document.body, Drawer.RELAYOUT|Drawer.REPAINT|Drawer.REORDER);}
    private static String px(double n){return String.format(java.util.Locale.ROOT,"%.2fpx",n);}
    private enum Mode { NONE(false,false,false,false,false,""),MOVE(false,false,false,false,true,""),N(true,false,false,false,false,"top:-5px;left:8px;right:8px;height:10px;cursor:n-resize;"),NE(true,true,false,false,false,"top:-5px;right:-5px;width:12px;height:12px;cursor:ne-resize;"),E(false,true,false,false,false,"top:8px;right:-5px;bottom:8px;width:10px;cursor:e-resize;"),SE(false,true,false,true,false,"right:-5px;bottom:-5px;width:12px;height:12px;cursor:se-resize;"),S(false,false,false,true,false,"left:8px;right:8px;bottom:-5px;height:10px;cursor:s-resize;"),SW(false,false,true,true,false,"left:-5px;bottom:-5px;width:12px;height:12px;cursor:sw-resize;"),W(false,false,true,false,false,"top:8px;left:-5px;bottom:8px;width:10px;cursor:w-resize;"),NW(true,false,true,false,false,"top:-5px;left:-5px;width:12px;height:12px;cursor:nw-resize;"); final boolean n,e,w,s,move;final String h;Mode(boolean n,boolean e,boolean w,boolean s,boolean move,String h){this.n=n;this.e=e;this.w=w;this.s=s;this.move=move;this.h=h;}String handleStyle(){return h;} }
}
