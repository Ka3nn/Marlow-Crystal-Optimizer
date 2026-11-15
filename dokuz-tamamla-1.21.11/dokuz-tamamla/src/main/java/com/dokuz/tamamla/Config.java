package com.dokuz.tamamla;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.item.ItemStack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

/** Kayıtlı rakam haritaları ve mod ayarları (config/dokuztamamla.json). */
public final class Config {
    private static final Logger LOG = LoggerFactory.getLogger("dokuztamamla");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static Config instance;

    public static Config get() {
        if (instance == null) instance = load();
        return instance;
    }

    /** Bir rakama (1-9) ait kayıtlı map art. */
    public static final class Entry {
        public int mapId = -1;
        public JsonElement stackJson; // itemin tam hali (isim, map id vb.)
        public byte[] colors;         // harita resmi (128x128)
        public byte scale;
        public ItemStack decoded;     // çalışma anında çözülmüş hali
        /** Harita (map art) mı; değilse isim + item türü ile tanınır. */
        public boolean isMap = true;
        public String matchItem;      // örn. "minecraft:paper"
        public String matchName;      // örn. "3.2"
    }

    /** Sahte görünümlü item frame'in kalıcı kaydı. */
    public static final class FrameRecord {
        public long pos;
        public int facing;
        public int realId;
        public int fake;
    }

    public final Entry[] entries = new Entry[10]; // 9 modu: index 1..9 = rakam 1..9
    /** 21 modu: index 1..9 = kart A(1/11), 3, 4, 5, 6, 7, 8, 9, 10. */
    public final Entry[] entries21 = new Entry[10];
    /** Düello: duel1[v] = "v.1" (1. dropper), duel2[v] = "v.2" (2. dropper), v = 1..9. */
    public final Entry[] duel1 = new Entry[10];
    public final Entry[] duel2 = new Entry[10];
    /** .2'nin lehine ayar, %0 (adil) ile %100 (.2 hep kazanır/berabere) arası. */
    public int duelPercent = 50;
    public int toggleDuelKey = org.lwjgl.glfw.GLFW.GLFW_KEY_L;
    public int resetDuelKey = -1;
    /** Dropper düzenleri: dropper konumu → 9 slotta en son görülen item anahtarı. */
    public final java.util.Map<Long, int[]> dropperLayouts = new java.util.HashMap<>();
    public final List<FrameRecord> frameRecords = new ArrayList<>();
    public boolean notify = true;
    public boolean autoReset = true;
    /** Sahte item aynı türden gerçek itemlerle birleşirse otomatik ayır. */
    public boolean autoSplit = true;
    /** 0 = otomatik (N. itemi 9'a tamamla), 1..9 = elle seçilen rakam. */
    public int mode = 0;
    /** Otomatik modda 9'a tamamlanacak itemin sırası (varsayılan 2.). */
    public int index = 2;
    /** "Sonraki itemi ayarla" tuşu (GLFW kodu). Varsayılan K. */
    public int armKey = org.lwjgl.glfw.GLFW.GLFW_KEY_K;
    /** "Turu sıfırla" tuşu (GLFW kodu). Varsayılan: yok. */
    public int resetKey = -1;
    /** 21 modu aç/kapa tuşu. Varsayılan J. */
    public int toggle21Key = org.lwjgl.glfw.GLFW.GLFW_KEY_J;
    /** 21 modu sıfırlama tuşu. Varsayılan: yok. */
    public int reset21Key = -1;
    /** Menüde açık olan sekme (0 = 9 modu, 1 = 21 modu). */
    public int menuPage = 0;

    /**
     * Sahte görünüm anahtarı: 1..9 = 9 modundaki rakam, 11..19 = 21 modundaki kart (10 + index).
     */
    public Entry entryFor(int key) {
        if (key >= 1 && key <= 9) return entries[key];
        if (key >= 11 && key <= 19) return entries21[key - 10];
        if (key >= 21 && key <= 29) return duel1[key - 20];
        if (key >= 31 && key <= 39) return duel2[key - 30];
        return null;
    }

    /** 21 modunda kart index'inin (1..9) adı. */
    public static String cardName(int idx) {
        return idx == 1 ? "A" : String.valueOf(idx + 1);
    }

    /** Bu harita 21 modunda hangi kart (1..9), değilse 0. */
    public int cardOf(int mapId) {
        if (mapId < 0) return 0;
        for (int d = 1; d <= 9; d++) {
            if (entries21[d] != null && entries21[d].mapId == mapId) return d;
        }
        return 0;
    }

