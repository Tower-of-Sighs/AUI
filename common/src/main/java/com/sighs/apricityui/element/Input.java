package com.sighs.apricityui.element;

import com.mojang.blaze3d.vertex.PoseStack;
import com.sighs.apricityui.event.MouseEvent;
import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.event.Event;
import com.sighs.apricityui.registry.annotation.ElementRegister;
import com.sighs.apricityui.render.Base;
import com.sighs.apricityui.render.FontDrawer;
import com.sighs.apricityui.render.Graph;
import com.sighs.apricityui.render.Rect;
import com.sighs.apricityui.style.Background;
import com.sighs.apricityui.layout.Box;
import com.sighs.apricityui.parser.Color;
import com.sighs.apricityui.layout.Position;
import com.sighs.apricityui.style.Text;
import com.sighs.apricityui.ui.ColorPicker;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.util.tinyfd.TinyFileDialogs;

import java.util.Objects;
import java.util.Locale;
import java.util.ArrayList;
import com.sighs.apricityui.parser.HTML;

@ElementRegister(Input.TAG_NAME)
public class Input extends AbstractText {
    public static final String TAG_NAME = "INPUT";
    // Keep overlapping control surfaces ordered inside one BODY paint slot.
    // Screen documents ignore these offsets; world windows use them to avoid
    // depth ties between the native-looking control layers.
    private static final float CONTENT_DEPTH_OFFSET = 0.16f;
    private static final float DETAIL_DEPTH_OFFSET = 0.24f;
    private static final float MARK_DEPTH_OFFSET = 0.20f;
    private boolean rangeDragging;
    private boolean rangeValueChanged;

    private enum Mode {
        TEXT,
        NUMBER,
        COLOR,
        BUTTON,
        CHECKBOX,
        RADIO,
        FILE,
        RANGE,
        HIDDEN
    }

    public Input(Document document) {
        super(document, TAG_NAME);
        addInternalEventListener("mousedown", event -> {
            if (!(event instanceof MouseEvent mouse) || isDisabled()) return;
            if (getMode() == Mode.RANGE) {
                if (mouse.button != -1 && mouse.button != 0) return;
                rangeDragging = true;
                rangeValueChanged = setRangeValueFromPointer(mouse);
                if (rangeValueChanged) triggerInputEvent();
                return;
            }
            if (getMode() == Mode.NUMBER) {
                handleNumberSpinner(mouse);
            }
        });
        addInternalEventListener("mousemove", event -> {
            if (!(event instanceof MouseEvent mouse)
                    || getMode() != Mode.RANGE || !rangeDragging || isDisabled()) return;
            if (setRangeValueFromPointer(mouse)) {
                rangeValueChanged = true;
                triggerInputEvent();
            }
        });
        addInternalEventListener("mouseup", event -> {
            if (!(event instanceof MouseEvent)
                    || getMode() != Mode.RANGE || !rangeDragging) return;
            rangeDragging = false;
            if (rangeValueChanged) {
                rangeValueChanged = false;
                triggerChangeOnlyEvent();
            }
        });
        addInternalEventListener("blur", event -> {
            rangeDragging = false;
            rangeValueChanged = false;
        });
        addInternalEventListener("wheel", event -> {
            if (!(event instanceof MouseEvent mouse)) return;
            if (handleNumberWheel(mouse)) {
                event.preventDefault();
                mouse.consumeNative();
            }
        });
    }

    @Override
    public String getValue() {
        if (getMode() == Mode.RANGE && !hasAttribute("value") && (value == null || value.isEmpty())) {
            double min = parseNumberAttribute("min", 0d);
            double max = parseNumberAttribute("max", 100d);
            return Double.toString(min + (max - min) * 0.5d);
        }
        if ("color".equalsIgnoreCase(getType()) && !hasAttribute("value") && (value == null || value.isEmpty())) {
            return "#000000";
        }
        return super.getValue();
    }

