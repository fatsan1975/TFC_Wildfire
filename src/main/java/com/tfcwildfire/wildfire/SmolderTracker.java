package com.tfcwildfire.wildfire;

import it.unimi.dsi.fastutil.longs.Long2FloatOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongIterator;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * "Smoldering" (içten içe tüten) alanlar:
 * - Ateş söndükten sonra bir süre daha duman çıkarır.
 * - Eğer tekrar kurursa / rüzgar artarsa spot fire üretebilir.
 */
public final class SmolderTracker extends SavedData {

    private static final String DATA_NAME = "tfc_wildfire_smolder";

    // posLong -> expireGameTime
    private final Long2LongOpenHashMap expireByPos = new Long2LongOpenHashMap();
    // posLong -> strength 0..1
    private final Long2FloatOpenHashMap strengthByPos = new Long2FloatOpenHashMap();

    private SmolderTracker() {}

    private static SmolderTracker load(CompoundTag tag, HolderLookup.Provider registries) {
        SmolderTracker t = new SmolderTracker();

        ListTag list = tag.getList("smolders", 10);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag c = list.getCompound(i);
            long p = c.getLong("p");
            t.expireByPos.put(p, c.getLong("e"));
            t.strengthByPos.put(p, c.getFloat("s"));
        }

        return t;
    }

    private static final Factory<SmolderTracker> FACTORY =
            new Factory<>(SmolderTracker::new, SmolderTracker::load, null);

    public static SmolderTracker get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(FACTORY, DATA_NAME);
    }

    public void add(ServerLevel level, BlockPos pos, float strength01) {
        long p = pos.asLong();
        long now = level.getGameTime();

        float prev = strengthByPos.getOrDefault(p, 0f);
        float merged = Mth.clamp(Math.max(prev, strength01), 0f, 1f);

        // 10 dakika + strength'a göre biraz daha uzun
        long ttl = 20L * 60L * 10L + (long) (merged * 20L * 60L * 6L);

        expireByPos.put(p, now + ttl);
        strengthByPos.put(p, merged);
        setDirty();
    }

    /** Her tick az sayıda entry işleyerek performansı koruyoruz. */
    public void tick(ServerLevel level) {
        long now = level.getGameTime();
        RandomSource rnd = level.random;

        int processed = 0;
        LongIterator it = expireByPos.keySet().iterator();
        while (it.hasNext() && processed < 40) {
            long key = it.nextLong();
            long expire = expireByPos.get(key);
            if (now >= expire) {
                it.remove();
                strengthByPos.remove(key);
                setDirty();
                continue;
            }

            BlockPos pos = BlockPos.of(key);
            float s = strengthByPos.getOrDefault(key, 0f);

            // yağmur smolder'ı boğar
            if (level.isRainingAt(pos.above())) {
                s -= 0.03f;
            } else {
                s -= 0.004f;
            }
            s = Mth.clamp(s, 0f, 1f);
            strengthByPos.put(key, s);

            // duman efektleri (yakın oyuncular görür)
            if (rnd.nextFloat() < 0.7f) {
                int count = 1 + (int) (s * 4);
                level.sendParticles(ParticleTypes.SMOKE, pos.getX() + 0.5, pos.getY() + 0.9, pos.getZ() + 0.5, count, 0.25, 0.15, 0.25, 0.01);
                if (rnd.nextFloat() < s * 0.6f) {
                    level.sendParticles(ParticleTypes.ASH, pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5, 1, 0.3, 0.2, 0.3, 0.01);
                }
            }

            // tekrar tutuşma şansı: sadece çok kuru ise
            var climate = TFCClimateAdapter.sample(level, pos);
            float rain01 = TFCClimateAdapter.rain01(level, pos, climate);

            float baseDry = TFCClimateAdapter.baseDryness01(climate);
            float wetness = MoistureTracker.getWetness(level, level.getChunkAt(pos).getPos(), rain01, climate.avgTempC(), climate.wind01());

            float dryness = net.minecraft.util.Mth.clamp(baseDry * (1f - wetness), 0f, 1f);

            if (!level.isRainingAt(pos.above()) && dryness > 0.83f && rnd.nextFloat() < (0.0018f * s)) {
                WildfireEngine.tryIgnite(level, pos.above(), dryness, 0.6f + 0.4f * s);
                // tutuştuktan sonra smolder biraz düşsün
                strengthByPos.put(key, s * 0.55f);
                setDirty();
            }

            processed++;
        }
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();

        LongIterator it = expireByPos.keySet().iterator();
        while (it.hasNext()) {
            long p = it.nextLong();
            CompoundTag c = new CompoundTag();
            c.putLong("p", p);
            c.putLong("e", expireByPos.get(p));
            c.putFloat("s", strengthByPos.getOrDefault(p, 0f));
            list.add(c);
        }

        tag.put("smolders", list);
        return tag;
    }
}
