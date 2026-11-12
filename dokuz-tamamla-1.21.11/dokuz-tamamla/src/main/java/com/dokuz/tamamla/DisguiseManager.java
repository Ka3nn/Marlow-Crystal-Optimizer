package com.dokuz.tamamla;

import com.mojang.serialization.JsonOps;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
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
import net.minecraft.screen.Generic3x3ContainerScreenHandler;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.math.Box;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Tüm mantık burada. "Sahte item" = gerçekte X rakamı olan ama SADECE bu ekranda Y rakamı gibi
 * görünen tek bir item. Bu item nereye giderse (hotbar, envanter, el, item frame, frame'den geri
 * alınınca yine envanter) Y gibi görünür. Dropper ekranında da sayılar buna göre gösterilir:
 * eksik olan item Y gibi görünür. Sıfırlama tuşu her şeyi gerçek haline döndürür.
 * Gerçek itemlere (dropper içi, sunucu) hiçbir zaman dokunulmaz.
 */
public final class DisguiseManager {
    public static final DisguiseManager INSTANCE = new DisguiseManager();

    private static final int OFFHAND = 36;
    private static final int CURSOR = 37;
    private static final int POSITIONS = 38; // 0-35 envanter, 36 sol el, 37 imleç

    /** Envanterdeki (veya imleçteki) bir sahte item. */
    private static final class InvToken {
        ItemStack ref;
        int pos;
        final int realId;
        final int fake;
        boolean intoFrame;
        ItemStack visual;
        int visualCount;

        InvToken(ItemStack ref, int pos, int realId, int fake) {
            this.ref = ref;
            this.pos = pos;
            this.realId = realId;
            this.fake = fake;
        }
    }

    /** Item frame'deki sahte item. */
    private static final class FrameDisguise {
        final int realId;
        final int fake;
        boolean provisional;
        final long created;
        final long pos;
        ItemStack visual;

        FrameDisguise(int realId, int fake, boolean provisional, long created, long pos) {
            this.realId = realId;
            this.fake = fake;
            this.provisional = provisional;
            this.created = created;
            this.pos = pos;
        }
    }

    /** Envanterden/frame'den çıkıp yerde duran (ya da frame'e girmek üzere olan) sahte item. */
    private static final class Pending {
        final int realId;
        final int fake;
        final long tick;
        /** true: frame'e konmuş olabilir (frame eşleşmesi denenir). false: sadece yerden geri alınma. */
        final boolean maybeFrame;

        Pending(int realId, int fake, long tick, boolean maybeFrame) {
            this.realId = realId;
            this.fake = fake;
            this.tick = tick;
            this.maybeFrame = maybeFrame;
        }
    }

    public final List<Integer> received = new ArrayList<>();
    private boolean armed;
    private int armedDigit;

    private final List<InvToken> tokens = new ArrayList<>();
    private final Map<Integer, FrameDisguise> frames = new HashMap<>();
    private final List<Pending> pendings = new ArrayList<>();
    private final Map<Integer, Integer> frameSeen = new HashMap<>();
    private final List<long[]> frameChanges = new ArrayList<>(); // {entityId, mapId, tick}

    private int[] lastIds;
    private int[] lastCounts;
    private Map<Integer, Integer> lastTotals = new HashMap<>();
    private long tick;
    private ClientWorld lastWorld;

    /** drawSlot sırasında çizilen slot (HandledScreenMixin doldurur). */
    public Slot currentSlot;

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

    private static ItemStack realStack(ItemFrameEntity f) {
        return ((FrameRealStackAccess) f).dokuz$realStack();
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

    private InvToken tokenFor(ItemStack s) {
        if (s == null || tokens.isEmpty()) return null;
        for (InvToken t : tokens) {
            if (t.ref == s) return t;
        }
        return null;
    }

    private ItemStack tokenVisual(InvToken t, int count, ItemStack fallback) {
        if (t.visual == null || t.visualCount != count) {
            ItemStack v = makeVisual(t.fake, count);
            if (v == null) return fallback;
            t.visual = v;
            t.visualCount = count;
        }
        return t.visual;
    }

    /** Hotbar / envanter / tooltip / item adı için. */
    public ItemStack inventoryVisual(ItemStack s) {
        InvToken t = tokenFor(s);
        return t == null ? s : tokenVisual(t, s.getCount(), s);
    }

    /** Elde tutulan (birinci şahıs) harita için. */
    public ItemStack heldVisual(ItemStack s) {
        if (tokens.isEmpty() || s == null || s.isEmpty()) return s;
        InvToken t = tokenFor(s);
        if (t != null) return tokenVisual(t, s.getCount(), s);
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) return s;
        for (ItemStack hand : new ItemStack[]{mc.player.getMainHandStack(), mc.player.getOffHandStack()}) {
            InvToken ht = tokenFor(hand);
            if (ht != null && ItemStack.areItemsAndComponentsEqual(s, hand)) return tokenVisual(ht, s.getCount(), s);
        }
        return s;
    }