    private Mode getMode() {
        String type = getType();
        if (type == null || type.isBlank()) return Mode.TEXT;
        return switch (type.toLowerCase(Locale.ROOT)) {
            case "button", "submit", "reset", "image" -> Mode.BUTTON;
            case "checkbox" -> Mode.CHECKBOX;
            case "radio" -> Mode.RADIO;
            case "file" -> Mode.FILE;
            case "range" -> Mode.RANGE;
            case "number" -> Mode.NUMBER;
            case "color" -> Mode.COLOR;
            case "hidden" -> Mode.HIDDEN;
            default -> Mode.TEXT;
        };
    }

    @Override
    public boolean canEditText() {
        return getMode() == Mode.TEXT || getMode() == Mode.NUMBER;
    }

    @Override
    public boolean canFocus() {
        return getMode() != Mode.HIDDEN && super.canFocus();
    }

    @Override
    public void handleClickDefault() {
        if (isDisabled()) return;
        Mode mode = getMode();
        if (mode == Mode.HIDDEN) return;
        if (mode == Mode.CHECKBOX) {
            setChecked(!isChecked());
            triggerChangeEvent();
        } else if (mode == Mode.RADIO) {
            if (!isChecked()) {
                setChecked(true);
                triggerChangeEvent();
            }
        } else if (mode == Mode.FILE) {
            openFileDialog();
        } else if (mode == Mode.COLOR) {
            openColorPicker();
        } else if (mode == Mode.RANGE) {
            // Range input is updated by its mousedown position; the click
            // activation itself does not apply a second synthetic value.
        } else if (mode == Mode.BUTTON && ("submit".equalsIgnoreCase(getAttribute("type"))
                || "image".equalsIgnoreCase(getAttribute("type")))) {
            submitEnclosingForm();
        } else if (mode == Mode.BUTTON && "reset".equalsIgnoreCase(getAttribute("type"))) {
            Element form = getFormOwner();
            if (form != null) form.reset();
        }
    }

    @Override
    public boolean canSelectText() {
        return (getMode() == Mode.TEXT || getMode() == Mode.NUMBER) && super.canSelectText();
    }

    private void triggerChangeEvent() {
        triggerInputEvent();

        triggerChangeOnlyEvent();
    }

    private void triggerInputEvent() {
        Event inputEvent = new Event(this, "input", true);
        Event.markTrustedFromCurrentDispatch(inputEvent);
        Event.tiggerEvent(inputEvent);
    }

    private void triggerChangeOnlyEvent() {
        Event changeEvent = new Event(this, "change", true);
        Event.markTrustedFromCurrentDispatch(changeEvent);
        Event.tiggerEvent(changeEvent);
    }

    private void openFileDialog() {
        String accept = getAttribute("accept");
        String pattern = resolveFilePattern(accept);
        String description = "*.html".equals(pattern) ? "HTML files" : "Files";
        try (MemoryStack stack = MemoryStack.stackPush()) {
            PointerBuffer filters = stack.mallocPointer(1);
            filters.put(stack.UTF8(pattern)).flip();
            String selected = TinyFileDialogs.tinyfd_openFileDialog("Choose file", "", filters, description, isMultiple());
            if (selected == null || selected.isBlank()) return;
            ArrayList<String> files = new ArrayList<>();
            for (String path : selected.split("[\\r\\n;]+")) {
                if (!path.isBlank() && acceptsFile(path.trim(), accept)) files.add(path.trim());
            }
            if (files.isEmpty()) return;
            if (!isMultiple() && files.size() > 1) files.subList(1, files.size()).clear();
            setFileList(files);
            triggerChangeEvent();
        } catch (Exception ignored) {
        }
    }

    private static String resolveFilePattern(String accept) {
        if (accept != null && accept.toLowerCase(Locale.ROOT).contains("html")) return "*.html";
        if (accept != null && accept.trim().startsWith(".")) {
            String extension = accept.trim().split("[, ]", 2)[0];
            return "*" + extension;
        }
        return "*.*";
    }

