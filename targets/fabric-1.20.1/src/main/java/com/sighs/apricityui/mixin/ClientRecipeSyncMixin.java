package com.sighs.apricityui.mixin;

import com.sighs.apricityui.dom.expander.RecipeExpander;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundUpdateRecipesPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPacketListener.class)
public abstract class ClientRecipeSyncMixin {
    @Inject(method = "handleUpdateRecipes", at = @At("TAIL"))
    private void apricityui$refreshRecipePreviews(ClientboundUpdateRecipesPacket packet, CallbackInfo ci) {
        RecipeExpander.clearCache();
    }
}
