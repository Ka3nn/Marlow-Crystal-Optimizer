package com.dokuz.tamamla.mixin;

import com.dokuz.tamamla.DisguiseManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Her kare çizilmeden hemen önce kontrol: değişen item hiçbir karede gerçek haliyle görünmez. */
@Mixin(GameRenderer.class)
public class GameRendererMixin {
    @Inject(method = "render", at = @At("HEAD"), require = 0)
    private void dokuz$beforeFrame(CallbackInfo ci) {
        DisguiseManager.INSTANCE.update(MinecraftClient.getInstance());
    }
}