    private static boolean acceptsFile(String path, String accept) {
        if (accept == null || accept.isBlank()) return true;
        String lowerPath = path.toLowerCase(Locale.ROOT);
        for (String token : accept.split(",")) {
            String candidate = token.trim().toLowerCase(Locale.ROOT);
            int parameter = candidate.indexOf(';');
            if (parameter >= 0) candidate = candidate.substring(0, parameter).trim();
            if (candidate.isEmpty()) continue;
            if (candidate.startsWith(".")) {
                if (lowerPath.endsWith(candidate)) return true;
            } else if (candidate.endsWith("/*")) {
                String media = candidate.substring(0, candidate.length() - 2);
                String mime = mimeForPath(lowerPath);
                if (!mime.isEmpty() && mime.startsWith(media + "/")) return true;
            } else if (candidate.contains("/")) {
                if (candidate.equals(mimeForPath(lowerPath))) return true;
            }
        }
        return false;
    }

    private static String mimeForPath(String lowerPath) {
        if (lowerPath == null) return "";
        int dot = lowerPath.lastIndexOf('.');
        if (dot < 0 || dot + 1 >= lowerPath.length()) return "";
        return switch (lowerPath.substring(dot + 1)) {
            case "html", "htm" -> "text/html";
            case "txt", "text" -> "text/plain";
            case "csv" -> "text/csv";
            case "css" -> "text/css";
            case "js", "mjs" -> "text/javascript";
            case "json" -> "application/json";
            case "xml" -> "application/xml";
            case "pdf" -> "application/pdf";
            case "zip" -> "application/zip";
            case "doc" -> "application/msword";
            case "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
            case "xls" -> "application/vnd.ms-excel";
            case "xlsx" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
            case "png" -> "image/png";
            case "jpg", "jpeg" -> "image/jpeg";
            case "gif" -> "image/gif";
            case "bmp" -> "image/bmp";
            case "webp" -> "image/webp";
            case "svg" -> "image/svg+xml";
            case "mp3", "wav", "ogg" -> "audio/*";
            case "mp4", "webm", "mov" -> "video/*";
            default -> "";
        };
    }

    public boolean handleSpaceKey() {
        if (isDisabled()) return false;
        Mode mode = getMode();
        if (mode == Mode.HIDDEN) return false;
        if (mode == Mode.CHECKBOX) {
            setChecked(!isChecked());
            triggerChangeEvent();
            return true;
        }
        if (mode == Mode.RADIO) {
            if (!isChecked()) {
                setChecked(true);
                triggerChangeEvent();
            }
            return true;
        }
        if (mode == Mode.RANGE) {
            return true;
        }
        if (mode == Mode.COLOR) {
            handleClickDefault();
            return true;
        }
        return false;
    }

    @Override
    public void drawPhase(PoseStack poseStack, Base.RenderPhase phase) {
        Rect rectRenderer = Rect.of(this);
        if (phase == Base.RenderPhase.SHADOW) rectRenderer.drawShadow(poseStack);

        Mode mode = getMode();
        if (mode == Mode.HIDDEN) return;
        if (mode == Mode.CHECKBOX || mode == Mode.RADIO) {
            if (phase == Base.RenderPhase.BODY) {
                drawCheckableInput(poseStack, rectRenderer, mode);
            }
            return;
        }

        if (phase == Base.RenderPhase.BORDER) rectRenderer.drawBorder(poseStack);
        if (phase != Base.RenderPhase.BODY) return;

        rectRenderer.drawBody(poseStack);
        Base.offsetPaintDepth(poseStack, CONTENT_DEPTH_OFFSET);
        if (mode == Mode.RANGE) {
            drawRangeInput(poseStack, rectRenderer);
            return;
        }
        if (mode == Mode.COLOR) {
            drawColorInput(poseStack, rectRenderer);
            return;
        }
        if (mode == Mode.BUTTON) {
            drawButtonInput(poseStack, rectRenderer);
            return;
        }
        if (mode == Mode.NUMBER) {
            drawNumberInput(poseStack, rectRenderer);
            return;
        }
        drawTextInput(poseStack, rectRenderer);
    }

