package com.tfcwildfire.wildfire;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec2;

/**
 * Wildfire motoru (v0.2):
 * - Nem (MoistureTracker) + yakıt (FuelCalculator) + rüzgar (WindModel) ile "intensity" hesaplar
 * - Ember density: intensity'ye göre downwind (rüzgar yönünde) spot fire üretir
 * - Crown fire: canopy varsa ve intensity yüksekse üst katmana da spot fire üretir
 * - Smoldering: ateş söndüğünde pos'u SmolderTracker'a ekler, duman + tekrar tutuşma şansı
 * - Torch: yere atılan torch item'ı belli süre kalırsa tutuşma denemesi
 */
public final class WildfireEngine {

    // Ember sayısını kısıtlayalım (performans)
    private static final int MAX_EMBERS_PER_FIRE_TICK = 10;

    private WildfireEngine() {}

    /** FireBlock tick'inin sonunda çağrılır (mixin). */
    public static void afterFireBlockTick(ServerLevel level, BlockPos firePos, RandomSource random) {
        if (level.isClientSide) return;

        // Nem + yakıt + rüzgar
        var climate = TFCClimateAdapter.sample(level, firePos);
        float rain01 = TFCClimateAdapter.rain01(level, firePos, climate);

        float baseDry = TFCClimateAdapter.baseDryness01(climate);
        float wetness = MoistureTracker.getWetness(level, level.getChunkAt(firePos).getPos(), rain01, climate.avgTempC(), climate.wind01());

        float dryness = net.minecraft.util.Mth.clamp(baseDry * (1f - wetness), 0f, 1f);

        float fuel = FuelCalculator.fuelLoad(level, firePos);
        WindModel.Wind wind = WindModel.get(level, firePos);

        // yağmur varsa intensity düşsün
        float rainDamp = level.isRainingAt(firePos.above()) ? 0.25f : 1.0f;

        float intensity = Mth.clamp(dryness * fuel * (1f + 1.25f * wind.speed01()) * rainDamp, 0f, 1f);

        // Duman / kor parçacıkları
        if (random.nextFloat() < 0.85f * intensity) {
            int smoke = 1 + (int) (intensity * 5);
            level.sendParticles(ParticleTypes.LARGE_SMOKE, firePos.getX() + 0.5, firePos.getY() + 0.9, firePos.getZ() + 0.5, smoke, 0.35, 0.25, 0.35, 0.005);
        }
        if (random.nextFloat() < 0.55f * intensity) {
            level.sendParticles(ParticleTypes.ASH, firePos.getX() + 0.5, firePos.getY() + 1.0, firePos.getZ() + 0.5, 1, 0.25, 0.2, 0.25, 0.01);
        }

        // Ember density = intensity^2
        float emberDensity = intensity * intensity;

        // Crown fire: canopy varsa ember'lar yukarı da sıçrasın
        boolean canopy = FuelCalculator.hasCanopy(level, firePos);
        boolean crown = canopy && intensity > 0.72f;

        // Ember üretimi
        int embers = (int) Mth.clamp(Math.floor(emberDensity * (MAX_EMBERS_PER_FIRE_TICK + 2)), 0, MAX_EMBERS_PER_FIRE_TICK);
        if (crown) embers = Math.min(MAX_EMBERS_PER_FIRE_TICK, embers + 2);

        for (int i = 0; i < embers; i++) {
            spawnEmber(level, firePos, wind.dir(), wind.speed01(), dryness, intensity, crown);
        }

        // Ateş sönerse: smolder bırak
        if (!level.getBlockState(firePos).is(Blocks.FIRE)) {
            SmolderTracker.get(level).add(level, firePos, Mth.clamp(0.35f + 0.65f * intensity, 0f, 1f));
        }
    }

    /** Server tick ile smolder + torch gibi şeyleri işliyoruz. */
    public static void serverTick(ServerLevel level) {
        if (level.isClientSide) return;

        // her tick değil, 2 tickte bir (performans)
        if ((level.getGameTime() & 1L) == 0L) {
            SmolderTracker.get(level).tick(level);
            TorchIgnitionTracker.tick(level);
        }
    }

    private static void spawnEmber(ServerLevel level, BlockPos from, Vec2 windDir, float windSpeed, float dryness, float intensity, boolean crown) {
        RandomSource rnd = level.random;

        // mesafe: rüzgarla artar
        int minD = 6;
        int maxD = 12 + (int) (windSpeed * 28f);
        int dist = minD + rnd.nextInt(Math.max(1, maxD - minD + 1));

        // küçük sapma
        float jitter = 0.35f + 0.6f * (1f - windSpeed);

        double dx = windDir.x * dist + rnd.nextGaussian() * jitter * 3.0;
        double dz = windDir.y * dist + rnd.nextGaussian() * jitter * 3.0;

        int dy = 0;
        if (crown) dy = 3 + rnd.nextInt(4);      // canopy'ye sıçrayan korlar yukarı taşınsın
        else if (rnd.nextFloat() < 0.25f) dy = 1;

        BlockPos target = from.offset(Mth.floor(dx), dy, Mth.floor(dz));

        // Çok uzağa gitmesin diye küçük clamp (performans/gariplik önleme)
        if (from.distManhattan(target) > 48) return;

        // Spot fire dene
        float strength = 0.55f + 0.45f * intensity;
        tryIgnite(level, target, dryness, strength);
    }

    /**
     * Yangını başlatmaya çalışır:
     * - Hedef pozisyon hava olmalı
     * - Altındaki blok yanıcı olmalı
     * - Yağmurda tutuşma çok zor (burada direkt veto)
     */
    public static boolean tryIgnite(ServerLevel level, BlockPos pos, float dryness, float strength01) {
        if (!level.hasChunkAt(pos)) return false;
        if (level.isRainingAt(pos)) return false;

        BlockState here = level.getBlockState(pos);
        if (!here.isAir()) {
            // Eğer burası doluysa, üstünü dene
            pos = pos.above();
            here = level.getBlockState(pos);
            if (!here.isAir()) return false;
        }

        BlockPos belowPos = pos.below();
        BlockState below = level.getBlockState(belowPos);

        if (!below.isFlammable(level, belowPos, Direction.UP)) return false;

        // Çok ıslaksa hiç yakma
        if (dryness < 0.75f) return false;

        // strength ile ekstra şans: güçlü ember daha kolay yakar
        float chance = Mth.clamp((dryness - 0.72f) * 1.2f + (strength01 - 0.5f) * 0.6f, 0f, 1f);

        if (level.random.nextFloat() > chance) return false;

        BlockState fireState = BaseFireBlock.getState(level, pos);
        level.setBlock(pos, fireState, 11);
        return true;
    }
}
