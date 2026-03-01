package com.sighs.apricityui.mixin;

import net.minecraft.client.renderer.blockentity.SignRenderer;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(value = SignRenderer.class)
public class SignRendererMixin {
    // @Inject(method = "render(Lnet/minecraft/world/level/block/entity/BlockEntity;FLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;II)V", at = @At("RETURN"))
    // private void qqq(BlockEntity par1, float par2, PoseStack par3, MultiBufferSource par4, int par5, int par6, CallbackInfo ci) {
    //
    //     WorldUIRenderer.renderOnSign((SignBlockEntity) par1, null, par3, par4, par5);
    // }
}