    private void drawButtonInput(PoseStack poseStack, Rect rectRenderer) {
        String label = value == null || value.isBlank() ? getAttribute("value") : value;
        if (label == null || label.isBlank()) {
            label = "button";
        }
        Text text = Text.of(this);
        text.content = label;
        text.color = new Color(Text.getFontColor(this));
        Position contentPos = rectRenderer.getContentPosition();
        FontDrawer.drawFont(poseStack, text, new Position(contentPos.x, singleLineDrawY(rectRenderer, text)));
    }

    private void drawCheckableInput(PoseStack poseStack, Rect rectRenderer, Mode mode) {
        Box box = rectRenderer.box;
        Background background = rectRenderer.background;

        float outerX = (float) (rectRenderer.position.x + box.getMarginLeft());
        float outerY = (float) (rectRenderer.position.y + box.getMarginTop());
        float outerW = (float) box.elementSize().width();
        float outerH = (float) box.elementSize().height();
        float controlSize = Math.max(0f, Math.min(outerW, outerH));
        float x = outerX + (outerW - controlSize) * 0.5f;
        float y = outerY + (outerH - controlSize) * 0.5f;

        float borderWidth = Math.max(1f, Math.min(2f, controlSize / 8f));
        float radius = mode == Mode.RADIO ? controlSize * 0.5f : Math.min(controlSize * 0.25f, 4f);

        boolean checked = isChecked();
        int accentColor = resolveAccentColor();
        int backgroundColor = checked ? accentColor : new Color(isDisabled() ? "#F2F2F2" : "#FFFFFF").getValue();
        int borderColor = checked ? accentColor : new Color(isDisabled() ? "#B7B7B7" : "#767676").getValue();

        Graph.beginBatch();
        Graph.drawUnifiedRoundedRect(
                poseStack.last().pose(),
                x,
                y,
                controlSize,
                controlSize,
                uniformRadii(radius),
                backgroundColor
        );
        Base.offsetPaintDepth(poseStack, DETAIL_DEPTH_OFFSET);
        Graph.drawComplexRoundedBorder(
                poseStack.last().pose(),
                x,
                y,
                controlSize,
                controlSize,
                uniformRadii(radius),
                new float[]{borderWidth, borderWidth, borderWidth, borderWidth},
                new int[]{borderColor, borderColor, borderColor, borderColor}
        );

        if (checked) {
            Base.offsetPaintDepth(poseStack, MARK_DEPTH_OFFSET);
            int indicatorColor = new Color(isDisabled() ? "#F7F7F7" : "#FFFFFF").getValue();
            if (mode == Mode.RADIO) {
                float dotSize = Math.max(4f, controlSize * 0.42f);
                float dotX = x + (controlSize - dotSize) * 0.5f;
                float dotY = y + (controlSize - dotSize) * 0.5f;
                Graph.drawUnifiedRoundedRect(
                        poseStack.last().pose(),
                        dotX,
                        dotY,
                        dotSize,
                        dotSize,
                        uniformRadii(dotSize * 0.5f),
                        indicatorColor
                );
            } else {
                drawCheckboxMark(poseStack, x, y, controlSize, indicatorColor);
            }
        }
    }

    private void drawCheckboxMark(PoseStack poseStack, float x, float y, float size, int color) {
        float pixel = Math.max(1.5f, size * 0.11f);
        for (int i = 0; i <= 4; i++) {
            float px = x + size * 0.20f + i * size * 0.055f;
            float py = y + size * 0.48f + i * size * 0.055f;
            Graph.drawFillRect(poseStack.last().pose(), px, py, px + pixel, py + pixel, color);
        }
        for (int i = 0; i <= 8; i++) {
            float px = x + size * 0.42f + i * size * 0.045f;
            float py = y + size * 0.70f - i * size * 0.055f;
            Graph.drawFillRect(poseStack.last().pose(), px, py, px + pixel, py + pixel, color);
        }
    }

