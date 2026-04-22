package com.tfcwildfire.wildfire;

import com.tfcwildfire.config.WildfireConfig;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.saveddata.SavedData;

public final class HotspotTracker {
    private static final String DATA_NAME = "tfcwildfire_hotspots";

    private HotspotTracker() {}

    // ✅ SİZDE EKSİKMİŞ
    public static void addHeat(ServerLevel level, ChunkPos cp, float add) {
        if (!WildfireConfig.SERVER.smolderEnabled.get()) return;
        HotspotData.get(level).addHeat(level.getGameTime(), cp, add);
    }

    // ✅ SİZDE EKSİKMİŞ
    public static void processSmolder(ServerLevel level) {
        if (!WildfireConfig.SERVER.smolderEnabled.get()) return;
        HotspotData.get(level).decayOnly(level.getGameTime());
        // burada istersen ileride “yeniden tutuşma”yı da koyarız,
        // şimdilik compile fix + heat sistemi hazır.
    }

    private static final class HotspotData extends SavedData {
        private final Long2ObjectOpenHashMap<Entry> entries = new Long2ObjectOpenHashMap<>();

        private static final class Entry {
            float heat;
            long lastTick;
        }

        static HotspotData get(ServerLevel level) {
            return level.getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(HotspotData::new, HotspotData::load, null),
                DATA_NAME
            );
        }

        static HotspotData load(CompoundTag tag, HolderLookup.Provider regs) {
            HotspotData d = new HotspotData();
            ListTag list = tag.getList("chunks", CompoundTag.TAG_COMPOUND);
            for (int i = 0; i < list.size(); i++) {
                CompoundTag c = list.getCompound(i);
                long k = c.getLong("k");
                Entry e = new Entry();
                e.heat = c.getFloat("h");
                e.lastTick = c.getLong("t");
                d.entries.put(k, e);
            }
            return d;
        }

        @Override
        public CompoundTag save(CompoundTag tag, HolderLookup.Provider regs) {
            ListTag list = new ListTag();
            for (var it = entries.long2ObjectEntrySet().fastIterator(); it.hasNext();) {
                var ent = it.next();
                long k = ent.getLongKey();
                Entry e = ent.getValue();

                CompoundTag c = new CompoundTag();
                c.putLong("k", k);
                c.putFloat("h", e.heat);
                c.putLong("t", e.lastTick);
                list.add(c);
            }
            tag.put("chunks", list);
            return tag;
        }

        void addHeat(long now, ChunkPos cp, float add) {
            Entry e = entries.get(cp.toLong());
            if (e == null) {
                e = new Entry();
                e.heat = 0f;
                e.lastTick = now;
                entries.put(cp.toLong(), e);
            }

            decayOne(now, e);

            float heatMax = (float) WildfireConfig.SERVER.heatMax.get().doubleValue();
            e.heat = Mth.clamp(e.heat + add, 0f, heatMax);
            e.lastTick = now;
            setDirty();
        }

        void decayOnly(long now) {
            float decay = (float) WildfireConfig.SERVER.heatDecayPerSecond.get().doubleValue();
            var it = entries.long2ObjectEntrySet().fastIterator();
            while (it.hasNext()) {
                var ent = it.next();
                Entry e = ent.getValue();

                long dt = now - e.lastTick;
                if (dt > 0) {
                    float seconds = dt / 20f;
                    e.heat = Math.max(0f, e.heat - decay * seconds);
                    e.lastTick = now;
                }

                if (e.heat <= 0.0001f) it.remove();
            }
            setDirty();
        }

        private void decayOne(long now, Entry e) {
            long dt = now - e.lastTick;
            if (dt <= 0) return;

            float seconds = dt / 20f;
            float decay = (float) WildfireConfig.SERVER.heatDecayPerSecond.get().doubleValue();
            e.heat = Math.max(0f, e.heat - decay * seconds);
        }
    }
}
