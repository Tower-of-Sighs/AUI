package com.sighs.apricityui.instance.element;

import com.mojang.blaze3d.matrix.MatrixStack;
import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.init.Drawer;
import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.render.Base;
import com.sighs.apricityui.render.Rect;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.item.ItemStack;
import com.sighs.apricityui.util.StringUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * Minecraft 相关元素基类，统一处理批量属性更新与基础渲染辅助能力。
 */
public abstract class MinecraftElement extends Element {
    private static final Map<String, Object> GLOBAL_RUNTIME_CACHES = new ConcurrentHashMap<>();
    private final HashMap<String, Object> runtimeCaches = new HashMap<>();
    private int attributeBatchDepth = 0;
    private boolean pendingCssUpdate = false;

    protected MinecraftElement(Document document, String tagName) {
        super(document, tagName);
    }

    protected static Object getGlobalCache(String key) {
        if (StringUtils.isNullOrEmptyEx(key)) return null;
        return GLOBAL_RUNTIME_CACHES.get(key);
    }

    protected static Object computeGlobalCacheIfAbsent(String key, Supplier<Object> factory) {
        if (StringUtils.isNullOrEmptyEx(key) || factory == null) return null;
        return GLOBAL_RUNTIME_CACHES.computeIfAbsent(key, ignored -> factory.get());
    }

    protected static void clearGlobalCache(String key) {
        if (StringUtils.isNullOrEmptyEx(key)) return;
        GLOBAL_RUNTIME_CACHES.remove(key);
    }

    protected static void clearAllGlobalCaches() {
        GLOBAL_RUNTIME_CACHES.clear();
    }

    public MinecraftBindingMode getBindingMode() {
        return MinecraftBindingMode.NONE;
    }

    public final boolean isBoundMode() {
        return getBindingMode() == MinecraftBindingMode.BOUND;
    }

    public final boolean isVirtualMode() {
        return getBindingMode() == MinecraftBindingMode.VIRTUAL;
    }

    public final <T extends Element> T findAncestor(Class<T> type) {
        if (type == null) return null;

        Element current = parentElement;
        while (current != null) {
            if (type.isInstance(current)) {
                return type.cast(current);
            }
            current = current.parentElement;
        }
        return null;
    }

    public final boolean hasAncestor(Class<? extends Element> type) {
        return findAncestor(type) != null;
    }

    public final Object getRuntimeCache(String key) {
        if (StringUtils.isNullOrEmptyEx(key)) return null;
        return runtimeCaches.get(key);
    }

    public final void putRuntimeCache(String key, Object value) {
        if (StringUtils.isNullOrEmptyEx(key)) return;
        if (value == null) {
            runtimeCaches.remove(key);
            return;
        }
        runtimeCaches.put(key, value);
    }

    public final Object computeRuntimeCacheIfAbsent(String key, Supplier<Object> factory) {
        if (StringUtils.isNullOrEmptyEx(key) || factory == null) return null;
        return runtimeCaches.computeIfAbsent(key, ignored -> factory.get());
    }

    public final void removeRuntimeCache(String key) {
        if (StringUtils.isNullOrEmptyEx(key)) return;
        runtimeCaches.remove(key);
    }

    public final void clearRuntimeCaches() {
        runtimeCaches.clear();
    }

    public final void beginAttributeBatch() {
        attributeBatchDepth++;
    }

    public final void endAttributeBatch(boolean updateCssOnce) {
        if (attributeBatchDepth <= 0) return;
        attributeBatchDepth--;
        if (updateCssOnce) pendingCssUpdate = true;
        if (attributeBatchDepth == 0 && pendingCssUpdate) {
            pendingCssUpdate = false;
            updateCSS();
        }
    }

    public final void setAttributesBatch(Map<String, String> attributes, boolean updateCssOnce) {
        if (attributes == null || attributes.isEmpty()) return;
        beginAttributeBatch();
        try {
            for (Map.Entry<String, String> entry : attributes.entrySet()) {
                putAttributeSilently(entry.getKey(), entry.getValue());
            }
        } finally {
            endAttributeBatch(updateCssOnce);
        }
    }

    protected final void putAttributeSilently(String name, String value) {
        if (StringUtils.isNullOrEmptyEx(name)) return;

        HashMap<String, String> attributes = getAttributes();
        String safeValue = value == null ? "" : value;
        attributes.put(name, safeValue);

        if ("style".equals(name)) {
            updateInlineStyle();
        }
        if ("value".equals(name)) {
            this.value = safeValue;
        }
        if ("id".equals(name)) {
            this.id = safeValue;
            if (document != null) document.recordID(this);
        }
        if ("class".equals(name)) {
            this.classNames = new ArrayList<>();
            this.classNames.addAll(Arrays.asList(safeValue.split(" ")));
        }

        if (attributeBatchDepth == 0) {
            updateCSS();
        }
    }

    public final void requestRepaint() {
        if (document != null) document.markDirty(this, Drawer.REPAINT);
    }

    public final void requestRelayout() {
        getRenderer().position.clear();
        getRenderer().size.clear();
        getRenderer().box.clear();
        if (document != null) document.markDirty(this, Drawer.RELAYOUT);
    }

    public ItemStack getTooltipStack() {
        return ItemStack.EMPTY;
    }

    public void renderTooltip(MatrixStack stack, int mouseX, int mouseY) {
        ItemStack itemStack = getTooltipStack();
        if (itemStack.isEmpty()) return;

        renderItemTooltip(stack, itemStack.getItem().getFontRenderer(itemStack), itemStack, mouseX, mouseY);
    }

    public static void renderItemTooltip(MatrixStack stack, FontRenderer font, ItemStack itemStack, int mouseX, int mouseY) {
        Screen screen = Minecraft.getInstance().screen;
        if (screen == null) return;
        net.minecraftforge.fml.client.gui.GuiUtils.preItemToolTip(itemStack);
        screen.renderWrappedToolTip(stack, screen.getTooltipFromItem(itemStack), mouseX, mouseY, (font == null ? Minecraft.getInstance().font : font));
        net.minecraftforge.fml.client.gui.GuiUtils.postItemToolTip();
    }

    @Override
    public void drawPhase(MatrixStack stack, Base.RenderPhase phase) {
        Rect rectRenderer = Rect.of(this);
        switch (phase) {
            case SHADOW:
                rectRenderer.drawShadow(stack);
                break;
            case BODY:
                rectRenderer.drawBody(stack);
                break;
            case BORDER:
                rectRenderer.drawBorder(stack);
                break;
        }
    }

    public enum MinecraftBindingMode {
        NONE,
        BOUND,
        VIRTUAL
    }
}
