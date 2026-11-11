package com.dokuz.tamamla;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Kayıtlı rakam haritaları ve mod ayarları (config/dokuztamamla.nbt). */
public final class Config {
    private static final Logger LOG = LoggerFactory.getLogger("dokuztamamla");
    private static Config instance;

    public static Config get() {
        if (instance == null) instance = load();
        return instance;
    }

    /** Bir rakama (1-9) ait kayıtlı map art. */
    public static final class Entry {
        public int mapId = -1;
        public NbtElement stackNbt;   // itemin tam hali (isim, map id vb.)
        public byte[] colors;         // harita resmi (128x128), yüklü değilse tekrar gösterebilmek için
        public byte scale;
        public ItemStack decoded;     // çalışma anında çözülmüş hali
    }

    /** Sahte görünümlü item frame'in kalıcı kaydı (oyuna tekrar girince de sürsün diye). */
    public static final class FrameRecord {
        public long pos;
        public int facing;
        public int realId;
        public int fake;
    }

    public final Entry[] entries = new Entry[10]; // index 1..9
    public final List<FrameRecord> frameRecords = new ArrayList<>();
    public boolean notify = true;
    public boolean autoReset = true;
    /** 0 = otomatik (N. itemi 9'a tamamla), 1..9 = elle seçilen rakam. */
    public int mode = 0;
    /** Otomatik modda 9'a tamamlanacak itemin sırası (varsayılan 2.). */
    public int index = 2;
    /** "Sonraki itemi ayarla" tuşu (GLFW kodu). Mod menüsünden değiştirilir. Varsayılan K. */
    public int armKey = org.lwjgl.glfw.GLFW.GLFW_KEY_K;
    /** "Turu sıfırla" tuşu (GLFW kodu). Mod menüsünden değiştirilir. Varsayılan: yok. */
    public int resetKey = -1;

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
        return FabricLoader.getInstance().getConfigDir().resolve("dokuztamamla.nbt");
    }

    private static Config load() {
        Config c = new Config();
        try {
            Path p = path();
            if (Files.exists(p)) {
                NbtCompound root = NbtIo.read(p);
                if (root != null) c.readFrom(root);
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
            NbtIo.write(toNbt(), p);
        } catch (Exception e) {
            LOG.error("Dokuz Tamamla ayarları kaydedilemedi", e);
        }
    }

    private void readFrom(NbtCompound root) {
        for (int d = 1; d <= 9; d++) {
            String key = "d" + d;
            if (!root.contains(key)) continue;
            NbtCompound c = root.getCompound(key);
            Entry e = new Entry();
            e.mapId = c.getInt("id");
            if (c.contains("stack")) e.stackNbt = c.get("stack");
            if (c.contains("colors")) e.colors = c.getByteArray("colors");
            e.scale = c.getByte("scale");
            entries[d] = e;
        }
        if (root.contains("notify")) notify = root.getBoolean("notify");
        if (root.contains("autoReset")) autoReset = root.getBoolean("autoReset");
        if (root.contains("mode")) mode = root.getInt("mode");
        if (root.contains("armKey")) armKey = root.getInt("armKey");
        if (root.contains("resetKey")) resetKey = root.getInt("resetKey");
        if (root.contains("index")) index = Math.max(1, root.getInt("index"));
        NbtList list = root.getList("frames", NbtElement.COMPOUND_TYPE);
        for (int i = 0; i < list.size(); i++) {
            NbtCompound c = list.getCompound(i);
            FrameRecord r = new FrameRecord();
            r.pos = c.getLong("pos");
            r.facing = c.getInt("facing");
            r.realId = c.getInt("real");
            r.fake = c.getInt("fake");
            frameRecords.add(r);
        }
    }

    private NbtCompound toNbt() {
        NbtCompound root = new NbtCompound();
        for (int d = 1; d <= 9; d++) {
            Entry e = entries[d];
            if (e == null) continue;
            NbtCompound c = new NbtCompound();
            c.putInt("id", e.mapId);
            if (e.stackNbt != null) c.put("stack", e.stackNbt);
            if (e.colors != null) c.putByteArray("colors", e.colors);
            c.putByte("scale", e.scale);
            root.put("d" + d, c);
        }
        root.putBoolean("notify", notify);
        root.putBoolean("autoReset", autoReset);
        root.putInt("mode", mode);
        root.putInt("index", index);
        root.putInt("armKey", armKey);
        root.putInt("resetKey", resetKey);
        NbtList list = new NbtList();
        for (FrameRecord r : frameRecords) {
            NbtCompound c = new NbtCompound();
            c.putLong("pos", r.pos);
            c.putInt("facing", r.facing);
            c.putInt("real", r.realId);
            c.putInt("fake", r.fake);
            list.add(c);
        }
        root.put("frames", list);
        return root;
    }
}
