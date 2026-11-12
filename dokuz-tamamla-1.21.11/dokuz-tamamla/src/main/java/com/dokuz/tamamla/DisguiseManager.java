package com.dokuz.tamamla;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import com.mojang.serialization.JsonOps;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.MapIdComponent;
import net.minecraft.entity.Entity;
import net.minecraft.entity.decoration.ItemFrameEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.FilledMapItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.map.MapState;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.math.Box;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Tüm mantık burada:
 *  - Envantere giren kayıtlı map art'ları algılar (gelen rakamlar listesi).
 *  - Tuşa basılınca bir sonraki gelen itemi SADECE bu istemcide başka rakam gibi gösterir.
 *  - O item hotbar/envanter/elde/item frame'de aynı sahte görünümle kalır.
 *  Gerçek itemlere (dropper içi, sunucu) hiçbir zaman dokunulmaz.
 */
public final class DisguiseManager {
    public static final DisguiseManager INSTANCE = new DisguiseManager();

    private static final int OFFHAND = 36;
    private static final int CURSOR = 37;
    private static final int POSITIONS = 38; // 0-35 envanter, 36 sol el, 37 imleç

    public final List<Integer> received = new ArrayList<>();
    private boolean armed;
    private int armedDigit;

    // Envanterde takip edilen sahte item
    private ItemStack trackedRef;
    private int trackedPos = -1;
    private int trackedRealId = -1;
    private int trackedFake;
    private boolean trackedIntoFrame;
    private ItemStack invVisual;
    private int invVisualDigit;
    private int invVisualCount;

    // Item frame'ler
    private static final class FrameDisguise {
        final int realId;
        final int fake;
        boolean provisional;
        final long created;
        ItemStack visual;

        FrameDisguise(int realId, int fake, boolean provisional, long created) {
            this.realId = realId;
            this.fake = fake;
            this.provisional = provisional;
            this.created = created;
        }
    }

    private final Map<Integer, FrameDisguise> frames = new HashMap<>();
    private final Map<Integer, Integer> frameSeen = new HashMap<>();
    private final List<long[]> frameChanges = new ArrayList<>(); // {entityId, mapId, tick}
    private int pendingRealId = -1;
    private int pendingFake;
    private long pendingTick;

    private int[] lastIds;
    private int[] lastCounts;
    private Map<Integer, Integer> lastTotals = new HashMap<>();
    private long tick;
    private ClientWorld lastWorld;
    private int suppressTicks;

    private DisguiseManager() {}

    // ------------------------------------------------------------------ yardımcılar

    public static int rawMapId(ItemStack s) {
        if (s == null || s.isEmpty() || !s.isOf(Items.FILLED_MAP)) return -1;
        MapIdComponent c = s.get(DataComponentTypes.MAP_ID);
        return c == null ? -1 : c.id();
    }

    private static ItemStack stackAt(ClientPlayerEntity p, int pos) {
        if (pos < 36) return p.getInventory().getStack(pos);
        if (pos == OFFHAND) return p.getOffHandStack();
        return p.currentScreenHandler.getCursorStack();
    }

    private static int facing(ItemFrameEntity f) {
        return f.getHorizontalFacing().ordinal();
    }

    public boolean isArmed() { return armed; }
    public int getArmedDigit() { return armedDigit; }

    public void notify(MinecraftClient mc, String msg) {
        if (Config.get().notify && mc.inGameHud != null) {
            mc.inGameHud.setOverlayMessage(Text.literal(msg), false);
        }
    }

    // ------------------------------------------------------------------ şablon & harita resmi