    private int resolveCheckableBorderColor(Box box) {
        Box.SideBorder top = box.getBorderTopSide();
        if (top.size() > 0) {
            return top.color().getValue();
        }
        return new Color(Text.getFontColor(this)).getValue();
    }

    private float[] uniformRadii(float radius) {
        return new float[]{radius, radius, radius, radius};
    }

    private void drawTextInput(PoseStack poseStack, Rect rectRenderer) {
        String textToShow = getRenderText();
        if ("password".equalsIgnoreCase(getAttribute("type")) && !textToShow.isEmpty()) {
            textToShow = "*".repeat(textToShow.length());
        }
        boolean isPlaceholder = textToShow.isEmpty() && !placeholder.isEmpty();
        String renderContent = isPlaceholder ? placeholder : textToShow;

        if (renderContent.isEmpty() && !Element.isElementFocusing(this)) return;

        Text text = Text.of(this);
        text.content = renderContent;
        text.color = isPlaceholder ? new Color("#888888") : new Color(Text.getFontColor(this));

        Position contentPos = rectRenderer.getContentPosition();
        float drawX = (float) (contentPos.x + resolveTextAlignX(renderContent) - scrollLeft);
        float drawY = (float) singleLineDrawY(rectRenderer, text);

        if (!isPlaceholder) {
            Base.offsetPaintDepth(poseStack, 0.04f);
            drawSingleLineSelection(poseStack, rectRenderer, textToShow, drawY, text.lineHeight);
            Base.offsetPaintDepth(poseStack, 0.10f);
        }
        if (!isPlaceholder && hasSelection() && canSelectText()) {
            int min = Math.max(0, Math.min(selMin(), textToShow.length()));
            int max = Math.max(0, Math.min(selMax(), textToShow.length()));
            String before = textToShow.substring(0, min);
            String selected = textToShow.substring(min, max);
            String after = textToShow.substring(max);

            float segmentX = drawX;
            if (!before.isEmpty()) {
                text.content = before;
                text.color = new Color(Text.getFontColor(this));
                FontDrawer.drawFont(poseStack, text, new Position(segmentX, drawY));
                segmentX += (float) com.sighs.apricityui.layout.Size.measureText(this, before);
            }
            if (!selected.isEmpty()) {
                text.content = selected;
                text.color = new Color("#FFFFFF");
                FontDrawer.drawFont(poseStack, text, new Position(segmentX, drawY));
                segmentX += (float) com.sighs.apricityui.layout.Size.measureText(this, selected);
            }
            if (!after.isEmpty()) {
                text.content = after;
                text.color = new Color(Text.getFontColor(this));
                FontDrawer.drawFont(poseStack, text, new Position(segmentX, drawY));
            }
        } else {
            FontDrawer.drawFont(poseStack, text, new Position(drawX, drawY));
        }
        Base.offsetPaintDepth(poseStack, DETAIL_DEPTH_OFFSET);
        drawSingleLineCursor(poseStack, textToShow, drawX, drawY, (float) text.lineHeight);
    }

