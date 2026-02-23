package com.sighs.apricityui.instance;

import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.instance.container.runtime.ContainerEngine;
import com.sighs.apricityui.instance.element.MinecraftElement;
import com.sighs.apricityui.instance.element.Recipe;
import com.sighs.apricityui.instance.element.Slot;
import com.sighs.apricityui.mixin.accessor.AbstractContainerScreenAccessor;
import com.sighs.apricityui.render.Base;
import com.sighs.apricityui.render.Rect;
import com.sighs.apricityui.style.Position;
import com.sighs.apricityui.style.Size;
import lombok.Getter;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class ApricityContainerScreen extends AbstractContainerScreen<ApricityContainerMenu> {
    private static final String DEVTOOLS_PATH = "devtools/index.html";
    private static final int QUICK_CRAFT_GHOST_COLOR = -2130706433;
    private static final float ICON_SCALE_EPSILON = 0.0001F;
    @Getter
    private final Document linkedDocument;
    private final ContainerEngine.Controller controller;
    private FrameSlotContext currentFrameSlotContext = null;

    private record FrameSlotContext(
            List<ContainerEngine.Controller.SlotVisualState> visualStates,
            int hoverSlotIndex,
            int focusedSlotIndex
    ) {
    }

    public ApricityContainerScreen(ApricityContainerMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        linkedDocument = Document.create(menu.getTemplatePath());
        controller = new ContainerEngine.Controller(this, new ContainerEngine.MenuAdapter(menu));
    }

    public ApricityContainerScreen(String path) {
        this(ApricityContainerMenu.createClientOnly(Minecraft.getInstance().player.getInventory(), path),
                Minecraft.getInstance().player.getInventory(),
                Component.literal("ApricityScreen"));
    }

    public int getGuiLeft() {
        return super.getGuiLeft();
    }

    public int getGuiTop() {
        return super.getGuiTop();
    }

    public int findSlotIndexAt(double mouseX, double mouseY) {
        return findSlotIndexAt(mouseX, mouseY, controller.getSlotVisualStates());
    }

    public boolean isSlotPointerInteractable(net.minecraft.world.inventory.Slot slot) {
        if (slot == null || !slot.isActive()) return false;
        int slotIndex = menu.slots.indexOf(slot);
        if (slotIndex < 0) return true;
        ContainerEngine.Controller.SlotVisualState visualState = resolveSlotVisualState(slotIndex, controller.getSlotVisualStates());
        if (visualState == null) return true;
        return !visualState.hidden()
                && !visualState.disabled()
                && visualState.acceptPointer();
    }

    private int findSlotIndexAt(double mouseX, double mouseY, List<ContainerEngine.Controller.SlotVisualState> visualStates) {
        if (!visualStates.isEmpty()) {
            for (ContainerEngine.Controller.SlotVisualState visualState : visualStates) {
                if (visualState.hidden()) continue;
                if (!visualState.acceptPointer()) continue;
                if (visualState.disabled()) continue;
                int slotIndex = visualState.globalSlotIndex();
                if (slotIndex < 0 || slotIndex >= menu.slots.size()) continue;

                net.minecraft.world.inventory.Slot slot = menu.slots.get(slotIndex);
                int slotSize = Math.max(1, visualState.slotSize());
                if (slot.isActive() && isHovering(slot.x, slot.y, slotSize, slotSize, mouseX, mouseY)) {
                    return slotIndex;
                }
            }
            return -1;
        }

        for (int index = 0; index < menu.slots.size(); index++) {
            net.minecraft.world.inventory.Slot slot = menu.slots.get(index);
            if (slot.isActive() && isHovering(slot.x, slot.y, 16, 16, mouseX, mouseY)) {
                return index;
            }
        }
        return -1;
    }

    private ContainerEngine.Controller.SlotVisualState resolveSlotVisualState(
            int slotIndex,
            List<ContainerEngine.Controller.SlotVisualState> visualStates
    ) {
        if (visualStates == null || visualStates.isEmpty()) return null;
        for (ContainerEngine.Controller.SlotVisualState visualState : visualStates) {
            if (visualState.globalSlotIndex() == slotIndex) return visualState;
        }
        return null;
    }

    @Override
    protected void init() {
        imageWidth = width;
        imageHeight = height;
        super.init();
        if (linkedDocument != null) {
            ensureControllerBound();
            controller.markLayoutDirty(ContainerEngine.Controller.DirtyReason.SCREEN_INIT);
            controller.syncMenuSlotPositionsIfDirty();
        }
    }

    @Override
    protected void renderBg(GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {
        if (linkedDocument != null) {
            ensureRecipePreviewSlots();
            Base.drawDocument(guiGraphics.pose(), linkedDocument);
            FrameSlotContext frameContext = currentFrameSlotContext;
            List<ContainerEngine.Controller.SlotVisualState> visualStates = frameContext == null
                    ? controller.getSlotVisualStates()
                    : frameContext.visualStates();
            drawBoundSlotRuntimeProxyBackgrounds(guiGraphics, visualStates);
            drawBoundSlotItems(guiGraphics, visualStates);
            drawVirtualSlotItems(guiGraphics);
        }
    }

    @Override
    protected void renderLabels(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        // 标题改为容器内节点渲染，不再固定绘制到屏幕左上角。
    }

    @Override
    public void render(@NotNull GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        ensureControllerBound();
        controller.syncMenuSlotPositionsIfDirty();
        FrameSlotContext frameContext = buildFrameSlotContext(mouseX, mouseY);
        currentFrameSlotContext = frameContext;
        syncBoundSlotRuntimeClasses(frameContext);
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        drawSlotHoverTooltipByElement(guiGraphics, mouseX, mouseY, frameContext);
        currentFrameSlotContext = null;
        drawDevToolsOverlay(guiGraphics);
    }

    private void ensureControllerBound() {
        if (linkedDocument == null) return;
        boolean rebound = controller.ensureBound(linkedDocument);
        if (rebound) {
            controller.markLayoutDirty(ContainerEngine.Controller.DirtyReason.BIND);
        }
    }

    private FrameSlotContext buildFrameSlotContext(int mouseX, int mouseY) {
        List<ContainerEngine.Controller.SlotVisualState> visualStates = controller.getSlotVisualStates();
        int hoverSlotIndex = findSlotIndexAt(mouseX, mouseY, visualStates);
        int focusedSlotIndex = controller.getFocusedSlot();
        return new FrameSlotContext(visualStates, hoverSlotIndex, focusedSlotIndex);
    }

    private void drawBoundSlotItems(
            GuiGraphics guiGraphics,
            List<ContainerEngine.Controller.SlotVisualState> slotVisualStates
    ) {
        AbstractContainerScreenAccessor accessor = (AbstractContainerScreenAccessor) this;
        net.minecraft.world.inventory.Slot clicked = accessor.apricityui$getClickedSlot();
        ItemStack draggingItem = accessor.apricityui$getDraggingItem();
        boolean splitting = accessor.apricityui$isSplittingStack();
        Set<net.minecraft.world.inventory.Slot> quickCraftSlots = accessor.apricityui$getQuickCraftSlots();
        boolean quickCrafting = accessor.apricityui$isQuickCrafting();
        int quickCraftingType = accessor.apricityui$getQuickCraftingType();
        ItemStack carried = menu.getCarried();
        int quickCraftBasePlaceCount = 0;
        if (quickCrafting && !carried.isEmpty() && quickCraftSlots != null && quickCraftSlots.size() > 1) {
            quickCraftBasePlaceCount = AbstractContainerMenu.getQuickCraftPlaceCount(quickCraftSlots, quickCraftingType, carried);
        }
        for (ContainerEngine.Controller.SlotVisualState slotVisualState : slotVisualStates) {
            if (slotVisualState.hidden()) continue;
            if (!slotVisualState.renderItem()) continue;
            if (slotVisualState.disabled()) continue;

            int slotIndex = slotVisualState.globalSlotIndex();
            if (slotIndex < 0 || slotIndex >= menu.slots.size()) continue;
            net.minecraft.world.inventory.Slot slot = menu.slots.get(slotIndex);
            if (slot == null || !slot.isActive()) continue;

            ItemStack renderStack = slot.getItem();
            String overlayText = null;
            boolean drawQuickCraftGhost = false;

            if (slot == clicked && !draggingItem.isEmpty() && splitting && !renderStack.isEmpty()) {
                renderStack = renderStack.copyWithCount(renderStack.getCount() / 2);
            } else if (quickCrafting && quickCraftSlots != null && quickCraftSlots.contains(slot) && !carried.isEmpty()) {
                if (quickCraftSlots.size() <= 1) {
                    continue;
                }
                if (AbstractContainerMenu.canItemQuickReplace(slot, carried, true) && menu.canDragTo(slot)) {
                    drawQuickCraftGhost = true;
                    int maxStackSize = Math.min(carried.getMaxStackSize(), slot.getMaxStackSize(carried));
                    int existingCount = slot.getItem().isEmpty() ? 0 : slot.getItem().getCount();
                    int placeCount = quickCraftBasePlaceCount + existingCount;
                    if (placeCount > maxStackSize) {
                        placeCount = maxStackSize;
                        overlayText = ChatFormatting.YELLOW + String.valueOf(maxStackSize);
                    }
                    renderStack = carried.copyWithCount(placeCount);
                }
            }

            if (renderStack.isEmpty()) continue;

            int slotSize = Math.max(1, slotVisualState.slotSize());
            float iconScale = Math.max(0.01F, slotVisualState.iconScale());
            int padding = clampPadding(slotSize, slotVisualState.padding());
            int renderAreaSize = Math.max(1, slotSize - padding * 2);
            int drawX = leftPos + slot.x + padding + (int) Math.round((renderAreaSize - 16) / 2.0);
            int drawY = topPos + slot.y + padding + (int) Math.round((renderAreaSize - 16) / 2.0);

            guiGraphics.pose().pushPose();
            guiGraphics.pose().translate(0.0D, 0.0D, 100.0D + slotVisualState.zIndex());
            if (drawQuickCraftGhost) {
                int ghostSize = Math.max(1, Math.round(16.0F * iconScale));
                int ghostX = Math.round(drawX + 8.0F - ghostSize / 2.0F);
                int ghostY = Math.round(drawY + 8.0F - ghostSize / 2.0F);
                guiGraphics.fill(ghostX, ghostY, ghostX + ghostSize, ghostY + ghostSize, QUICK_CRAFT_GHOST_COLOR);
            }
            applyItemScaleTransform(guiGraphics, drawX, drawY, iconScale);
            guiGraphics.renderItem(renderStack, drawX, drawY, slot.x + slot.y * imageWidth);
            guiGraphics.renderItemDecorations(font, renderStack, drawX, drawY, overlayText);
            guiGraphics.pose().popPose();
        }
    }

    /**
     * repeat 的运行时代理槽位不在文档树中，默认文档渲染不会绘制其背景/边框。
     * 这里按真实菜单槽位坐标补绘代理槽位外观，保证背景与可交互区域对齐。
     */
    private void drawBoundSlotRuntimeProxyBackgrounds(
            GuiGraphics guiGraphics,
            List<ContainerEngine.Controller.SlotVisualState> slotVisualStates
    ) {
        if (slotVisualStates.isEmpty()) return;
        for (ContainerEngine.Controller.SlotVisualState slotVisualState : slotVisualStates) {
            if (slotVisualState.hidden()) continue;
            if (!slotVisualState.renderBackground()) continue;

            int slotIndex = slotVisualState.globalSlotIndex();
            if (slotIndex < 0 || slotIndex >= menu.slots.size()) continue;
            Slot runtimeSlot = controller.getBoundSlotElement(slotIndex);
            if (runtimeSlot == null) continue;
            if (!runtimeSlot.isRuntimeRepeatProxy()) continue;

            net.minecraft.world.inventory.Slot menuSlot = menu.slots.get(slotIndex);
            if (menuSlot == null || !menuSlot.isActive()) continue;

            int slotSize = Math.max(1, slotVisualState.slotSize());
            runtimeSlot.getRenderer().position.set(new Position(leftPos + menuSlot.x, topPos + menuSlot.y));
            runtimeSlot.getRenderer().size.set(new Size(slotSize, slotSize));
            runtimeSlot.getRenderer().box.clear();

            guiGraphics.pose().pushPose();
            guiGraphics.pose().translate(0.0D, 0.0D, 10.0D + slotVisualState.zIndex());
            Base.resolveOffset(guiGraphics.pose());
            runtimeSlot.drawPhase(guiGraphics.pose(), Base.RenderPhase.SHADOW);
            Base.resolveOffset(guiGraphics.pose());
            runtimeSlot.drawPhase(guiGraphics.pose(), Base.RenderPhase.BODY);
            Base.resolveOffset(guiGraphics.pose());
            runtimeSlot.drawPhase(guiGraphics.pose(), Base.RenderPhase.BORDER);
            guiGraphics.pose().popPose();
        }
    }

    private void syncBoundSlotRuntimeClasses(FrameSlotContext frameContext) {
        List<ContainerEngine.Controller.SlotVisualState> slotVisualStates = frameContext.visualStates();
        if (slotVisualStates.isEmpty()) return;

        int hoverSlotIndex = frameContext.hoverSlotIndex();
        int focusedSlotIndex = frameContext.focusedSlotIndex();
        for (ContainerEngine.Controller.SlotVisualState slotVisualState : slotVisualStates) {
            int slotIndex = slotVisualState.globalSlotIndex();
            if (slotIndex < 0 || slotIndex >= menu.slots.size()) continue;
            Slot runtimeSlot = controller.getBoundSlotElement(slotIndex);
            if (runtimeSlot == null) continue;

            net.minecraft.world.inventory.Slot menuSlot = menu.slots.get(slotIndex);
            boolean hovered = hoverSlotIndex == slotIndex || runtimeSlot.isHover;
            boolean active = focusedSlotIndex == slotIndex || runtimeSlot.isActive;
            boolean disabled = slotVisualState.disabled();
            boolean hasItem = menuSlot != null && menuSlot.hasItem();
            runtimeSlot.syncRuntimeStateClasses(hovered, active, disabled, hasItem);
        }
    }

    private void ensureRecipePreviewSlots() {
        if (linkedDocument == null) return;
        // 复制快照避免初始化过程中触发文档元素集合并发修改。
        List<Element> snapshot = new ArrayList<>(linkedDocument.getElements());
        boolean changed = false;
        for (Element element : snapshot) {
            if (element instanceof Recipe recipe) {
                changed |= recipe.ensurePreviewSlots();
            }
        }
        if (changed) {
            controller.markLayoutDirty(ContainerEngine.Controller.DirtyReason.RECIPE_PREVIEW);
        }
    }

    private void drawVirtualSlotItems(GuiGraphics guiGraphics) {
        if (linkedDocument == null) return;

        for (Element element : linkedDocument.getElements()) {
            if (!(element instanceof Slot slot)) continue;
            if (!slot.isVirtualMode()) continue;
            if (!slot.isVisible) continue;
            if ("none".equals(slot.getComputedStyle().display)) continue;
            if (!slot.shouldRenderItem()) continue;

            Rect rect = Rect.of(slot);
            Position body = rect.getBodyRectPosition();
            Size bodySize = rect.getBodyRectSize();
            int slotWidth = Math.max(1, (int) Math.round(bodySize.width()));
            int slotHeight = Math.max(1, (int) Math.round(bodySize.height()));
            int padding = clampPadding(Math.min(slotWidth, slotHeight), slot.resolvePadding(0));
            int renderWidth = Math.max(1, slotWidth - padding * 2);
            int renderHeight = Math.max(1, slotHeight - padding * 2);
            int drawX = (int) Math.round(body.x + padding + (renderWidth - 16) / 2.0);
            int drawY = (int) Math.round(body.y + padding + (renderHeight - 16) / 2.0);
            ItemStack stack = slot.resolveDisplayStack();
            if (stack.isEmpty()) continue;

            float iconScale = Math.max(0.01F, slot.resolveIconScale(1.0F));
            guiGraphics.pose().pushPose();
            applyItemScaleTransform(guiGraphics, drawX, drawY, iconScale);
            guiGraphics.renderItem(stack, drawX, drawY);
            guiGraphics.renderItemDecorations(font, stack, drawX, drawY);
            guiGraphics.pose().popPose();
        }
        drawRuntimeRecipePreviewSlotBackgrounds(guiGraphics);
        drawRuntimeRecipePreviewItems(guiGraphics);
    }

    private void drawRuntimeRecipePreviewSlotBackgrounds(GuiGraphics guiGraphics) {
        for (Element element : linkedDocument.getElements()) {
            if (!(element instanceof Recipe recipe)) continue;
            if (!recipe.isVisible) continue;
            if ("none".equals(recipe.getComputedStyle().display)) continue;

            Rect recipeRect = Rect.of(recipe);
            Position recipeBody = recipeRect.getBodyRectPosition();
            for (Recipe.RuntimePreviewSlot runtimePreviewSlot : recipe.getRuntimePreviewSlots()) {
                Slot runtimeSlot = runtimePreviewSlot.slot();
                if (runtimeSlot == null) continue;
                if (!runtimeSlot.shouldRenderBackground()) continue;

                int slotSize = Math.max(1, runtimePreviewSlot.slotSize());
                int slotX = (int) Math.round(recipeBody.x + runtimePreviewSlot.x());
                int slotY = (int) Math.round(recipeBody.y + runtimePreviewSlot.y());
                runtimeSlot.getRenderer().position.set(new Position(slotX, slotY));
                runtimeSlot.getRenderer().size.set(new Size(slotSize, slotSize));
                runtimeSlot.getRenderer().box.clear();

                guiGraphics.pose().pushPose();
                guiGraphics.pose().translate(0.0D, 0.0D, 10.0D + runtimeSlot.resolveZIndex(0));
                Base.resolveOffset(guiGraphics.pose());
                runtimeSlot.drawPhase(guiGraphics.pose(), Base.RenderPhase.SHADOW);
                Base.resolveOffset(guiGraphics.pose());
                runtimeSlot.drawPhase(guiGraphics.pose(), Base.RenderPhase.BODY);
                Base.resolveOffset(guiGraphics.pose());
                runtimeSlot.drawPhase(guiGraphics.pose(), Base.RenderPhase.BORDER);
                guiGraphics.pose().popPose();
            }
        }
    }

    private void drawRuntimeRecipePreviewItems(GuiGraphics guiGraphics) {
        for (Element element : linkedDocument.getElements()) {
            if (!(element instanceof Recipe recipe)) continue;
            if (!recipe.isVisible) continue;
            if ("none".equals(recipe.getComputedStyle().display)) continue;

            Rect recipeRect = Rect.of(recipe);
            Position recipeBody = recipeRect.getBodyRectPosition();
            for (Recipe.RuntimePreviewSlot runtimePreviewSlot : recipe.getRuntimePreviewSlots()) {
                Slot runtimeSlot = runtimePreviewSlot.slot();
                if (runtimeSlot == null) continue;
                if (!runtimeSlot.shouldRenderItem()) continue;
                runtimeSlot.tick();

                ItemStack stack = runtimeSlot.resolveDisplayStack();
                if (stack.isEmpty()) continue;

                int slotSize = Math.max(1, runtimePreviewSlot.slotSize());
                int padding = clampPadding(slotSize, runtimeSlot.resolvePadding(0));
                float iconScale = Math.max(0.01F, runtimeSlot.resolveIconScale(1.0F));
                int slotX = (int) Math.round(recipeBody.x + runtimePreviewSlot.x());
                int slotY = (int) Math.round(recipeBody.y + runtimePreviewSlot.y());
                int renderArea = Math.max(1, slotSize - padding * 2);
                int drawX = (int) Math.round(slotX + padding + (renderArea - 16) / 2.0);
                int drawY = (int) Math.round(slotY + padding + (renderArea - 16) / 2.0);

                guiGraphics.pose().pushPose();
                applyItemScaleTransform(guiGraphics, drawX, drawY, iconScale);
                guiGraphics.renderItem(stack, drawX, drawY);
                guiGraphics.renderItemDecorations(font, stack, drawX, drawY);
                guiGraphics.pose().popPose();
            }
        }
    }

    private void drawSlotHoverTooltipByElement(
            GuiGraphics guiGraphics,
            int mouseX,
            int mouseY,
            FrameSlotContext frameContext
    ) {
        if (linkedDocument == null) return;
        List<Element> elements = linkedDocument.getElements();
        for (int index = elements.size() - 1; index >= 0; index--) {
            Element element = elements.get(index);
            if (!(element instanceof MinecraftElement minecraftElement)) continue;
            if (!minecraftElement.isHover) continue;

            ItemStack stack = minecraftElement.getTooltipStack();
            if (stack.isEmpty()) continue;
            minecraftElement.renderTooltip(guiGraphics, mouseX, mouseY);
            return;
        }

        Slot runtimeRecipeSlot = findHoveredRuntimeRecipeSlot(mouseX, mouseY);
        if (runtimeRecipeSlot != null) {
            runtimeRecipeSlot.renderTooltip(guiGraphics, mouseX, mouseY);
            return;
        }

        // bound slot 只走原版物品 tooltip 语义：
        // 1) 先尝试原版维护的 hoveredSlot；
        // 2) 若无效（自定义布局场景常见），再用本地命中逻辑兜底。
        if (hoveredSlot != null && hoveredSlot.isActive() && isSlotPointerInteractable(hoveredSlot)) {
            ItemStack stack = hoveredSlot.getItem();
            if (!stack.isEmpty()) {
                guiGraphics.renderTooltip(font, stack, mouseX, mouseY);
                return;
            }
        }

        int slotIndex = frameContext == null
                ? findSlotIndexAt(mouseX, mouseY)
                : frameContext.hoverSlotIndex();
        if (slotIndex < 0 || slotIndex >= menu.slots.size()) return;

        net.minecraft.world.inventory.Slot menuSlot = menu.slots.get(slotIndex);
        if (menuSlot == null || !menuSlot.isActive()) return;
        ItemStack stack = menuSlot.getItem();
        if (stack.isEmpty()) return;

        guiGraphics.renderTooltip(font, stack, mouseX, mouseY);
    }

    private Slot findHoveredRuntimeRecipeSlot(int mouseX, int mouseY) {
        if (linkedDocument == null) return null;
        List<Element> elements = linkedDocument.getElements();
        for (int elementIndex = elements.size() - 1; elementIndex >= 0; elementIndex--) {
            Element element = elements.get(elementIndex);
            if (!(element instanceof Recipe recipe)) continue;
            if (!recipe.isVisible) continue;
            if ("none".equals(recipe.getComputedStyle().display)) continue;

            Rect recipeRect = Rect.of(recipe);
            Position recipeBody = recipeRect.getBodyRectPosition();
            List<Recipe.RuntimePreviewSlot> runtimeSlots = recipe.getRuntimePreviewSlots();
            for (int slotIndex = runtimeSlots.size() - 1; slotIndex >= 0; slotIndex--) {
                Recipe.RuntimePreviewSlot runtimePreviewSlot = runtimeSlots.get(slotIndex);
                Slot runtimeSlot = runtimePreviewSlot.slot();
                if (runtimeSlot == null) continue;
                if (!runtimeSlot.shouldAcceptPointer() || runtimeSlot.isDisabled()) {
                    runtimeSlot.setHover(false);
                    continue;
                }

                int slotSize = Math.max(1, runtimePreviewSlot.slotSize());
                int slotX = (int) Math.round(recipeBody.x + runtimePreviewSlot.x());
                int slotY = (int) Math.round(recipeBody.y + runtimePreviewSlot.y());
                boolean hover = mouseX >= slotX
                        && mouseX < slotX + slotSize
                        && mouseY >= slotY
                        && mouseY < slotY + slotSize;
                runtimeSlot.setHover(hover);
                if (hover) {
                    return runtimeSlot;
                }
            }
        }
        return null;
    }

    private void drawDevToolsOverlay(GuiGraphics guiGraphics) {
        var devToolsDocuments = Document.get(DEVTOOLS_PATH);
        if (devToolsDocuments.isEmpty()) return;

        Document devToolsDocument = devToolsDocuments.get(0);
        if (devToolsDocument == null || devToolsDocument.body == null) return;
        if (devToolsDocument == linkedDocument) return;
        Base.drawDocument(guiGraphics.pose(), devToolsDocument);
    }

    @Override
    public void onClose() {
        if (linkedDocument == null) {
            super.onClose();
            return;
        }

        if (linkedDocument.body != null) {
            linkedDocument.body.triggerEvent(currentEvent -> {
                if ("unload".equals(currentEvent.type) && currentEvent.listener != null) {
                    try {
                        currentEvent.listener.accept(currentEvent);
                    } catch (Exception ignored) {
                    }
                }
            });
        }

        linkedDocument.remove();
        super.onClose();
    }

    @Override
    public void removed() {
        controller.close();
        super.removed();
    }

    private static int clampPadding(int slotSize, int padding) {
        int maxPadding = Math.max(0, (Math.max(1, slotSize) - 1) / 2);
        int normalized = Math.max(0, padding);
        return Math.min(normalized, maxPadding);
    }

    private static void applyItemScaleTransform(GuiGraphics guiGraphics, int drawX, int drawY, float iconScale) {
        if (Math.abs(iconScale - 1.0F) <= ICON_SCALE_EPSILON) return;
        float centerX = drawX + 8.0F;
        float centerY = drawY + 8.0F;
        guiGraphics.pose().translate(centerX, centerY, 0.0D);
        guiGraphics.pose().scale(iconScale, iconScale, 1.0F);
        guiGraphics.pose().translate(-centerX, -centerY, 0.0D);
    }
}
