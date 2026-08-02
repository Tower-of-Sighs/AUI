package com.sighs.apricityui.ui;

import com.sighs.apricityui.event.MouseEvent;
import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.render.Drawer;
import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.event.Event;
import com.sighs.apricityui.render.Operation;

import java.util.ArrayList;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/** Global advanced color picker. A completed empty result represents cancellation. */
public final class ColorPicker {
    private static final String PATH = "devtools/color-picker.html";
    private static final double WIDTH = 288, HEIGHT = 476, GAP = 8;
    private static ColorPicker active;

    private final Document document;
    private final boolean ownsDocument;
    private final CompletableFuture<Optional<String>> result = new CompletableFuture<>();
    private final ColorState color;
    private String format = "hex";
    private Element root, svPanel, hueSlider, alphaSlider, previewFill, previewValue, alphaFill, svHandle, hueHandle, alphaHandle, inputs;
    private Element dragTarget;

    private ColorPicker(Document document, boolean ownsDocument, String initialColor) {
        this.document = document;
        this.ownsDocument = ownsDocument;
        this.color = ColorState.parse(initialColor);
    }

    public static synchronized CompletableFuture<Optional<String>> pick(String initialColor) {
        Document document = Document.create(PATH);
        if (document == null || document.body == null) return CompletableFuture.completedFuture(Optional.empty());
        document.setReloadPersistent(true);
        return open(document, null, initialColor, true);
    }

    public static synchronized CompletableFuture<Optional<String>> pickIn(Document document, Element anchor, String initialColor) {
        return open(document, anchor, initialColor, false);
    }

    public static synchronized boolean isOpen() { return active != null && active.root != null && active.root.isConnected(); }
    public static synchronized void closeActive() { if (active != null) active.finish(Optional.empty()); }

    private static CompletableFuture<Optional<String>> open(Document document, Element anchor, String initialColor,
                                                             boolean ownsDocument) {
        if (document == null || document.body == null) return CompletableFuture.completedFuture(Optional.empty());
        Tooltip.hide();
        if (active != null) active.finish(Optional.empty());
        active = new ColorPicker(document, ownsDocument, initialColor);
        active.render(anchor);
        return active.result;
    }

    private void render(Element anchor) {
        root = el("DIV", "color-picker"); root.setTopLayer(true); root.setAttribute("data-aui-color-picker", "true");
        Element header = el("DIV", "cp-header"); header.append(text("DIV", "cp-header-left", "COLOR"));
        Element close = button("cp-close", "x"); close.addEventListener("click", event -> finish(Optional.empty())); header.append(close); root.append(header);
        Element body = el("DIV", "cp-body");
        svPanel = el("DIV", "cp-sv-panel"); svPanel.append(el("DIV", "cp-sv-white")); svPanel.append(el("DIV", "cp-sv-black")); svHandle = el("DIV", "cp-sv-handle"); svPanel.append(svHandle); body.append(svPanel);
        hueSlider = slider("cp-slider cp-slider-hue"); hueHandle = el("DIV", "cp-slider-handle"); hueSlider.append(hueHandle); body.append(sliderRow("H", hueSlider));
        alphaSlider = slider("cp-slider cp-slider-alpha"); alphaFill = el("DIV", "cp-slider-alpha-fill"); alphaHandle = el("DIV", "cp-slider-handle"); alphaSlider.append(alphaFill); alphaSlider.append(alphaHandle); body.append(sliderRow("A", alphaSlider));
        Element preview = el("DIV", "cp-current-preview"); previewFill = el("DIV", "cp-current-preview-fill"); previewValue = text("DIV", "cp-current-value", ""); preview.append(previewFill); preview.append(previewValue); body.append(preview);
        Element tabs = el("DIV", "cp-format-tabs"); for (String value : new String[]{"hex", "rgb", "hsl"}) { Element tab = button("cp-format-tab" + (value.equals(format) ? " active" : ""), value.toUpperCase(Locale.ROOT)); tab.setAttribute("data-format", value); tab.addEventListener("click", event -> { format = value; updateFormatTabs(); renderInputs(); update(); }); tabs.append(tab); } body.append(tabs);
        inputs = el("DIV", "cp-inputs"); body.append(inputs);
        Element actions = el("DIV", "cp-actions"); Element left = el("DIV", "cp-actions-left"); Element eye = button("cp-icon-btn", ""); eye.setInnerHTML("<svg viewBox=\"0 0 14 14\" fill=\"currentColor\"><path d=\"M12.5 1.5l-2-2a1 1 0 0 0-1.4 0L7.6 1l-.7-.7a1 1 0 0 0-1.4 0L4 1.8a1 1 0 0 0 0 1.4l.7.7-4 4a1 1 0 0 0 0 1.4l3 3a1 1 0 0 0 1.4 0l4-4 .7.7a1 1 0 0 0 1.4 0l1.5-1.5a1 1 0 0 0 0-1.4l-.7-.7 1.5-1.5a1 1 0 0 0 0-1.4z\"/></svg>"); eye.setAttribute("title", "Pick from screen"); eye.addEventListener("click", event -> ToastManager.show("EYEDROPPER NOT SUPPORTED")); Element copy = button("cp-icon-btn", ""); copy.setInnerHTML("<svg viewBox=\"0 0 14 14\" fill=\"currentColor\"><rect x=\"4\" y=\"4\" width=\"8\" height=\"8\" fill=\"none\" stroke=\"currentColor\" stroke-width=\"1.2\"/><path d=\"M2 10V3h6v1H3v6H2z\"/></svg>"); copy.setAttribute("title", "Copy color value"); copy.addEventListener("click", event -> Operation.setClipboardText(value())); left.append(eye); left.append(copy); actions.append(left); actions.append(el("DIV", "cp-actions-spacer")); Element right = el("DIV", "cp-actions-right"); Element cancel = button("cp-btn", "CANCEL"); cancel.addEventListener("click", event -> finish(Optional.empty())); Element apply = button("cp-btn primary", "APPLY"); apply.addEventListener("click", event -> finish(Optional.of(value()))); right.append(cancel); right.append(apply); actions.append(right); body.append(actions); root.append(body);
        document.body.append(root);
        bindDrag(svPanel); bindDrag(hueSlider); bindDrag(alphaSlider);
        root.addEventListener("mousedown", Event::stopPropagation);
        document.addEventListener("mousedown", event -> { if (event.target instanceof Element target && !root.contains(target)) finish(Optional.empty()); });
        document.addEventListener("mousemove", this::drag);
        document.addEventListener("mouseup", event -> dragTarget = null);
        position(anchor); renderInputs(); update(); dirty();
    }