    private void drawNumberInput(PoseStack poseStack, Rect rectRenderer) {
        drawTextInput(poseStack, rectRenderer);
        Base.offsetPaintDepth(poseStack, DETAIL_DEPTH_OFFSET);

        Box box = rectRenderer.box;
        Position contentPos = rectRenderer.getContentPosition();
        double width = Math.max(0d, box.innerSize().width());
        double height = Math.max(0d, box.innerSize().height());
        double spinnerWidth = numberSpinnerWidth(width);
        if (width <= 0 || height <= 0 || spinnerWidth <= 0) return;

        float left = (float) (contentPos.x + width - spinnerWidth);
        float top = (float) contentPos.y;
        float right = (float) (contentPos.x + width);
        float bottom = (float) (contentPos.y + height);
        boolean spinnerDisabled = isDisabled() || hasAttribute("readonly");
        int background = new Color(spinnerDisabled ? "#D5D5D5" : (isHover ? "#E8E8E8" : "#F4F4F4")).getValue();
        int separator = new Color(spinnerDisabled ? "#B7B7B7" : "#C8C8C8").getValue();
        int arrow = new Color(spinnerDisabled ? "#929292" : "#4F4F4F").getValue();

        Graph.beginBatch();
        Graph.drawFillRect(poseStack.last().pose(), left, top, right, bottom, background);
        Graph.drawFillRect(poseStack.last().pose(), left, (float) (top + height / 2d - 0.5d), right,
                (float) (top + height / 2d + 0.5d), separator);
        drawSpinnerTriangle(poseStack, left + (float) spinnerWidth / 2f, top + (float) height / 4f, true, arrow);
        drawSpinnerTriangle(poseStack, left + (float) spinnerWidth / 2f, top + (float) (height * 3d / 4d), false, arrow);
    }

    private void drawColorInput(PoseStack poseStack, Rect rectRenderer) {
        Box box = rectRenderer.box;
        Position contentPos = rectRenderer.getContentPosition();
        double width = Math.max(0d, box.innerSize().width());
        double height = Math.max(0d, box.innerSize().height());
        if (width <= 0 || height <= 0) return;

        float left = (float) contentPos.x;
        float top = (float) contentPos.y;
        float right = (float) (contentPos.x + width);
        float bottom = (float) (contentPos.y + height);
        int color = new Color(getValue()).getValue();
        int alpha = (color >>> 24) & 0xFF;

        Graph.beginBatch();
        if (alpha < 255) {
            int light = new Color("#E6E6E6").getValue();
            int dark = new Color("#BDBDBD").getValue();
            float tile = Math.max(3f, Math.min(6f, (float) Math.min(width, height) / 3f));
            for (float y = top; y < bottom; y += tile) {
                for (float x = left; x < right; x += tile) {
                    boolean alternate = ((int) Math.floor((x - left) / tile) + (int) Math.floor((y - top) / tile)) % 2 == 0;
                    Graph.drawFillRect(poseStack.last().pose(), x, y, Math.min(right, x + tile),
                            Math.min(bottom, y + tile), alternate ? light : dark);
                }
            }
        }
        Base.offsetPaintDepth(poseStack, DETAIL_DEPTH_OFFSET);
        Graph.drawFillRect(poseStack.last().pose(), left, top, right, bottom, color);
    }

    private static void drawSpinnerTriangle(PoseStack poseStack, float centerX, float centerY,
                                            boolean up, int color) {
        for (int row = 0; row < 5; row++) {
            int distance = up ? row : 4 - row;
            float halfWidth = 1f + distance * 0.85f;
            float y = centerY - 2f + row;
            Graph.drawFillRect(poseStack.last().pose(), centerX - halfWidth, y,
                    centerX + halfWidth + 1f, y + 1f, color);
        }
    }

    private static double numberSpinnerWidth(double width) {
        return Math.max(0d, Math.min(width, Math.max(14d, Math.min(18d, width * 0.25d))));
    }

    private int numberSpinnerDirection(MouseEvent event) {
        if (event == null || getMode() != Mode.NUMBER || isDisabled() || hasAttribute("readonly")) return 0;
        Element.DOMRect rect = getBoundingClientRect();
        if (rect == null || rect.width <= 0 || rect.height <= 0) return 0;
        Box box = Box.of(this);
        double contentLeft = rect.x + box.getBorderLeft() + box.getPaddingLeft();
        double contentTop = rect.y + box.getBorderTop() + box.getPaddingTop();
        double contentWidth = Math.max(0d, rect.width - box.getBorderHorizontal()
                - box.getPaddingHorizontal() - getVerticalScrollbarGutter());
        double contentHeight = Math.max(0d, rect.height - box.getBorderVertical()
                - box.getPaddingVertical() - getHorizontalScrollbarGutter());
        double spinnerWidth = numberSpinnerWidth(contentWidth);
        double spinnerRight = contentLeft + contentWidth;
        double spinnerBottom = contentTop + contentHeight;
        if (contentWidth <= 0d || contentHeight <= 0d
                || event.clientX < spinnerRight - spinnerWidth || event.clientX > spinnerRight
                || event.clientY < contentTop || event.clientY > spinnerBottom) return 0;
        return event.clientY < contentTop + contentHeight / 2d ? 1 : -1;
    }

