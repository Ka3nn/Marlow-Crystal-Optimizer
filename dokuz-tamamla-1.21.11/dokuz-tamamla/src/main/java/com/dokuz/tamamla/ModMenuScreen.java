package com.dokuz.tamamla;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.lwjgl.glfw.GLFW;

import java.util.stream.Collectors;

public class ModMenuScreen extends Screen {
    private final Screen parent;
    private String status = "";

    private ButtonWidget modeButton;
    private ButtonWidget indexButton;
    private ButtonWidget notifyButton;
    private ButtonWidget resetButton;
    private ButtonWidget armButton;
    private ButtonWidget keyButton;
    private ButtonWidget resetKeyButton;
    /** 0 = tuş beklenmiyor, 1 = 9'a tamamlama tuşu, 2 = turu sıfırlama tuşu. */
    private int waitingFor;

    public ModMenuScreen(Screen parent) {
        super(Text.literal("Dokuz Tamamla"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        DisguiseManager m = DisguiseManager.INSTANCE;
        Config cfg = Config.get();
        int w = 240, h = 20, step = 22;
        int x = this.width / 2 - w / 2;
        int y = 50;

        addDrawableChild(ButtonWidget.builder(Text.literal("Hotbar'ı kaydet (slot 1-9 = rakam 1-9)"),
                b -> status = m.saveHotbar(client)).dimensions(x, y, w, h).build());
        y += step;

        modeButton = addDrawableChild(ButtonWidget.builder(modeText(), b -> {
            cfg.mode = (cfg.mode + 1) % 10;
            cfg.save();
            refresh();
        }).dimensions(x, y, w, h).build());
        y += step;

        indexButton = addDrawableChild(ButtonWidget.builder(indexText(), b -> {
            cfg.index = cfg.index % 5 + 1;
            cfg.save();
            refresh();
        }).dimensions(x, y, w, h).build());
        y += step;

        notifyButton = addDrawableChild(ButtonWidget.builder(notifyText(), b -> {
            cfg.notify = !cfg.notify;
            cfg.save();
            refresh();
        }).dimensions(x, y, w / 2 - 1, h).build());
        resetButton = addDrawableChild(ButtonWidget.builder(autoResetText(), b -> {
            cfg.autoReset = !cfg.autoReset;
            cfg.save();
            refresh();
        }).dimensions(x + w / 2 + 1, y, w / 2 - 1, h).build());
        y += step;

        keyButton = addDrawableChild(ButtonWidget.builder(keyText(), b -> startWaiting(1)).dimensions(x, y, w / 2 - 1, h).build());
        resetKeyButton = addDrawableChild(ButtonWidget.builder(resetKeyText(), b -> startWaiting(2)).dimensions(x + w / 2 + 1, y, w / 2 - 1, h).build());
        y += step;

        armButton = addDrawableChild(ButtonWidget.builder(armText(), b -> {
            m.toggleArm(client);
            refresh();
        }).dimensions(x, y, w, h).build());
        y += step;

        addDrawableChild(ButtonWidget.builder(Text.literal("Turu sıfırla"), b -> {
            m.resetRound();
            status = "Gelen itemler listesi sıfırlandı.";
            refresh();
        }).dimensions(x, y, w / 2 - 1, h).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Görünümleri temizle"), b -> {
            m.clearAll();
            status = "Tüm sahte görünümler kaldırıldı.";
            refresh();
        }).dimensions(x + w / 2 + 1, y, w / 2 - 1, h).build());
        y += step;

        addDrawableChild(ButtonWidget.builder(Text.literal("Kapat"), b -> close())
                .dimensions(x, y, w, h).build());
    }

    private void refresh() {
        modeButton.setMessage(modeText());
        indexButton.setMessage(indexText());
        notifyButton.setMessage(notifyText());
        resetButton.setMessage(autoResetText());
        armButton.setMessage(armText());
        keyButton.setMessage(keyText());
        resetKeyButton.setMessage(resetKeyText());
    }

    private Text keyText() {
        if (waitingFor == 1) return Text.literal("> Tuşa bas <").formatted(Formatting.YELLOW);
        return Text.literal("Tamamla tuşu: ").append(keyName(Config.get().armKey));
    }

    private Text resetKeyText() {
        if (waitingFor == 2) return Text.literal("> Tuşa bas <").formatted(Formatting.YELLOW);
        return Text.literal("Sıfırla tuşu: ").append(keyName(Config.get().resetKey));
    }