    /**
     * Ekrandaki bir slotun göstereceği item. Oyuncu envanteri → sahte görünüm.
     * Dropper/dispenser → sayılar, "eksik olan item seçtiğin rakammış" gibi görünecek şekilde.
     */
    public ItemStack slotVisual(Slot slot, ItemStack s) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (slot == null || mc.player == null) return inventoryVisual(s);
        if (slot.inventory == mc.player.getInventory()) return inventoryVisual(s);
        if (!(mc.player.currentScreenHandler instanceof Generic3x3ContainerScreenHandler)) return s;

        Map<Integer, Integer> delta = countDeltas();
        if (delta.isEmpty()) return s;
        int id = rawMapId(s);
        Integer d = id == -1 ? null : delta.get(id);
        if (d == null || d == 0) return s;
        // Bu rakamın dropper'daki ilk slotu mu? Ayarlama sadece ilk slota uygulanır.
        ScreenHandler h = mc.player.currentScreenHandler;
        for (Slot other : h.slots) {
            if (other.inventory == mc.player.getInventory()) continue;
            if (rawMapId(other.getStack()) == id) {
                if (other != slot) return s;
                break;
            }
        }
        int n = s.getCount() + d;
        return n <= 0 ? ItemStack.EMPTY : s.copyWithCount(n);
    }

    /** Dışarıdaki her sahte item için: gerçek rakam +1, gösterilen rakam -1. */
    private Map<Integer, Integer> countDeltas() {
        Map<Integer, Integer> delta = new HashMap<>();
        Config cfg = Config.get();
        List<int[]> out = new ArrayList<>();
        for (InvToken t : tokens) out.add(new int[]{t.realId, t.fake});
        for (FrameDisguise f : frames.values()) out.add(new int[]{f.realId, f.fake});
        for (Pending p : pendings) out.add(new int[]{p.realId, p.fake});
        for (int[] o : out) {
            if (!cfg.hasDigit(o[1])) continue;
            int fakeId = cfg.entries[o[1]].mapId;
            if (fakeId == o[0]) continue;
            delta.merge(o[0], 1, Integer::sum);
            delta.merge(fakeId, -1, Integer::sum);
        }
        return delta;
    }

    /** Item frame'lerde kontrol edilecek bir şey var mı (yoksa hiç uğraşılmaz). */
    public boolean hasFrameDisguises() {
        return !frames.isEmpty() || !tokens.isEmpty();
    }

    /** Item frame'in göstereceği sahte item; yoksa null (gerçeği gösterilir). */
    public ItemStack frameVisual(ItemFrameEntity f, ItemStack real) {
        FrameDisguise d = frames.get(f.getId());
        if (d == null) d = claimFrameNow(f, real);
        if (d == null) return null;
        return frameVisualFor(d, real);
    }

    /**
     * Elindeki sahte item bu frame'e az önce konduysa, frame'i aynı karede sahte yapar
     * (sunucu güncellemesi envanter güncellemesinden önce gelirse bile gerçek hali görünmez).
     */
    private FrameDisguise claimFrameNow(ItemFrameEntity f, ItemStack real) {
        int id = rawMapId(real);
        if (id == -1 || tokens.isEmpty()) return null;
        Integer seen = frameSeen.get(f.getId());
        if (seen != null && seen == id) return null; // bu frame'de zaten vardı → gerçek item
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) return null;
        for (InvToken t : tokens) {
            if (t.realId != id) continue;
            if (t.ref != mc.player.getMainHandStack() && t.ref != mc.player.getOffHandStack()) continue;
            addFrame(f, id, t.fake, false);
            if (!mc.player.getAbilities().creativeMode) t.intoFrame = true;
            frameSeen.put(f.getId(), id);
            return frames.get(f.getId());
        }
        return null;
    }

    private ItemStack frameVisualFor(FrameDisguise d, ItemStack real) {
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

    /** Her şeyi gerçek haline döndürür (sıfırlama tuşu). */
    public void clearAll() {
        resetRound();
        tokens.clear();
        frames.clear();
        pendings.clear();
        frameChanges.clear();
        lastIds = null;
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
        for (InvToken t : tokens) t.visual = null;
        lastIds = null; // yeni kayıttan sonra sahte "geldi" algılanmasın
        if (missing == 0) return "Kaydedildi: slot 1-9 → rakam 1-9";
        return "Kaydedildi. " + missing + " haritanın resmi yüklenmemiş, onları bir kez eline al.";
    }

    // ------------------------------------------------------------------ item frame'e koyma (anlık tahmin)

    public void onUseFrame(PlayerEntity player, Hand hand, ItemFrameEntity f) {
        InvToken t = tokenFor(player.getStackInHand(hand));
        if (t == null) return;
        if (!realStack(f).isEmpty()) return;
        addFrame(f, t.realId, t.fake, true);
        if (!player.getAbilities().creativeMode) t.intoFrame = true;
    }

    private void addFrame(ItemFrameEntity f, int realId, int fake, boolean provisional) {
        frames.put(f.getId(), new FrameDisguise(realId, fake, provisional, tick, f.getBlockPos().asLong()));
        Config cfg = Config.get();
        cfg.putFrameRecord(f.getBlockPos().asLong(), facing(f), realId, fake);
        cfg.save();
    }

    // ------------------------------------------------------------------ her tick

    private void resetRuntime() {
        tokens.clear();
        pendings.clear();
        armed = false;
        frames.clear();
        frameSeen.clear();
        frameChanges.clear();
        lastIds = null;
        lastTotals = new HashMap<>();
        Config cfg = Config.get();
        for (int d = 1; d <= 9; d++) {
            if (cfg.entries[d] != null) cfg.entries[d].decoded = null;
        }
    }

    /** Saniyede 20 kez (oyun güncellemesi). */
    public void tick(MinecraftClient mc) {
        tick++;
        if (mc.world != null && tick % 20 == 0) captureMissingColors(mc);
        update(mc);
    }

    /**
     * Envanter ve item frame kontrolü. Hem her oyun güncellemesinde hem de her kare çizilmeden
     * hemen önce çağrılır; böylece gelen item hiçbir karede gerçek haliyle görünmez.
     * Aynı karede iki kez çağrılması sorun değildir (değişiklik yoksa hiçbir şey yapmaz).
     */
    public void update(MinecraftClient mc) {
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

        if (lastIds == null) {
            snapshot(ids, counts, totals);
            scanFrames(mc, cfg);
            return;
        }

        boolean containerOpen = p.currentScreenHandler != p.playerScreenHandler;

        // 1) Sahte itemleri envanterde takip et (taşınırsa peşinden git)
        Set<Integer> claimed = new HashSet<>();
        for (InvToken t : new ArrayList<>(tokens)) {
            int found = -1;
            for (int i = 0; i < POSITIONS; i++) {
                if (stacks[i] == t.ref && !claimed.contains(i)) { found = i; break; }
            }
            if (found == -1 && t.pos >= 0 && ids[t.pos] == t.realId && !claimed.contains(t.pos)) found = t.pos;
            if (found == -1) {
                int before = lastTotals.getOrDefault(t.realId, 0);
                int now = totals.getOrDefault(t.realId, 0);
                if (now > 0 && now >= before) {
                    if (ids[CURSOR] == t.realId && lastIds[CURSOR] != t.realId && !claimed.contains(CURSOR)) {
                        found = CURSOR;
                    } else {
                        for (int i = 0; i < POSITIONS; i++) {
                            if (ids[i] == t.realId && lastIds[i] != t.realId && !claimed.contains(i)) { found = i; break; }
                        }
                    }
                }
            }
            if (found >= 0) {
                t.ref = stacks[found];
                t.pos = found;
                claimed.add(found);
            } else {
                tokens.remove(t);
                // Dropper/sandık açıkken kaybolduysa içine konmuştur → gerçek haline döner.
                if (!t.intoFrame && !containerOpen) {
                    pendings.add(new Pending(t.realId, t.fake, tick, true));
                }
            }
        }

        // 2) Yeni gelen itemleri algıla
        for (Map.Entry<Integer, Integer> en : totals.entrySet()) {
            int id = en.getKey();
            int delta = en.getValue() - lastTotals.getOrDefault(id, 0);
            if (delta <= 0) continue;
            int digit = cfg.digitOf(id);
            for (int k = 0; k < delta; k++) {
                int pos = newStackPos(id, ids, counts, stacks, claimed);

                // Yerden / frame'den geri alınan sahte item → yine sahte görünsün.
                Pending back = null;
                for (Pending pe : pendings) {
                    if (pe.realId == id && (back == null || pe.tick > back.tick)) back = pe;
                }
                if (back != null && pos >= 0 && !containerOpen) {
                    pendings.remove(back);
                    tokens.add(new InvToken(stacks[pos], pos, id, back.fake));
                    claimed.add(pos);
                    continue;
                }

                if (armed && pos >= 0) {
                    tokens.add(new InvToken(stacks[pos], pos, id, armedDigit));
                    claimed.add(pos);
                    armed = false;
                    ensureMapState(armedDigit);
                    if (cfg.autoReset) received.clear();
                    else received.add(armedDigit);
                    notify(mc, "✔ " + armedDigit);
                    continue;
                }
                received.add(digit);
            }
        }

        // 3) Item frame'leri tara
        scanFrames(mc, cfg);

        // 4) Envanterden kaybolan sahte item bir frame'e girdiyse eşle
        frameChanges.removeIf(c -> tick - c[2] > 60);
        for (Pending pe : new ArrayList<>(pendings)) {
            if (!pe.maybeFrame || tick - pe.tick > 40) continue;
            long[] best = null;
            for (long[] c : frameChanges) {
                if (c[1] != pe.realId || frames.containsKey((int) c[0])) continue;
                if (Math.abs(c[2] - pe.tick) > 40) continue;
                if (best == null || Math.abs(c[2] - pe.tick) < Math.abs(best[2] - pe.tick)) best = c;
            }
            if (best != null) {
                Entity e = mc.world.getEntityById((int) best[0]);
                if (e instanceof ItemFrameEntity f) addFrame(f, pe.realId, pe.fake, false);
                frameChanges.remove(best);
                pendings.remove(pe);
            }
        }
        // Yerde çok uzun kalan sahte item unutulur (5 dakika).
        pendings.removeIf(pe -> tick - pe.tick > 6000);

        snapshot(ids, counts, totals);
    }

    /** Bu tick'te yeni gelen (veya sayısı artan) ve henüz sahiplenilmemiş pozisyon. */
    private int newStackPos(int id, int[] ids, int[] counts, ItemStack[] stacks, Set<Integer> claimed) {
        for (int i = 0; i < POSITIONS; i++) {
            if (claimed.contains(i)) continue;
            if (ids[i] == id && (lastIds[i] != id || counts[i] > lastCounts[i]) && tokenFor(stacks[i]) == null) return i;
        }
        return -1;
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
            int id = rawMapId(realStack(f));
            Integer prev = frameSeen.put(eid, id);
            if (prev != null && prev != id && id != -1 && cfg.digitOf(id) != 0) {
                frameChanges.add(new long[]{eid, id, tick});
                // Elindeki sahte item bu frame'e kondu → hemen sahte yap (bekleme yok).
                if (!frames.containsKey(eid)) {
                    for (InvToken t : tokens) {
                        if (t.realId != id) continue;
                        if (t.ref != mc.player.getMainHandStack() && t.ref != mc.player.getOffHandStack()) continue;
                        addFrame(f, id, t.fake, false);
                        if (!mc.player.getAbilities().creativeMode) t.intoFrame = true;
                        break;
                    }
                }
            }
            FrameDisguise d = frames.get(eid);
            if (d != null) {
                if (id == d.realId) {
                    d.provisional = false;
                } else if (id != -1 || !d.provisional || tick - d.created > 60) {
                    frames.remove(eid);
                    if (cfg.removeFrameRecord(f.getBlockPos().asLong(), facing(f))) changed = true;
                    // Frame'den çıkarıldıysa: yerden geri alınınca yine sahte görünsün.
                    if (id == -1 && !d.provisional) {
                        pendings.add(new Pending(d.realId, d.fake, tick, false));
                    }
                }
            } else if (id != -1) {
                Config.FrameRecord r = cfg.findFrameRecord(f.getBlockPos().asLong(), facing(f), id);
                if (r != null) frames.put(eid, new FrameDisguise(id, r.fake, false, tick, f.getBlockPos().asLong()));
            }
        }
        // Frame yakındayken kırıldıysa (entity yok oldu): item yere düşmüştür.
        for (Map.Entry<Integer, FrameDisguise> en : new ArrayList<>(frames.entrySet())) {
            FrameDisguise d = en.getValue();
            if (mc.world.getEntityById(en.getKey()) != null) continue;
            if (mc.player.getBlockPos().getSquaredDistance(net.minecraft.util.math.BlockPos.fromLong(d.pos)) > 8 * 8) continue;
            frames.remove(en.getKey());
            Config.FrameRecord removed = null;
            for (Config.FrameRecord r : cfg.frameRecords) {
                if (r.pos == d.pos && r.realId == d.realId) { removed = r; break; }
            }
            if (removed != null) { cfg.frameRecords.remove(removed); changed = true; }
            if (!d.provisional) pendings.add(new Pending(d.realId, d.fake, tick, false));
        }
        if (changed) cfg.save();
    }
}
