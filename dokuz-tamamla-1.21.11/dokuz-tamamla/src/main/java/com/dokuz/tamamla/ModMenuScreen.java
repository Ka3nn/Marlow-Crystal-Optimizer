package com.dokuz.tamamla;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/** P ile açılan mod menüsü. İki sekme: "9 modu" ve "21 modu". */
public class ModMenuScreen extends Screen {
    private final Screen parent;
    private String status = "";

    /** Tuş atarken hangi ayar bekleniyor: 0 yok, 1 tamamla, 2 sıfırla, 3 21 aç/kapa, 4 21 sıfırla. */
    private int waitingFor;
    private final boolean[] heldAtStart = new boolean[GLFW.GLFW_KEY_LAST + 1];

    /** Yazısı duruma göre değişen butonlar ve yazılarını üreten fonksiyonlar. */
    private final List<ButtonWidget> dynButtons = new ArrayList<>();
    private final List<java.util.function.Supplier<Text>> dynTexts = new ArrayList<>();

    public ModMenuScreen(Screen parent) {
        super(Text.literal("Dokuz Tamamla"));
        this.parent = parent;
    }

    private ButtonWidget dyn(java.util.function.Supplier<Text> text, ButtonWidget.PressAction action,
                             int x, int y, int w, int h) {
        ButtonWidget b = addDrawableChild(ButtonWidget.builder(text.get(), action).dimensions(x, y, w, h).build());
        dynButtons.add(b);
        dynTexts.add(text);
        return b;
    }

    @Override
    protected void init() {
        dynButtons.clear();
        dynTexts.clear();
        Config cfg = Config.get();
        int w = 240, h = 20, step = 22;
        int x = this.width / 2 - w / 2;
        int half = w / 2 - 1;
        int x2 = x + w / 2 + 1;

        // Sekmeler + kapat
        int tabW = (w - 52) / 2;
        dyn(() -> tabText(0, "9 Modu"), b -> switchPage(0), x, 42, tabW, h);
        dyn(() -> tabText(1, "21 Modu"), b -> switchPage(1), x + tabW + 2, 42, tabW, h);
        addDrawableChild(ButtonWidget.builder(Text.literal("Kapat"), b -> close())
                .dimensions(x + w - 48, 42, 48, h).build());

        int y = 68;
        if (cfg.menuPage == 1) init21(cfg, x, x2, w, half, h, step, y);
        else init9(cfg, x, x2, w, half, h, step, y);
    }

    private void switchPage(int page) {
        Config cfg = Config.get();
        if (cfg.menuPage == page) return;
        cfg.menuPage = page;
        cfg.save();
        waitingFor = 0;
        status = "";
        clearAndInit();
    }

    private Text tabText(int page, String name) {
        boolean on = Config.get().menuPage == page;
        return on ? Text.literal("[ " + name + " ]").formatted(Formatting.YELLOW) : Text.literal(name);
    }

    // ---------------------------------------------------------------- 9 modu sekmesi

