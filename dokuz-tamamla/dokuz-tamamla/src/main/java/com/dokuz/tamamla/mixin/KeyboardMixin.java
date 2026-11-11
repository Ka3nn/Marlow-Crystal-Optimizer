package com.dokuz.tamamla.mixin;

import com.dokuz.tamamla.Config;
import com.dokuz.tamamla.DokuzTamamlaClient;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.Keyboard;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Mod menüsünden atanan tuşu ve P tuşunu, hızlı basışlar kaçmasın diye doğrudan yakalar. */
@Mixin(Keyboard.class)
public class KeyboardMixin {
    @Inject(method = "onKey", at = @At("HEAD"))
    private void dokuz$onKey(long window, int key, int scancode, int action, int modifiers, CallbackInfo ci) {
        if (action != GLFW.GLFW_PRESS) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (window != mc.getWindow().getHandle() || mc.currentScreen != null || mc.player == null) return;

        int armKey = Config.get().armKey;
        if (armKey != InputUtil.UNKNOWN_KEY.getCode() && key == armKey) {
            DokuzTamamlaClient.armKeyPressed = true;
        }

        int resetKey = Config.get().resetKey;
        if (resetKey != InputUtil.UNKNOWN_KEY.getCode() && key == resetKey) {
            DokuzTamamlaClient.resetKeyPressed = true;
        }

        InputUtil.Key social = KeyBindingHelper.getBoundKeyOf(mc.options.socialInteractionsKey);
        if (social.getCategory() == InputUtil.Type.KEYSYM && key == social.getCode()) {
            DokuzTamamlaClient.socialKeyPressed = true;
        }
    }
}