    private void bindDrag(Element element) { element.addEventListener("mousedown", event -> { if (event instanceof MouseEvent mouse) { dragTarget = element; move(mouse); event.preventDefault(); } }); }
    private void drag(Event event) { if (dragTarget != null && event instanceof MouseEvent mouse) move(mouse); }
    private void move(MouseEvent event) {
        Element.DOMRect rect = dragTarget.getBoundingClientRect();
        double x = clamp((event.clientX - rect.left) / Math.max(1, rect.width), 0, 1);
        if (dragTarget == svPanel) color.setS(x * 100).setV((1 - clamp((event.clientY - rect.top) / Math.max(1, rect.height), 0, 1)) * 100);
        else if (dragTarget == hueSlider) color.setH(x * 360);
        else color.setA(Math.round(x * 100) / 100d);
        update();
    }

    private void renderInputs() {
        new ArrayList<>(inputs.children).forEach(Element::remove);
        if ("hex".equals(format)) { addInput("HEX", "cp-hex", hex().toUpperCase(Locale.ROOT), true, this::fromHex); addInput("A %", "cp-hexa", Integer.toString((int) Math.round(color.a * 100)), false, value -> color.setA(clamp(number(value, 0) / 100, 0, 1))); }
        else if ("rgb".equals(format)) { Rgb rgb = color.rgb(); addInput("R", "cp-r", Integer.toString(rgb.r), false, ignored -> fromRgb()); addInput("G", "cp-g", Integer.toString(rgb.g), false, ignored -> fromRgb()); addInput("B", "cp-b", Integer.toString(rgb.b), false, ignored -> fromRgb()); addInput("A", "cp-rgba", decimal(color.a), false, value -> color.setA(clamp(number(value, 0), 0, 1))); }
        else { Hsl hsl = color.hsl(); addInput("H", "cp-h", Integer.toString(hsl.h), false, ignored -> fromHsl()); addInput("S", "cp-s", Integer.toString(hsl.s), false, ignored -> fromHsl()); addInput("L", "cp-l", Integer.toString(hsl.l), false, ignored -> fromHsl()); addInput("A", "cp-hsla", decimal(color.a), false, value -> color.setA(clamp(number(value, 0), 0, 1))); }
    }