    public ItemStack template(int d) {
        Config cfg = Config.get();
        if (!cfg.hasDigit(d)) return null;
        Config.Entry e = cfg.entries[d];
        if (e.decoded == null) {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc.world != null && e.stackJson != null) {
                try {
                    e.decoded = ItemStack.CODEC
                            .parse(mc.world.getRegistryManager().getOps(JsonOps.INSTANCE), e.stackJson)
                            .result().orElse(null);
                } catch (Exception ignored) {
                    e.decoded = null;
                }
            }
            if (e.decoded == null || e.decoded.isEmpty()) {
                ItemStack s = new ItemStack(Items.FILLED_MAP);
                s.set(DataComponentTypes.MAP_ID, new MapIdComponent(e.mapId));
                e.decoded = s;
            }
        }
        return e.decoded;
    }

    /** Sahte rakamın harita resmi istemcide yüklü değilse kayıtlı renklerden oluşturur. */
    private void ensureMapState(int d) {
        MinecraftClient mc = MinecraftClient.getInstance();
        Config cfg = Config.get();
        if (mc.world == null || !cfg.hasDigit(d)) return;
        Config.Entry e = cfg.entries[d];
        MapIdComponent id = new MapIdComponent(e.mapId);
        if (mc.world.getMapState(id) != null || e.colors == null) return;
        MapState st = MapState.of(e.scale, true, mc.world.getRegistryKey());
        System.arraycopy(e.colors, 0, st.colors, 0, Math.min(e.colors.length, st.colors.length));
        mc.world.putClientsideMapState(id, st);
    }

    private ItemStack makeVisual(int digit, int count) {
        ItemStack tpl = template(digit);
        if (tpl == null) return null;
        ensureMapState(digit);
        return tpl.copyWithCount(Math.max(1, count));
    }

    private void captureMissingColors(MinecraftClient mc) {
        Config cfg = Config.get();
        boolean changed = false;
        for (int d = 1; d <= 9; d++) {
            Config.Entry e = cfg.entries[d];
            if (e == null || e.colors != null) continue;
            MapState st = mc.world.getMapState(new MapIdComponent(e.mapId));
            if (st != null) {
                e.colors = st.colors.clone();
                e.scale = st.scale;
                changed = true;
            }
        }
        if (changed) cfg.save();
    }

    // ------------------------------------------------------------------ render tarafı

    /** Hotbar / envanter / tooltip / item adı için. */
    public ItemStack inventoryVisual(ItemStack s) {
        if (trackedRef == null || s != trackedRef) return s;
        return invVisual(s.getCount(), s);
    }

    /** Elde tutulan (birinci şahıs) harita için. */
    public ItemStack heldVisual(ItemStack s) {
        if (trackedRef == null || s == null || s.isEmpty()) return s;
        if (s == trackedRef) return invVisual(s.getCount(), s);
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player != null
                && (mc.player.getMainHandStack() == trackedRef || mc.player.getOffHandStack() == trackedRef)
                && ItemStack.areItemsAndComponentsEqual(s, trackedRef)) {
            return invVisual(s.getCount(), s);
        }
        return s;
    }

    private ItemStack invVisual(int count, ItemStack fallback) {
        if (invVisual == null || invVisualDigit != trackedFake || invVisualCount != count) {
            ItemStack v = makeVisual(trackedFake, count);
            if (v == null) return fallback;
            invVisual = v;
            invVisualDigit = trackedFake;
            invVisualCount = count;
        }
        return invVisual;
    }

    public boolean hasFrameDisguises() {
        return !frames.isEmpty();
    }

    /** Item frame'in göstereceği sahte item; yoksa null (gerçeği gösterilir). */
    public ItemStack frameVisual(ItemFrameEntity f, ItemStack real) {
        if (frames.isEmpty()) return null;
        FrameDisguise d = frames.get(f.getId());
        if (d == null) return null;
        if (real.isEmpty()) {
            if (!d.provisional) return null;
        } else if (rawMapId(real) != d.realId) {
            return null;
        }
        if (d.visual == null) {
            d.visual = makeVisual(d.fake, 1);
            if (d.visual == null) return null;
        }
        return d.visual;
    }

    // ------------------------------------------------------------------ kullanıcı işlemleri

    public int computeTarget() {
        Config cfg = Config.get();
        if (cfg.mode >= 1 && cfg.mode <= 9) return cfg.mode;
        int i = cfg.index - 1;
        if (received.size() <= i) return -1;
        int t = 9 - received.get(i);
        return (t >= 1 && t <= 9) ? t : 0;
    }

    public void toggleArm(MinecraftClient mc) {
        if (armed) {
            armed = false;
            notify(mc, "İptal edildi");
            return;
        }
        int t = computeTarget();
        Config cfg = Config.get();
        if (t == -1) {
            notify(mc, "Henüz " + cfg.index + ". item gelmedi");
        } else if (t == 0) {
            notify(mc, cfg.index + ". item 9, 9'a tamamlanamaz");
        } else if (template(t) == null) {
            notify(mc, t + " rakamı kayıtlı değil (menüden kaydet)");
        } else {
            armed = true;
            armedDigit = t;
            notify(mc, "Hazır → sıradaki: " + t);
        }
    }

    public void resetRound() {
        received.clear();
        armed = false;
    }

    public void clearAll() {
        resetRound();
        clearTracked();
        frames.clear();
        pendingRealId = -1;
        Config cfg = Config.get();
        cfg.frameRecords.clear();
        cfg.save();
    }

    public String saveHotbar(MinecraftClient mc) {
        if (mc.player == null || mc.world == null) return "Önce dünyaya gir.";
        PlayerInventory inv = mc.player.getInventory();
        int[] ids = new int[10];
        for (int i = 0; i < 9; i++) {
            int id = rawMapId(inv.getStack(i));
            if (id == -1) return "Slot " + (i + 1) + " dolu bir harita (map art) değil.";
            for (int j = 1; j <= i; j++) {
                if (ids[j] == id) return "Slot " + (i + 1) + " ve slot " + j + " aynı harita.";
            }
            ids[i + 1] = id;
        }
        Config cfg = Config.get();
        int missing = 0;
        for (int d = 1; d <= 9; d++) {
            ItemStack s = inv.getStack(d - 1);
            Config.Entry e = new Config.Entry();
            e.mapId = ids[d];
            e.stackJson = ItemStack.CODEC
                    .encodeStart(mc.world.getRegistryManager().getOps(JsonOps.INSTANCE), s.copyWithCount(1))
                    .result().orElse(null);
            MapState st = FilledMapItem.getMapState(new MapIdComponent(ids[d]), mc.world);
            if (st != null) {
                e.colors = st.colors.clone();
                e.scale = st.scale;
            } else {
                missing++;
            }
            cfg.entries[d] = e;
        }
        cfg.save();
        invVisual = null;
        lastIds = null; // yeni kayıttan sonra sahte "geldi" algılanmasın
        if (missing == 0) return "Kaydedildi: slot 1-9 → rakam 1-9";
        return "Kaydedildi. " + missing + " haritanın resmi yüklenmemiş, onları bir kez eline al.";
    }

    // ------------------------------------------------------------------ item frame'e koyma (anlık tahmin)

    public void onUseFrame(PlayerEntity player, Hand hand, ItemFrameEntity f) {
        if (trackedRef == null) return;
        if (player.getStackInHand(hand) != trackedRef) return;
        if (!((FrameRealStackAccess) f).dokuz$realStack().isEmpty()) return;
        addFrame(f, trackedRealId, trackedFake, true);
        if (!player.getAbilities().creativeMode) trackedIntoFrame = true;
    }

    private void addFrame(ItemFrameEntity f, int realId, int fake, boolean provisional) {
        frames.put(f.getId(), new FrameDisguise(realId, fake, provisional, tick));
        Config cfg = Config.get();
        cfg.putFrameRecord(f.getBlockPos().asLong(), facing(f), realId, fake);
        cfg.save();
    }

    // ------------------------------------------------------------------ her tick

    private void clearTracked() {
        trackedRef = null;
        trackedPos = -1;
        trackedRealId = -1;
        trackedIntoFrame = false;
        invVisual = null;
    }

    private void resetRuntime() {
        clearTracked();
        armed = false;
        frames.clear();
        frameSeen.clear();
        frameChanges.clear();
        pendingRealId = -1;
        lastIds = null;
        lastTotals = new HashMap<>();
        Config cfg = Config.get();
        for (int d = 1; d <= 9; d++) {
            if (cfg.entries[d] != null) cfg.entries[d].decoded = null;
        }
    }

    public void tick(MinecraftClient mc) {
        tick++;
        ClientPlayerEntity p = mc.player;
        if (p == null || mc.world == null) {
            if (lastWorld != null) resetRuntime();
            lastWorld = null;
            return;
        }
        if (mc.world != lastWorld) {
            resetRuntime();
            lastWorld = mc.world;
        }
        Config cfg = Config.get();
        if (tick % 20 == 0) captureMissingColors(mc);

        ItemStack[] stacks = new ItemStack[POSITIONS];
        int[] ids = new int[POSITIONS];
        int[] counts = new int[POSITIONS];
        Map<Integer, Integer> totals = new HashMap<>();
        for (int i = 0; i < POSITIONS; i++) {
            ItemStack s = stackAt(p, i);
            stacks[i] = s;
            int id = rawMapId(s);
            if (id != -1 && cfg.digitOf(id) == 0) id = -1;
            ids[i] = id;
            counts[i] = id == -1 ? 0 : s.getCount();
            if (id != -1) totals.merge(id, counts[i], Integer::sum);
        }

        if (lastIds == null || suppressTicks > 0) {
            if (suppressTicks > 0) suppressTicks--;
            snapshot(ids, counts, totals);
            scanFrames(mc, cfg);
            return;
        }

        // 1) Sahte itemi envanterde takip et (taşınırsa peşinden git)
        if (trackedRef != null) {
            int found = -1;
            for (int i = 0; i < POSITIONS; i++) {
                if (stacks[i] == trackedRef) { found = i; break; }
            }
            if (found == -1 && trackedPos >= 0 && ids[trackedPos] == trackedRealId) found = trackedPos;
            if (found == -1) {
                int before = lastTotals.getOrDefault(trackedRealId, 0);
                int now = totals.getOrDefault(trackedRealId, 0);
                if (now > 0 && now >= before) {
                    if (ids[CURSOR] == trackedRealId && lastIds[CURSOR] != trackedRealId) {
                        found = CURSOR;
                    } else {
                        for (int i = 0; i < POSITIONS; i++) {
                            if (ids[i] == trackedRealId && lastIds[i] != trackedRealId) { found = i; break; }
                        }
                    }
                }
            }
            if (found >= 0) {
                trackedRef = stacks[found];
                trackedPos = found;
            } else {
                if (!trackedIntoFrame) {
                    pendingRealId = trackedRealId;
                    pendingFake = trackedFake;
                    pendingTick = tick;
                }
                clearTracked();
            }
        }

        // 2) Yeni gelen itemleri algıla
        for (Map.Entry<Integer, Integer> en : totals.entrySet()) {
            int id = en.getKey();
            int delta = en.getValue() - lastTotals.getOrDefault(id, 0);
            if (delta <= 0) continue;
            int digit = cfg.digitOf(id);
            for (int k = 0; k < delta; k++) {
                if (armed) {
                    int pos = -1;
                    for (int i = 0; i < POSITIONS; i++) {
                        if (ids[i] == id && (lastIds[i] != id || counts[i] > lastCounts[i]) && stacks[i] != trackedRef) {
                            pos = i;
                            break;
                        }
                    }
                    if (pos >= 0) {
                        clearTracked();
                        trackedRef = stacks[pos];
                        trackedPos = pos;
                        trackedRealId = id;
                        trackedFake = armedDigit;
                        armed = false;
                        ensureMapState(armedDigit);
                        if (cfg.autoReset) received.clear();
                        else received.add(armedDigit);
                        notify(mc, "✔ " + armedDigit);
                        continue;
                    }
                }
                received.add(digit);
            }
        }

        // 3) Item frame'leri tara
        scanFrames(mc, cfg);

        // 4) Envanterden kaybolan sahte item bir frame'e girdiyse eşle
        frameChanges.removeIf(c -> tick - c[2] > 60);
        if (pendingRealId != -1) {
            if (tick - pendingTick > 60) {
                pendingRealId = -1;
            } else {
                long[] best = null;
                for (long[] c : frameChanges) {
                    if (c[1] != pendingRealId || frames.containsKey((int) c[0])) continue;
                    if (Math.abs(c[2] - pendingTick) > 40) continue;
                    if (best == null || Math.abs(c[2] - pendingTick) < Math.abs(best[2] - pendingTick)) best = c;
                }
                if (best != null) {
                    Entity e = mc.world.getEntityById((int) best[0]);
                    if (e instanceof ItemFrameEntity f) addFrame(f, pendingRealId, pendingFake, false);
                    frameChanges.remove(best);
                    pendingRealId = -1;
                }
            }
        }

        snapshot(ids, counts, totals);
    }

    private void snapshot(int[] ids, int[] counts, Map<Integer, Integer> totals) {
        lastIds = ids;
        lastCounts = counts;
        lastTotals = totals;
    }

    private void scanFrames(MinecraftClient mc, Config cfg) {
        Box box = mc.player.getBoundingBox().expand(32);
        boolean changed = false;
        for (ItemFrameEntity f : mc.world.getEntitiesByClass(ItemFrameEntity.class, box, e -> true)) {
            int eid = f.getId();
            int id = rawMapId(((FrameRealStackAccess) f).dokuz$realStack());
            Integer prev = frameSeen.put(eid, id);
            if (prev != null && prev != id && id != -1 && cfg.digitOf(id) != 0) {
                frameChanges.add(new long[]{eid, id, tick});
            }
            FrameDisguise d = frames.get(eid);
            if (d != null) {
                if (id == d.realId) {
                    d.provisional = false;
                } else if (id != -1 || !d.provisional || tick - d.created > 60) {
                    frames.remove(eid);
                    if (cfg.removeFrameRecord(f.getBlockPos().asLong(), facing(f))) changed = true;
                }
            } else if (id != -1) {
                Config.FrameRecord r = cfg.findFrameRecord(f.getBlockPos().asLong(), facing(f), id);
                if (r != null) frames.put(eid, new FrameDisguise(id, r.fake, false, tick));
            }
        }
        if (changed) cfg.save();
    }
}
