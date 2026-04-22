package com.tfcwildfire.wildfire;

import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.Level;

import java.util.HashMap;
import java.util.Map;

/**
 * Yangın front'u büyüdükçe (çok sayıda aktif ateş tick'i) sistem "apocalypse" moduna yaklaşır:
 * - spread/embers/crown güçlenir
 * - bazı durumlarda (hafif yağmur / orta nem) bile yangın kolay kolay durmaz
 *
 * Not: Buradaki sayım "bu tick'te kaç kez FireBlock tick'i işledik" ölçüsüdür.
 * Bu, yangın alanı büyüdükçe doğal olarak artar ve gerçekçi bir proxy olur.
 */
public final class WildfireFrontTracker {

    private static final Map<ResourceKey<Level>, Integer> fireTicksThisTick = new HashMap<>();
    private static final Map<ResourceKey<Level>, Double> sumX = new HashMap<>();
    private static final Map<ResourceKey<Level>, Double> sumY = new HashMap<>();
    private static final Map<ResourceKey<Level>, Double> sumZ = new HashMap<>();
    private static final Map<ResourceKey<Level>, Float> multiplier = new HashMap<>();
    private static final Map<ResourceKey<Level>, Float> pressure = new HashMap<>();
    private static final Map<ResourceKey<Level>, Vec3> center = new HashMap<>();

    private WildfireFrontTracker() {}

    public static void recordFireTick(ServerLevel level, net.minecraft.core.BlockPos pos) {
        ResourceKey<Level> key = level.dimension();
        fireTicksThisTick.merge(key, 1, Integer::sum);
        sumX.merge(key, pos.getX() + 0.5d, Double::sum);
        sumY.merge(key, pos.getY() + 0.5d, Double::sum);
        sumZ.merge(key, pos.getZ() + 0.5d, Double::sum);
    }

    /** 1.0..~6.0 arası */
    public static float getMultiplier(ServerLevel level) {
        return multiplier.getOrDefault(level.dimension(), 1.0f);
    }

    /** 0..1 arası (kolay kullanım) */
    public static float getPressure(ServerLevel level) {
        return pressure.getOrDefault(level.dimension(), 0.0f);
    }

    public static Vec3 getFrontCenter(ServerLevel level) {
        return center.get(level.dimension());
    }

    /** Server tick sonunda çağrılır: sayacı sıfırlar ve yeni multiplier hesaplar. */
    public static void postServerTick(MinecraftServer server) {
        for (ServerLevel level : server.getAllLevels()) {
            ResourceKey<Level> key = level.dimension();
            int count = fireTicksThisTick.getOrDefault(key, 0);

            // Exponential-ish: yangın büyüdükçe hızlı artar ama sonsuza gitmesin.
            // 0 -> 1.0, 50 -> ~2.0, 200 -> ~3.0, 800 -> ~4.3, 2000 -> ~5.2
            float m = 1.0f + (float) (Math.log1p(count) / 2.7);
            if (m > 6.0f) m = 6.0f;

            multiplier.put(key, m);
            float p = (m - 1.0f) / 5.0f; // 0..1
            if (p < 0f) p = 0f;
            if (p > 1f) p = 1f;
            pressure.put(key, p);

            if (count > 0) {
                center.put(key, new Vec3(
                    sumX.getOrDefault(key, 0.0d) / count,
                    sumY.getOrDefault(key, 0.0d) / count,
                    sumZ.getOrDefault(key, 0.0d) / count
                ));
            } else {
                center.remove(key);
            }

            // reset
            fireTicksThisTick.put(key, 0);
            sumX.put(key, 0.0d);
            sumY.put(key, 0.0d);
            sumZ.put(key, 0.0d);
        }
    }
}
