package com.sighs.apricityui.mixin;

import com.sighs.apricityui.util.mixin.RealPartialTickProvider;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Timer;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public class MinecraftMixin implements RealPartialTickProvider {
    @Unique
    private float aui$realPartialTick;

    @Shadow
    private volatile boolean pause;
    @Shadow
    private float pausePartialTick;

    @Shadow
    @Final
    private Timer timer;

    @Inject(
            method = "runTick",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/util/profiling/ProfilerFiller;popPush(Ljava/lang/String;)V",
                    ordinal = 0,
                    shift = At.Shift.BEFORE)
    )
    private void aui$realPartialTick(boolean bl, CallbackInfo ci) {
        aui$realPartialTick = this.pause ? this.pausePartialTick : this.timer.partialTick;
    }

    @Unique
    public float aui$getRealPartialTick() {
        return aui$realPartialTick;
    }
}