    private void addInput(String label, String id, String initial, boolean hex, java.util.function.Consumer<String> change) { Element group = el("DIV", "cp-input-group" + (hex ? " hex" : "")); group.append(text("DIV", "cp-input-label", label)); Element input = el("INPUT", "cp-input"); input.setAttribute("id", id); input.setAttribute("type", "text"); input.setValue(initial); input.addEventListener("input", event -> { change.accept(input.getValue()); update(); }); group.append(input); inputs.append(group); }
    private void updateFormatTabs() { for (Element tab : root.querySelectorAll(".cp-format-tab")) tab.setClassName("cp-format-tab" + (format.equals(tab.getAttribute("data-format")) ? " active" : "")); }
    private void fromHex(String raw) { ColorState parsed = ColorState.parse(raw); color.copy(parsed); }
    private void fromRgb() { color.setRgb((int) number(input("#cp-r"), 0), (int) number(input("#cp-g"), 0), (int) number(input("#cp-b"), 0)); }
    private void fromHsl() { color.setHsl(number(input("#cp-h"), 0), number(input("#cp-s"), 0), number(input("#cp-l"), 0)); }
    private String input(String selector) { Element input = root.querySelector(selector); return input == null ? "0" : input.getValue(); }

    private void update() { Rgb rgb = color.rgb(); String rgbText = "rgb(" + rgb.r + "," + rgb.g + "," + rgb.b + ")"; svPanel.setAttribute("style", "background:hsl(" + number(color.h) + ",100%,50%);"); svHandle.setAttribute("style", "left:" + color.s + "%;top:" + (100 - color.v) + "%;"); hueHandle.setAttribute("style", "left:" + color.h / 3.6 + "%;"); alphaHandle.setAttribute("style", "left:" + color.a * 100 + "%;"); alphaFill.setAttribute("style", "background:linear-gradient(to right,rgba(" + rgb.r + "," + rgb.g + "," + rgb.b + ",0)," + rgbText + ");"); previewFill.setAttribute("style", "background:" + rgba() + ";"); previewValue.setTextContent(value().toUpperCase(Locale.ROOT)); dirty(); }
    private void position(Element anchor) { double x = (document.getViewport().layoutWidth() - WIDTH) / 2, y = (document.getViewport().layoutHeight() - HEIGHT) / 2; if (anchor != null) { Element.DOMRect r = anchor.getBoundingClientRect(); x = r.right + 10; y = r.top - 40; if (x + WIDTH > document.getViewport().layoutWidth() - GAP) x = r.left - WIDTH - 10; } x = clamp(x, GAP, Math.max(GAP, document.getViewport().layoutWidth() - WIDTH - GAP)); y = clamp(y, GAP, Math.max(GAP, document.getViewport().layoutHeight() - HEIGHT - GAP)); root.setAttribute("style", "left:" + number(x) + "px;top:" + number(y) + "px;"); }
    private String hex() { Rgb c = color.rgb(); return String.format(Locale.ROOT, "#%02x%02x%02x", c.r, c.g, c.b); }
    private String rgba() { Rgb c = color.rgb(); return "rgba(" + c.r + "," + c.g + "," + c.b + "," + decimal(color.a) + ")"; }
    private String value() { Rgb c = color.rgb(); if ("hex".equals(format)) return color.a < .999 ? hex() + String.format(Locale.ROOT, "%02x", Math.round(color.a * 255)) : hex(); if ("rgb".equals(format)) return color.a < .999 ? rgba() : "rgb(" + c.r + ", " + c.g + ", " + c.b + ")"; Hsl h = color.hsl(); return color.a < .999 ? "hsla(" + h.h + ", " + h.s + "%, " + h.l + "%, " + decimal(color.a) + ")" : "hsl(" + h.h + ", " + h.s + "%, " + h.l + "%)"; }
    private void finish(Optional<String> value) {
        if (root != null) root.remove();
        dragTarget = null;
        if (!result.isDone()) result.complete(value);
        synchronized (ColorPicker.class) {
            if (active == this) active = null;
        }
        if (ownsDocument) {
            document.remove();
        } else {
            dirty();
        }
    }
    private Element el(String tag, String cls) { Element e = Element.init(document.createElement(tag)); e.setAttribute("class", cls); return e; } private Element text(String tag, String cls, String value) { Element e = el(tag, cls); e.setTextContent(value); return e; } private Element button(String cls, String value) { Element e = text("BUTTON", cls, value); e.setAttribute("type", "button"); return e; } private Element slider(String cls) { return el("DIV", cls); } private Element sliderRow(String label, Element slider) { Element row = el("DIV", "cp-slider-row"); row.append(text("DIV", "cp-slider-label", label)); row.append(slider); return row; } private void dirty() { if (document.body != null) document.markDirty(document.body, Drawer.RELAYOUT | Drawer.REPAINT | Drawer.REORDER | Drawer.HITTEST); }
    private static double clamp(double value, double min, double max) { return Math.max(min, Math.min(max, Double.isFinite(value) ? value : min)); } private static double number(String value, double fallback) { try { return Double.parseDouble(value == null ? "" : value.trim()); } catch (NumberFormatException ignored) { return fallback; } } private static String number(double value) { return Math.abs(value - Math.rint(value)) < .01 ? Long.toString(Math.round(value)) : String.format(Locale.ROOT, "%.2f", value); } private static String decimal(double value) { return String.format(Locale.ROOT, "%.2f", value); }