    public boolean hasCard(int d) {
        return d >= 1 && d <= 9 && entries21[d] != null;
    }

    /** Düello: bu anahtar hangi item, 10*taraf + değer (11..19 = v.1, 21..29 = v.2), değilse 0. */
    public int duelOf(int key) {
        if (key < 0) return 0;
        for (int v = 1; v <= 9; v++) {
            if (duel1[v] != null && duel1[v].mapId == key) return 10 + v;
            if (duel2[v] != null && duel2[v].mapId == key) return 20 + v;
        }
        return 0;
    }

    public int duelCount() {
        int n = 0;
        for (int v = 1; v <= 9; v++) {
            if (duel1[v] != null) n++;
            if (duel2[v] != null) n++;
        }
        return n;
    }

    /** Bu anahtara sahip kaydın görünüm anahtarı (1..39), yoksa 0. */
    public int entryKeyOf(int key) {
        for (int k = 1; k <= 39; k++) {
            Entry e = entryFor(k);
            if (e != null && e.mapId == key) return k;
        }
        return 0;
    }

    /** Herhangi bir modda kayıtlı mı. */
    public boolean isRegistered(int mapId) {
        return digitOf(mapId) != 0 || cardOf(mapId) != 0 || duelOf(mapId) != 0;
    }

    public int digitOf(int mapId) {
        if (mapId < 0) return 0;
        for (int d = 1; d <= 9; d++) {
            if (entries[d] != null && entries[d].mapId == mapId) return d;
        }
        return 0;
    }

    public boolean hasDigit(int d) {
        return d >= 1 && d <= 9 && entries[d] != null;
    }

    public FrameRecord findFrameRecord(long pos, int facing, int realId) {
        for (FrameRecord r : frameRecords) {
            if (r.pos == pos && r.facing == facing && r.realId == realId) return r;
        }
        return null;
    }

    public void putFrameRecord(long pos, int facing, int realId, int fake) {
        frameRecords.removeIf(r -> r.pos == pos && r.facing == facing);
        FrameRecord r = new FrameRecord();
        r.pos = pos;
        r.facing = facing;
        r.realId = realId;
        r.fake = fake;
        frameRecords.add(r);
    }

    public boolean removeFrameRecord(long pos, int facing) {
        return frameRecords.removeIf(r -> r.pos == pos && r.facing == facing);
    }

    private static Path path() {
        return FabricLoader.getInstance().getConfigDir().resolve("dokuztamamla.json");
    }

    private static Config load() {
        Config c = new Config();
        try {
            Path p = path();
            if (Files.exists(p)) {
                JsonObject root = JsonParser.parseString(Files.readString(p, StandardCharsets.UTF_8)).getAsJsonObject();
                c.readFrom(root);
            }
        } catch (Exception e) {
            LOG.error("Dokuz Tamamla ayarları okunamadı", e);
        }
        return c;
    }

    public void save() {
        try {
            Path p = path();
            Files.createDirectories(p.getParent());
            Files.writeString(p, GSON.toJson(toJson()), StandardCharsets.UTF_8);
        } catch (Exception e) {
            LOG.error("Dokuz Tamamla ayarları kaydedilemedi", e);
        }
    }

    private static int intOr(JsonObject o, String k, int def) {
        return o.has(k) ? o.get(k).getAsInt() : def;
    }

    private static boolean boolOr(JsonObject o, String k, boolean def) {
        return o.has(k) ? o.get(k).getAsBoolean() : def;
    }

    private static void readEntries(JsonObject root, String prefix, Entry[] target) {
        for (int d = 1; d <= 9; d++) {
            String key = prefix + d;
            if (!root.has(key)) continue;
            JsonObject c = root.getAsJsonObject(key);
            Entry e = new Entry();
            e.mapId = intOr(c, "id", -1);
            if (c.has("stack")) e.stackJson = c.get("stack");
            if (c.has("colors")) e.colors = Base64.getDecoder().decode(c.get("colors").getAsString());
            e.scale = (byte) intOr(c, "scale", 0);
            e.isMap = boolOr(c, "isMap", true);
            if (c.has("matchItem")) e.matchItem = c.get("matchItem").getAsString();
            if (c.has("matchName")) e.matchName = c.get("matchName").getAsString();
            target[d] = e;
        }
    }

