package com.sighs.apricityui.instance.render.item;

import com.sighs.apricityui.render.Base;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

/**
 * AUI 物品模型绘制所需的运行时上下文。
 * level 和 entity 可以为空，以支持主菜单和展示槽位。
 */
public record ItemRenderContext(
        ClientLevel level,
        LivingEntity entity,
        int seed,
        int packedLight,
        int packedOverlay,
        boolean guiLighting,
        float modelZ,
        float decorationZ
) {
    public static final int GUI_LIGHT = LightTexture.FULL_BRIGHT;
    public static final int NO_OVERLAY = OverlayTexture.NO_OVERLAY;

    public static ItemRenderContext forGui(ItemStack stack) {
        Minecraft minecraft = Minecraft.getInstance();
        int seed = stack == null || stack.isEmpty()
                ? 0
                : 31 * System.identityHashCode(stack.getItem()) + stack.getDamageValue();
        return new ItemRenderContext(
                minecraft.level,
                minecraft.player,
                seed,
                GUI_LIGHT,
                NO_OVERLAY,
                true,
                Base.getGuiItemModelZ(),
                Base.getGuiItemDecorationZ()
        );
    }

    /**
     * 与 GuiGraphics.renderItemDecorations 一致的当前物品冷却进度。
     */
    public static float resolveCooldownProgress(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return -1.0F;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) return -1.0F;
        float progress = minecraft.player.getCooldowns().getCooldownPercent(stack.getItem(), minecraft.getFrameTime());
        return progress > 0.0F ? progress : -1.0F;
    }
}
