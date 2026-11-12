package com.dokuz.tamamla;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.multiplayer.SocialInteractionsScreen;
import net.minecraft.client.util.InputUtil;
import net.minecraft.entity.decoration.ItemFrameEntity;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import org.lwjgl.glfw.GLFW;

public class DokuzTamamlaClient implements ClientModInitializer {
    private static boolean lastArmDown;
    private static boolean lastResetDown;
    private static boolean lastSocialDown;

    @Override
    public void onInitializeClient() {
        Config.get();

        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            DisguiseManager.INSTANCE.tick(mc);
            handleKeys(mc);
        });

        UseEntityCallback.EVENT.register((player, world, hand, entity, hit) -> {
            if (player == MinecraftClient.getInstance().player && entity instanceof ItemFrameEntity f) {
                DisguiseManager.INSTANCE.onUseFrame(player, hand, f);
            }
            return ActionResult.PASS;
        });
    }

    /** Tuş şu an basılı mı (GLFW ile doğrudan, sürümden bağımsız). */
    public static boolean isDown(MinecraftClient mc, int code) {
        if (code < GLFW.GLFW_KEY_SPACE || code > GLFW.GLFW_KEY_LAST) return false;
        return GLFW.glfwGetKey(mc.getWindow().getHandle(), code) == GLFW.GLFW_PRESS;
    }

    private static void handleKeys(MinecraftClient mc) {
        DisguiseManager m = DisguiseManager.INSTANCE;
        Config cfg = Config.get();
        boolean inGame = mc.player != null && mc.currentScreen == null;

        boolean armDown = isDown(mc, cfg.armKey);
        if (armDown && !lastArmDown && inGame) m.toggleArm(mc);
        lastArmDown = armDown;

        boolean resetDown = isDown(mc, cfg.resetKey);
        if (resetDown && !lastResetDown && inGame) {
            m.clearAll();
            m.notify(mc, "Her şey eski haline döndü");
        }
        lastResetDown = resetDown;

        // P (Social Interactions tuşu): her zaman mod menüsünü açar.
        // Sunucuda Social Interactions ekranı açılmışsa onun yerine mod menüsü gelir.
        InputUtil.Key social = KeyBindingHelper.getBoundKeyOf(mc.options.socialInteractionsKey);
        boolean socialDown = social.getCategory() == InputUtil.Type.KEYSYM && isDown(mc, social.getCode());
        boolean canOpen = mc.player != null
                && (mc.currentScreen == null || mc.currentScreen instanceof SocialInteractionsScreen);
        if (socialDown && !lastSocialDown && canOpen) {
            mc.setScreen(new ModMenuScreen(null));
            mc.inGameHud.setOverlayMessage(Text.empty(), false); // "kullanılamıyor" yazısını gizle
        }
        lastSocialDown = socialDown;
    }
}