    private static void writeEntries(JsonObject root, String prefix, Entry[] source) {
        for (int d = 1; d <= 9; d++) {
            Entry e = source[d];
            if (e == null) continue;
            JsonObject c = new JsonObject();
            c.addProperty("id", e.mapId);
            if (e.stackJson != null) c.add("stack", e.stackJson);
            if (e.colors != null) c.addProperty("colors", Base64.getEncoder().encodeToString(e.colors));
            c.addProperty("scale", e.scale);
            c.addProperty("isMap", e.isMap);
            if (e.matchItem != null) c.addProperty("matchItem", e.matchItem);
            if (e.matchName != null) c.addProperty("matchName", e.matchName);
            root.add(prefix + d, c);
        }
    }

    private void readFrom(JsonObject root) {
        readEntries(root, "d", entries);
        readEntries(root, "c", entries21);
        readEntries(root, "x", duel1);
        readEntries(root, "y", duel2);
        duelPercent = Math.max(0, Math.min(100, intOr(root, "duelPercent", 50)));
        toggleDuelKey = intOr(root, "toggleDuelKey", org.lwjgl.glfw.GLFW.GLFW_KEY_L);
        resetDuelKey = intOr(root, "resetDuelKey", -1);
        if (root.has("dropperLayouts")) {
            for (JsonElement el : root.getAsJsonArray("dropperLayouts")) {
                JsonObject c = el.getAsJsonObject();
                int[] keys = new int[9];
                JsonArray arr = c.getAsJsonArray("keys");
                for (int i = 0; i < 9 && i < arr.size(); i++) keys[i] = arr.get(i).getAsInt();
                dropperLayouts.put(c.get("pos").getAsLong(), keys);
            }
        }
        toggle21Key = intOr(root, "toggle21Key", org.lwjgl.glfw.GLFW.GLFW_KEY_J);
        reset21Key = intOr(root, "reset21Key", -1);
        menuPage = intOr(root, "menuPage", 0);
        notify = boolOr(root, "notify", true);
        autoReset = boolOr(root, "autoReset", true);
        autoSplit = boolOr(root, "autoSplit", true);
        mode = intOr(root, "mode", 0);
        index = Math.max(1, intOr(root, "index", 2));
        armKey = intOr(root, "armKey", org.lwjgl.glfw.GLFW.GLFW_KEY_K);
        resetKey = intOr(root, "resetKey", -1);
        if (root.has("frames")) {
            for (JsonElement el : root.getAsJsonArray("frames")) {
                JsonObject c = el.getAsJsonObject();
                FrameRecord r = new FrameRecord();
                r.pos = c.get("pos").getAsLong();
                r.facing = intOr(c, "facing", 0);
                r.realId = intOr(c, "real", -1);
                r.fake = intOr(c, "fake", 0);
                frameRecords.add(r);
            }
        }
    }

    private JsonObject toJson() {
        JsonObject root = new JsonObject();
        writeEntries(root, "d", entries);
        writeEntries(root, "c", entries21);
        writeEntries(root, "x", duel1);
        writeEntries(root, "y", duel2);
        root.addProperty("duelPercent", duelPercent);
        root.addProperty("toggleDuelKey", toggleDuelKey);
        root.addProperty("resetDuelKey", resetDuelKey);
        JsonArray layouts = new JsonArray();
        for (java.util.Map.Entry<Long, int[]> en : dropperLayouts.entrySet()) {
            JsonObject c = new JsonObject();
            c.addProperty("pos", en.getKey());
            JsonArray arr = new JsonArray();
            for (int k : en.getValue()) arr.add(k);
            c.add("keys", arr);
            layouts.add(c);
        }
        root.add("dropperLayouts", layouts);
        root.addProperty("toggle21Key", toggle21Key);
        root.addProperty("reset21Key", reset21Key);
        root.addProperty("menuPage", menuPage);
        root.addProperty("notify", notify);
        root.addProperty("autoReset", autoReset);
        root.addProperty("autoSplit", autoSplit);
        root.addProperty("mode", mode);
        root.addProperty("index", index);
        root.addProperty("armKey", armKey);
        root.addProperty("resetKey", resetKey);
        JsonArray list = new JsonArray();
        for (FrameRecord r : frameRecords) {
            JsonObject c = new JsonObject();
            c.addProperty("pos", r.pos);
            c.addProperty("facing", r.facing);
            c.addProperty("real", r.realId);
            c.addProperty("fake", r.fake);
            list.add(c);
        }
        root.add("frames", list);
        return root;
    }
}
