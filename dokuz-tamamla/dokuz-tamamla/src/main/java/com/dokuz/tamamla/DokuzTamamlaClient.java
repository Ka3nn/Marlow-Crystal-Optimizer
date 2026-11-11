package com.dokuz.tamamla;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.decoration.ItemFrameEntity;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;

public class DokuzTamamlaClient implements ClientModInitializer {
    /** KeyboardMixin tarafından, oyun ekranında (menü kapalıyken) tuşa basılınca işaretlenir. */
    public static boolean armKeyPressed;
    public static boolean resetKeyPressed;
    public static boolean socialKeyPressed;

    @Override
    public void onInitializeClient() {
        Config.get();

        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            DisguiseManager.INSTANCE.tick(mc);
            handleKeys(mc);
        });

        UseEntityCallback.EVENT.register((player, world, hand, entity, hit) -> {
            if (world.isClient && entity instanceof ItemFrameEntity f) {
                DisguiseManager.INSTANCE.onUseFrame(player, hand, f);
            }
            return ActionResult.PASS;
        });
    }

    private static void handleKeys(MinecraftClient mc) {
        DisguiseManager m = DisguiseManager.INSTANCE;

        if (armKeyPressed) {
            armKeyPressed = false;
            if (mc.player != null) m.toggleArm(mc);
        }

        if (resetKeyPressed) {
            resetKeyPressed = false;
            if (mc.player != null) {
                m.resetRound();
                m.notify(mc, "Tur sıfırlandı");
            }
        }

        // P (Social Interactions tuşu): Social Interactions açılmadıysa mod menüsünü aç.
        if (socialKeyPressed) {
            socialKeyPressed = false;
            if (mc.player != null && mc.currentScreen == null) {
                mc.setScreen(new ModMenuScreen(null));
                mc.inGameHud.setOverlayMessage(Text.empty(), false); // "kullanılamıyor" yazısını gizle
            }
        }
    }
}
