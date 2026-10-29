package com.sighs.apricityui.mixin;

import com.sighs.apricityui.world.WorldProjection;
import net.minecraft.client.renderer.GameRenderer;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
public abstract class WorldProjectionMixin {
    @Inject(method = "renderLevel()V", at = @At("HEAD"))
    private void apricityui$beginWorldProjection(CallbackInfo ci) {
        WorldProjection.FRAME.clear();
    }

    // Patched 26.3 renderLevel uploads once, after hurt/view bob and portal/nausea.
    // Capture the upload argument rather than CameraRenderState's unmodified base.
    @ModifyArg(method = "renderLevel()V", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/ProjectionMatrixBuffer;getBuffer(Lorg/joml/Matrix4f;)Lcom/mojang/renderpearl/api/buffers/GpuBufferSlice;"), index = 0)
    private Matrix4f apricityui$captureWorldProjection(Matrix4f projection) {
        WorldProjection.FRAME.capture(projection);
        return projection;
    }

    @Inject(method = {"renderLevel()V", "resetData()V"}, at = @At("RETURN"))
    private void apricityui$endWorldProjection(CallbackInfo ci) {
        WorldProjection.FRAME.clear();
    }
}