    private void init9(Config cfg, int x, int x2, int w, int half, int h, int step, int y) {
        DisguiseManager m = DisguiseManager.INSTANCE;

        addDrawableChild(ButtonWidget.builder(Text.literal("Hotbar'ı kaydet (slot 1-9 = rakam 1-9)"),
                b -> status = m.saveHotbar(client)).dimensions(x, y, w, h).build());
        y += step;

        dyn(() -> Text.literal(cfg.mode == 0 ? "Hedef: Otomatik (9'a tamamla)" : "Hedef: Elle → " + cfg.mode),
                b -> { cfg.mode = (cfg.mode + 1) % 10; cfg.save(); refresh(); }, x, y, w, h);
        y += step;

        dyn(() -> Text.literal("Tamamlanacak: " + cfg.index + ". gelen"),
                b -> { cfg.index = cfg.index % 5 + 1; cfg.save(); refresh(); }, x, y, half, h);
        dyn(() -> Text.literal("Aynıları ayır: " + onOff(cfg.autoSplit)),
                b -> { cfg.autoSplit = !cfg.autoSplit; cfg.save(); refresh(); }, x2, y, half, h);
        y += step;

        dyn(() -> Text.literal("Bildirim: " + onOff(cfg.notify)),
                b -> { cfg.notify = !cfg.notify; cfg.save(); refresh(); }, x, y, half, h);
        dyn(() -> Text.literal("Sonra sıfırla: " + onOff(cfg.autoReset)),
                b -> { cfg.autoReset = !cfg.autoReset; cfg.save(); refresh(); }, x2, y, half, h);
        y += step;

        dyn(() -> keyLabel(1, "Tamamla tuşu: ", cfg.armKey), b -> startWaiting(1), x, y, half, h);
        dyn(() -> keyLabel(2, "Sıfırla tuşu: ", cfg.resetKey), b -> startWaiting(2), x2, y, half, h);
        y += step;

        dyn(() -> m.isArmed()
                        ? Text.literal("HAZIR (" + m.getArmedDigit() + ") — iptal et")
                        : Text.literal("Sonraki itemi ayarla (").append(keyName(cfg.armKey)).append(")"),
                b -> { m.toggleArm(client); refresh(); }, x, y, w, h);
        y += step;

        addDrawableChild(ButtonWidget.builder(Text.literal("Turu sıfırla"), b -> {
            m.resetRound();
            status = "Gelen itemler listesi sıfırlandı.";
            refresh();
        }).dimensions(x, y, half, h).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Eski haline döndür"), b -> {
            m.clearAll();
            status = "Her şey gerçek haline döndü.";
            refresh();
        }).dimensions(x2, y, half, h).build());
    }

    // ---------------------------------------------------------------- 21 modu sekmesi

    private void init21(Config cfg, int x, int x2, int w, int half, int h, int step, int y) {
        DisguiseManager m = DisguiseManager.INSTANCE;

        addDrawableChild(ButtonWidget.builder(Text.literal("Hotbar'ı kaydet (slot 1-9 = A,3,4,5,6,7,8,9,10)"),
                b -> status = m.saveHotbar21(client)).dimensions(x, y, w, h).build());
        y += step;

        dyn(() -> m.isActive21()
                        ? Text.literal("21 modu: AÇIK — kapat (").append(keyName(cfg.toggle21Key)).append(")").formatted(Formatting.GREEN)
                        : Text.literal("21 modu: Kapalı — aç (").append(keyName(cfg.toggle21Key)).append(")"),
                b -> { m.toggle21(client); refresh(); }, x, y, w, h);
        y += step;

        dyn(() -> keyLabel(3, "Aç/kapa tuşu: ", cfg.toggle21Key), b -> startWaiting(3), x, y, half, h);
        dyn(() -> keyLabel(4, "Sıfırla tuşu: ", cfg.reset21Key), b -> startWaiting(4), x2, y, half, h);
        y += step;

        dyn(() -> Text.literal("Bildirim: " + onOff(cfg.notify)),
                b -> { cfg.notify = !cfg.notify; cfg.save(); refresh(); }, x, y, half, h);
        dyn(() -> Text.literal("Aynıları ayır: " + onOff(cfg.autoSplit)),
                b -> { cfg.autoSplit = !cfg.autoSplit; cfg.save(); refresh(); }, x2, y, half, h);
        y += step;

        addDrawableChild(ButtonWidget.builder(Text.literal("21 modunu sıfırla (eski haline döndür)"), b -> {
            m.reset21(client);
            status = "21 modunda değişen her şey gerçek haline döndü.";
            refresh();
        }).dimensions(x, y, w, h).build());
    }

    // ---------------------------------------------------------------- ortak

    private static String onOff(boolean b) {
        return b ? "Açık" : "Kapalı";
    }

    private void refresh() {
        for (int i = 0; i < dynButtons.size(); i++) dynButtons.get(i).setMessage(dynTexts.get(i).get());
    }

    private Text keyLabel(int which, String label, int code) {
        if (waitingFor == which) return Text.literal("> Tuşa bas <").formatted(Formatting.YELLOW);
        return Text.literal(label).append(keyName(code));
    }

    private static Text keyName(int code) {
        if (code == InputUtil.UNKNOWN_KEY.getCode()) return Text.literal("Yok");
        return InputUtil.Type.KEYSYM.createFromCode(code).getLocalizedText();
    }

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
                switch (waitingFor) {
                    case 1 -> cfg.armKey = code;
                    case 2 -> cfg.resetKey = code;
                    case 3 -> cfg.toggle21Key = code;
                    case 4 -> cfg.reset21Key = code;
                    default -> { }
                }
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

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        super.render(ctx, mouseX, mouseY, delta);
        int cx = this.width / 2;
        Config cfg = Config.get();
        DisguiseManager m = DisguiseManager.INSTANCE;

        ctx.drawCenteredTextWithShadow(textRenderer, this.title, cx, 6, 0xFFFFFFFF);

        if (cfg.menuPage == 1) {
            MutableText saved = Text.literal("Kayıtlı: ");
            for (int d = 1; d <= 9; d++) {
                saved.append(Text.literal(Config.cardName(d) + " ")
                        .formatted(cfg.hasCard(d) ? Formatting.GREEN : Formatting.RED));
            }
            ctx.drawCenteredTextWithShadow(textRenderer, saved, cx, 18, 0xFFFFFFFF);

            String got = m.received21.isEmpty() ? "-"
                    : m.received21.stream().map(Config::cardName).collect(Collectors.joining(", "));
            String plan;
            List<Integer> seq = m.getSeq21();
            if (seq == null) {
                plan = m.received21.size() < 2 ? "2. kart bekleniyor" : "tuşa basınca";
            } else {
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < seq.size(); i++) {
                    if (i > 0) sb.append("+");
                    String n = Config.cardName(seq.get(i));
                    sb.append(i < m.getSeq21Pos() ? "(" + n + ")" : n);
                }
                plan = sb + "  [" + m.getSeq21Options() + " yol]";
            }
            ctx.drawCenteredTextWithShadow(textRenderer,
                    Text.literal("Gelenler: " + got + "   →   Plan: " + plan), cx, 30, 0xFFFFFF55);
        } else {
            MutableText saved = Text.literal("Kayıtlı: ");
            for (int d = 1; d <= 9; d++) {
                saved.append(Text.literal(d + " ").formatted(cfg.hasDigit(d) ? Formatting.GREEN : Formatting.RED));
            }
            ctx.drawCenteredTextWithShadow(textRenderer, saved, cx, 18, 0xFFFFFFFF);

            String got = m.received.isEmpty() ? "-"
                    : m.received.stream().map(String::valueOf).collect(Collectors.joining(", "));
            int t = m.computeTarget();
            String next = t == -1 ? "bekleniyor" : t == 0 ? "yok (9)" : String.valueOf(t);
            ctx.drawCenteredTextWithShadow(textRenderer,
                    Text.literal("Gelenler: " + got + "   →   Sıradaki: " + next), cx, 30, 0xFFFFFF55);
        }

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
