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

public final class MoistureTracker {
    private static final String DATA_NAME = "tfcwildfire_moisture";

    private MoistureTracker() {}

    // ✅ BU METOT SİZDE EKSİKMİŞ
    public static float getWetness(ServerLevel level, ChunkPos chunkPos, float rain01, float tempC, float wind01) {
        if (!WildfireConfig.SERVER.moistureEnabled.get()) return 0f;
        MoistureData data = MoistureData.get(level);
        return data.updateAndGet(level.getGameTime(), chunkPos, rain01, tempC, wind01);
    }

    private static final class MoistureData extends SavedData {
        private final Long2ObjectOpenHashMap<Entry> entries = new Long2ObjectOpenHashMap<>();

        private static final class Entry {
            float wet;       // 0..wetnessMax
            long lastTick;
            long rainStreak; // aralıksız yağmur süresi (tick)
        }

        static MoistureData get(ServerLevel level) {
            // DataFixTypes kullanmıyoruz (sende yoktu)
            return level.getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(MoistureData::new, MoistureData::load, null),
                DATA_NAME
            );
        }

        static MoistureData load(CompoundTag tag, HolderLookup.Provider regs) {
            MoistureData d = new MoistureData();
            ListTag list = tag.getList("chunks", CompoundTag.TAG_COMPOUND);

            for (int i = 0; i < list.size(); i++) {
                CompoundTag c = list.getCompound(i);
                long k = c.getLong("k");
                Entry e = new Entry();
                e.wet = c.getFloat("w");
                e.lastTick = c.getLong("t");
                e.rainStreak = c.getLong("rs");
                d.entries.put(k, e);
            }
            return d;
        }

        @Override
        public CompoundTag save(CompoundTag tag, HolderLookup.Provider regs) {
            ListTag list = new ListTag();
            for (var it = entries.long2ObjectEntrySet().fastIterator(); it.hasNext();) {
                var ent = it.next();
                long key = ent.getLongKey();
                Entry e = ent.getValue();

                CompoundTag c = new CompoundTag();
                c.putLong("k", key);
                c.putFloat("w", e.wet);
                c.putLong("t", e.lastTick);
                c.putLong("rs", e.rainStreak);
                list.add(c);
            }
            tag.put("chunks", list);
            return tag;
        }

        float updateAndGet(long now, ChunkPos chunkPos, float rain01, float tempC, float wind01) {
            long key = chunkPos.toLong();
            Entry e = entries.get(key);
            if (e == null) {
                e = new Entry();
                e.wet = 0f;
                e.lastTick = now;
                e.rainStreak = 0;
                entries.put(key, e);
                setDirty();
                return 0f;
            }

            long dt = now - e.lastTick;
            if (dt <= 0) return e.wet;

            float seconds = dt / 20f;
            float wetMax = (float) WildfireConfig.SERVER.wetnessMax.get().doubleValue();

            // yağmur: şiddet + süre birleşik
            if (rain01 > 0.01f) {
                e.rainStreak += dt;

                float streakMinutes = e.rainStreak / 1200f;
                float durationFactor = Mth.clamp(1f + 0.20f * streakMinutes, 1f, 3.5f);

                float add = (float) (WildfireConfig.SERVER.rainWettingRatePerSecond.get() * rain01 * durationFactor * seconds);
                e.wet = Mth.clamp(e.wet + add, 0f, wetMax);
            } else {
                e.rainStreak = 0;
            }

            // kuruma: sıcaklık + rüzgar hızlandırır
            float baseDry = (float) WildfireConfig.SERVER.baseDryingRatePerSecond.get().doubleValue();
            float tempFactor = 1f + (float) WildfireConfig.SERVER.tempDryingBonus.get().doubleValue()
                * Mth.clamp((tempC - 10f) / 25f, 0f, 1f);
            float windFactor = 1f + (float) WildfireConfig.SERVER.windDryingBonus.get().doubleValue()
                * Mth.clamp(wind01, 0f, 1f);

            float sub = baseDry * tempFactor * windFactor * seconds;
            e.wet = Mth.clamp(e.wet - sub, 0f, wetMax);

            e.lastTick = now;
            setDirty();
            return e.wet;
        }
    }
}