    /** Handles a click in the number input's up/down spinner area. */
    public boolean handleNumberSpinner(MouseEvent event) {
        int direction = numberSpinnerDirection(event);
        return direction != 0 && adjustNumber(direction);
    }

    /** Handles a wheel event while this number control is the hit target. */
    public boolean handleNumberWheel(MouseEvent event) {
        if (event == null || getMode() != Mode.NUMBER || isDisabled() || hasAttribute("readonly")) return false;
        double delta = event.deltaY;
        if (!Double.isFinite(delta) || Math.abs(delta) < 0.000001d) delta = event.scrollDelta;
        if (!Double.isFinite(delta) || Math.abs(delta) < 0.000001d) return false;
        return adjustNumber(delta < 0d ? 1 : -1);
    }

    private boolean adjustNumber(int direction) {
        if (getMode() != Mode.NUMBER || direction == 0 || isDisabled() || hasAttribute("readonly")) return false;
        String before = getValue();
        if (direction > 0) stepUp(1);
        else stepDown(1);

        double next = getValueAsNumber();
        if (Double.isFinite(next)) {
            double min = parseNumberAttribute("min", Double.NEGATIVE_INFINITY);
            double max = parseNumberAttribute("max", Double.POSITIVE_INFINITY);
            double clamped = Math.max(min, Math.min(max, next));
            if (Double.compare(next, clamped) != 0) setValueAsNumber(clamped);
        }

        String after = getValue();
        if (Objects.equals(before, after)) return false;
        triggerChangeEvent();
        return true;
    }

    private void openColorPicker() {
        if (document == null) return;
        // Keep the picker out of the page that is currently dispatching the
        // input click.  Mounting its full DOM tree there forces a synchronous
        // relayout/repaint during activation and can re-enter hit testing.
        ColorPicker.pick(getValue()).thenAccept(selected -> selected.ifPresent(next ->
                Document.runWithContext(document, () -> Event.runTrustedAction(() -> {
                    if (Objects.equals(getValue(), next)) return;
                    setValue(next);
                    triggerChangeEvent();
                }))));
    }

    private void drawRangeInput(PoseStack poseStack, Rect rectRenderer) {
        double width = rectRenderer.box.innerSize().width();
        double centerY = rectRenderer.getContentPosition().y + rectRenderer.box.innerSize().height() / 2.0d;
        double left = rectRenderer.getContentPosition().x;
        double right = left + Math.max(0d, width);
        double fraction = rangeFraction();
        int track = new Color(isDisabled() ? "#A5A5A5" : "#777777").getValue();
        int accent = resolveAccentColor();
        Graph.drawFillRect(poseStack.last().pose(), (float) left, (float) centerY - 1f, (float) right, (float) centerY + 1f, track);
        Graph.drawFillRect(poseStack.last().pose(), (float) left, (float) centerY - 1f,
                (float) (left + (right - left) * fraction), (float) centerY + 1f, accent);
        Base.offsetPaintDepth(poseStack, DETAIL_DEPTH_OFFSET);
        float knobX = (float) (left + (right - left) * fraction - 5f);
        Graph.drawUnifiedRoundedRect(poseStack.last().pose(), knobX, (float) centerY - 5f, 10f, 10f,
                uniformRadii(5f), isDisabled() ? new Color("#B7B7B7").getValue() : accent);
    }