    private static Text keyName(int code) {
        if (code == InputUtil.UNKNOWN_KEY.getCode()) return Text.literal("Yok");
        return InputUtil.Type.KEYSYM.createFromCode(code).getLocalizedText();
    }

    /** Tuş atarken: menü açıkken basılı tuşları her tick kontrol eder (sürümden bağımsız). */
    private final boolean[] heldAtStart = new boolean[GLFW.GLFW_KEY_LAST + 1];

    private void startWaiting(int which) {
        waitingFor = which;
        for (int k = GLFW.GLFW_KEY_SPACE; k <= GLFW.GLFW_KEY_LAST; k++) {
            heldAtStart[k] = DokuzTamamlaClient.isDown(client, k);
        }
        status = "Bir tuşa bas (ESC: iptal, DELETE: tuşu kaldır)";
        refresh();
    }

    @Override
    public void tick() {
        super.tick();
        if (waitingFor == 0 || client == null) return;
        for (int k = GLFW.GLFW_KEY_SPACE; k <= GLFW.GLFW_KEY_LAST; k++) {
            boolean down = DokuzTamamlaClient.isDown(client, k);
            if (!down) { heldAtStart[k] = false; continue; }
            if (heldAtStart[k]) continue;
            Config cfg = Config.get();
            if (k == GLFW.GLFW_KEY_ESCAPE) {
                status = "İptal edildi.";
            } else {
                int code = (k == GLFW.GLFW_KEY_DELETE || k == GLFW.GLFW_KEY_BACKSPACE)
                        ? InputUtil.UNKNOWN_KEY.getCode() : k;
                if (waitingFor == 1) cfg.armKey = code; else cfg.resetKey = code;
                cfg.save();
                status = code == InputUtil.UNKNOWN_KEY.getCode() ? "Tuş kaldırıldı." : "Tuş ayarlandı.";
            }
            waitingFor = 0;
            refresh();
            return;
        }
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return waitingFor == 0;
    }

    private Text modeText() {
        int mode = Config.get().mode;
        return Text.literal(mode == 0 ? "Hedef: Otomatik (9'a tamamla)" : "Hedef: Elle → " + mode);
    }

    private Text indexText() {
        return Text.literal("9'a tamamlanacak item: " + Config.get().index + ". gelen");
    }

    private Text notifyText() {
        return Text.literal("Bildirim: " + (Config.get().notify ? "Açık" : "Kapalı"));
    }

    private Text autoResetText() {
        return Text.literal("Sonra sıfırla: " + (Config.get().autoReset ? "Açık" : "Kapalı"));
    }

    private Text armText() {
        DisguiseManager m = DisguiseManager.INSTANCE;
        if (m.isArmed()) return Text.literal("HAZIR (" + m.getArmedDigit() + ") — iptal et");
        return Text.literal("Sonraki itemi ayarla (").append(keyName(Config.get().armKey)).append(")");
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        super.render(ctx, mouseX, mouseY, delta);
        int cx = this.width / 2;
        Config cfg = Config.get();
        DisguiseManager m = DisguiseManager.INSTANCE;

        ctx.drawCenteredTextWithShadow(textRenderer, this.title, cx, 6, 0xFFFFFFFF);

        MutableText saved = Text.literal("Kayıtlı: ");
        for (int d = 1; d <= 9; d++) {
            saved.append(Text.literal(d + " ").formatted(cfg.hasDigit(d) ? Formatting.GREEN : Formatting.RED));
        }
        ctx.drawCenteredTextWithShadow(textRenderer, saved, cx, 18, 0xFFFFFFFF);

        String got = m.received.isEmpty() ? "-" : m.received.stream().map(String::valueOf).collect(Collectors.joining(", "));
        int t = m.computeTarget();
        String next = t == -1 ? "bekleniyor" : t == 0 ? "yok (9)" : String.valueOf(t);
        ctx.drawCenteredTextWithShadow(textRenderer, Text.literal("Gelenler: " + got + "   →   Sıradaki: " + next), cx, 30, 0xFFFFFF55);

        if (!status.isEmpty()) {
            ctx.drawCenteredTextWithShadow(textRenderer, Text.literal(status), cx, this.height - 14, 0xFFAAFFAA);
        }
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    @Override
    public void close() {
        if (client != null) client.setScreen(parent);
    }
}