    private record Rgb(int r, int g, int b) {} private record Hsl(int h, int s, int l) {}
    private static final class ColorState { double h, s, v, a = 1; ColorState setH(double h) { this.h = clamp(h, 0, 360); return this; } ColorState setS(double s) { this.s = clamp(s, 0, 100); return this; } ColorState setV(double v) { this.v = clamp(v, 0, 100); return this; } ColorState setA(double a) { this.a = clamp(a, 0, 1); return this; } void copy(ColorState other) { h=other.h;s=other.s;v=other.v;a=other.a; }
        static ColorState parse(String value) { ColorState out = new ColorState(); String s = value == null ? "" : value.trim().toLowerCase(Locale.ROOT); try { if (s.startsWith("#")) { String hex=s.substring(1); if (hex.length()==3 || hex.length()==4) hex=""+hex.charAt(0)+hex.charAt(0)+hex.charAt(1)+hex.charAt(1)+hex.charAt(2)+hex.charAt(2)+(hex.length()==4?""+hex.charAt(3)+hex.charAt(3):""); if (hex.length()==6 || hex.length()==8) { out.setRgb(Integer.parseInt(hex.substring(0,2),16),Integer.parseInt(hex.substring(2,4),16),Integer.parseInt(hex.substring(4,6),16)); if(hex.length()==8)out.a=Integer.parseInt(hex.substring(6,8),16)/255d; return out; } } java.util.regex.Matcher m=java.util.regex.Pattern.compile("rgba?\\(([^)]+)\\)").matcher(s); if(m.matches()){String[] p=m.group(1).split(",");out.setRgb((int)number(p[0],0),(int)number(p[1],0),(int)number(p[2],0));if(p.length>3)out.a=clamp(number(p[3],1),0,1);return out;} m=java.util.regex.Pattern.compile("hsla?\\(([^)]+)\\)").matcher(s); if(m.matches()){String[] p=m.group(1).replace("%","").split(",");out.setHsl(number(p[0],0),number(p[1],0),number(p[2],0));if(p.length>3)out.a=clamp(number(p[3],1),0,1);return out;} } catch(RuntimeException ignored) {} return out; }
        ColorState setRgb(int r,int g,int b) { double rr=clamp(r,0,255)/255,gg=clamp(g,0,255)/255,bb=clamp(b,0,255)/255,max=Math.max(rr,Math.max(gg,bb)),min=Math.min(rr,Math.min(gg,bb)),d=max-min; v=max*100;s=max==0?0:d/max*100; if(d==0)h=0;else if(max==rr)h=60*((gg-bb)/d+(gg<bb?6:0));else if(max==gg)h=60*((bb-rr)/d+2);else h=60*((rr-gg)/d+4);return this; }
        Rgb rgb() { double hh=(h%360)/60,ss=s/100,vv=v/100,c=vv*ss,x=c*(1-Math.abs(hh%2-1)),m=vv-c; double r=0,g=0,b=0; if(hh<1){r=c;g=x;}else if(hh<2){r=x;g=c;}else if(hh<3){g=c;b=x;}else if(hh<4){g=x;b=c;}else if(hh<5){r=x;b=c;}else{r=c;b=x;} return new Rgb((int)Math.round((r+m)*255),(int)Math.round((g+m)*255),(int)Math.round((b+m)*255)); }
        Hsl hsl() { Rgb c=rgb(); double r=c.r/255d,g=c.g/255d,b=c.b/255d,max=Math.max(r,Math.max(g,b)),min=Math.min(r,Math.min(g,b)),l=(max+min)/2,d=max-min; double ss=d==0?0:d/(1-Math.abs(2*l-1)); double hh;if(d==0)hh=0;else if(max==r)hh=60*((g-b)/d+(g<b?6:0));else if(max==g)hh=60*((b-r)/d+2);else hh=60*((r-g)/d+4);return new Hsl((int)Math.round(hh),(int)Math.round(ss*100),(int)Math.round(l*100)); }
        ColorState setHsl(double h,double s,double l) { double hh=((h%360)+360)%360/360,ss=clamp(s,0,100)/100,ll=clamp(l,0,100)/100; double q=ll<.5?ll*(1+ss):ll+ss-ll*ss,p=2*ll-q; return setRgb((int)Math.round(hue(p,q,hh+1d/3)*255),(int)Math.round(hue(p,q,hh)*255),(int)Math.round(hue(p,q,hh-1d/3)*255)); } private static double hue(double p,double q,double t){if(t<0)t+=1;if(t>1)t-=1;if(t<1d/6)return p+(q-p)*6*t;if(t<.5)return q;if(t<2d/3)return p+(q-p)*(2d/3-t)*6;return p;}}
}