    public boolean handleRangeKey(int key) {
        if (getMode() == Mode.NUMBER) {
            if (isDisabled() || hasAttribute("readonly")) return false;
            boolean down = key == org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT || key == org.lwjgl.glfw.GLFW.GLFW_KEY_DOWN;
            boolean up = key == org.lwjgl.glfw.GLFW.GLFW_KEY_RIGHT || key == org.lwjgl.glfw.GLFW.GLFW_KEY_UP;
            if (!down && !up) return false;
            adjustNumber(up ? 1 : -1);
            return true;
        }
        if (getMode() != Mode.RANGE || isDisabled()) return false;
        String type = key == org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT || key == org.lwjgl.glfw.GLFW.GLFW_KEY_DOWN ? "down" :
                (key == org.lwjgl.glfw.GLFW.GLFW_KEY_RIGHT || key == org.lwjgl.glfw.GLFW.GLFW_KEY_UP ? "up" : "");
        if (type.isEmpty()) return false;
        double current = getValueAsNumber();
        if (!Double.isFinite(current)) current = rangeMin();
        double step = rangeStep();
        setValueAsNumber(current + ("up".equals(type) ? step : -step));
        triggerChangeEvent();
        return true;
    }

    public void setValueFromPointer(double fraction) {
        setRangeValueFromFraction(fraction);
    }

    private boolean setRangeValueFromPointer(MouseEvent event) {
        if (event == null) return false;
        Element.DOMRect rect = getBoundingClientRect();
        double width = rect != null && Double.isFinite(rect.width) && rect.width > 0.0d
                ? rect.width : Math.max(1d, Box.of(this).innerSize().width());
        double offset = rect == null ? event.offsetX : event.clientX - rect.x;
        if (!Double.isFinite(offset)) {
            offset = event.offsetX;
        }
        return setRangeValueFromFraction(offset / width);
    }

    private boolean setRangeValueFromFraction(double fraction) {
        double min = rangeMin();
        double max = rangeMax();
        double value = min + Math.max(0d, Math.min(1d, fraction)) * (max - min);
        double step = rangeStep();
        if (step > 0) value = min + Math.round((value - min) / step) * step;
        double next = Math.max(min, Math.min(max, value));
        double previous = getValueAsNumber();
        setValueAsNumber(next);
        double current = getValueAsNumber();
        if (Double.isFinite(previous) && Double.isFinite(current)) {
            return Double.compare(previous, current) != 0;
        }
        return !Objects.equals(Double.toString(previous), Double.toString(current));
    }

    private double rangeMin() { return parseNumberAttribute("min", 0d); }
    private double rangeMax() { return parseNumberAttribute("max", 100d); }
    private double rangeStep() { return parseNumberAttribute("step", 1d); }
    private double parseNumberAttribute(String name, double fallback) {
        try {
            double value = Double.parseDouble(getAttribute(name));
            return Double.isFinite(value) ? value : fallback;
        } catch (Exception ignored) { return fallback; }
    }
    private double rangeFraction() {
        double min = rangeMin();
        double max = rangeMax();
        double value = getValueAsNumber();
        if (!Double.isFinite(value) || max <= min) return 0.5d;
        return Math.max(0d, Math.min(1d, (value - min) / (max - min)));
    }

    private int resolveAccentColor() {
        String accent = getComputedStyle().accentColor;
        if (accent == null || accent.isBlank() || "unset".equalsIgnoreCase(accent)
                || "auto".equalsIgnoreCase(accent)) {
            accent = "#0075FF";
        }
        return new Color(accent).getValue();
    }

    private double singleLineDrawY(Rect rectRenderer, Text text) {
        if (rectRenderer == null || text == null) return 0.0d;
        Position contentPos = rectRenderer.getContentPosition();
        double contentHeight = Math.max(0.0d, rectRenderer.box.innerSize().height());
        return contentPos.y + (contentHeight - text.lineHeight) / 2.0d;
    }
}
