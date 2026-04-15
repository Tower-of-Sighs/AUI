package com.sighs.apricityui.instance.element;

import com.mojang.blaze3d.vertex.PoseStack;
import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.init.Drawer;
import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.render.Base;
import com.sighs.apricityui.render.Rect;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.item.ItemStack;

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
        if (key == null || key.isBlank()) return null;
        return GLOBAL_RUNTIME_CACHES.get(key);
    }

    protected static Object computeGlobalCacheIfAbsent(String key, Supplier<Object> factory) {
        if (key == null || key.isBlank() || factory == null) return null;
        return GLOBAL_RUNTIME_CACHES.computeIfAbsent(key, ignored -> factory.get());
    }

    protected static void clearGlobalCache(String key) {
        if (key == null || key.isBlank()) return;
        GLOBAL_RUNTIME_CACHES.remove(key);
    }

    protected static void clearAllGlobalCaches() {
        GLOBAL_RUNTIME_CACHES.clear();
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
        if (key == null || key.isBlank()) return null;
        return runtimeCaches.get(key);
    }

    public final void putRuntimeCache(String key, Object value) {
        if (key == null || key.isBlank()) return;
        if (value == null) {
            runtimeCaches.remove(key);
            return;
        }
        runtimeCaches.put(key, value);
    }

    public final Object computeRuntimeCacheIfAbsent(String key, Supplier<Object> factory) {
        if (key == null || key.isBlank() || factory == null) return null;
        return runtimeCaches.computeIfAbsent(key, ignored -> factory.get());
    }

    public final void removeRuntimeCache(String key) {
        if (key == null || key.isBlank()) return;
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
            invalidateStyle();
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
        if (name == null || name.isBlank()) return;

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
            this.classNames = parseClassNames(safeValue);
        }

        if (attributeBatchDepth == 0) {
            invalidateStyle();
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

    public void renderTooltip(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        ItemStack stack = getTooltipStack();
        if (stack.isEmpty()) return;
        guiGraphics.renderTooltip(Minecraft.getInstance().font, stack, mouseX, mouseY);
    }

    @Override
    public void drawPhase(PoseStack poseStack, Base.RenderPhase phase) {
        Rect rectRenderer = Rect.of(this);
        switch (phase) {
            case SHADOW -> rectRenderer.drawShadow(poseStack);
            case BODY -> rectRenderer.drawBody(poseStack);
            case BORDER -> rectRenderer.drawBorder(poseStack);
        }
    }
}
