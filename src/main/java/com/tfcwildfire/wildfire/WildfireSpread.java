package com.tfcwildfire.wildfire;

import com.tfcwildfire.config.WildfireConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec2;

public final class WildfireSpread {
    private WildfireSpread() {}

    public static void onFireTick(ServerLevel level, BlockPos firePos, RandomSource random) {
        if (!WildfireConfig.SERVER.enabled.get()) return;


        // Fire-front sayacı: bu tick'te kaç farklı ateş tick'i çalıştı?
        WildfireFrontTracker.recordFireTick(level, firePos);
        float frontMul = WildfireFrontTracker.getMultiplier(level);
        float frontPressure = WildfireFrontTracker.getPressure(level); // 0..1

        if (ClayPitProtectionTracker.shouldSuppressFireAt(level, firePos)) {
            level.removeBlock(firePos, false);
            return;
        }

        // Vanilla ateş (oyuncu çaktı / yıldırım) dahil: altındaki TFC çimleri yanmış çime dönüşsün.
        BurningBlockCompat.tryCharredGrassBelow(level, firePos);

        var climate = TFCClimateAdapter.sample(level, firePos);

        float rain01 = TFCClimateAdapter.rain01(level, firePos, climate);
        float baseDry = TFCClimateAdapter.baseDryness01(climate);

        ChunkPos cp = level.getChunkAt(firePos).getPos();
        float wetness = MoistureTracker.getWetness(level, cp, rain01, climate.avgTempC(), climate.wind01());

        float effectiveDry = Mth.clamp(baseDry * (1f - wetness), 0f, 1f);
        // Apocalypse: yangın front'u büyüdükçe (çok sayıda aktif ateş) etkili kuruluk artar.
        float effectiveDry2 = Mth.clamp(effectiveDry + 0.22f * frontPressure, 0f, 1f);
        float fuelLoad = FuelLoad.sample(level, firePos, random);

        // smolder heat biriktir (yanmış alan sıcak kalsın)
        float heatAdd = (float) WildfireConfig.SERVER.heatAddPerFireTick.get().doubleValue();
        heatAdd *= (0.25f + 0.75f * effectiveDry2) * (0.25f + 0.75f * fuelLoad);
        HotspotTracker.addHeat(level, cp, heatAdd);

        // parçacıklar
        if (WildfireConfig.SERVER.particlesEnabled.get()) {
            int smoke = WildfireConfig.SERVER.smokeParticles.get();
            int ember = WildfireConfig.SERVER.emberParticles.get();

            float intensity = (0.25f + 0.75f * effectiveDry2) * (0.35f + 0.65f * fuelLoad) * (1f + climate.wind01());
            int sCount = Mth.clamp((int) Math.round(smoke * intensity), 0, 80);
            int eCount = Mth.clamp((int) Math.round(ember * intensity), 0, 80);

            if (sCount > 0) level.sendParticles(ParticleTypes.SMOKE, firePos.getX() + 0.5, firePos.getY() + 0.7, firePos.getZ() + 0.5, sCount, 0.35, 0.25, 0.35, 0.0);
            if (eCount > 0) level.sendParticles(ParticleTypes.FLAME, firePos.getX() + 0.5, firePos.getY() + 0.2, firePos.getZ() + 0.5, eCount, 0.2, 0.1, 0.2, 0.01);
        }

        // yakın yayılım
        float chance = WildfireConfig.SERVER.baseSpreadChance.get().floatValue();
        chance *= (0.15f + 0.85f * effectiveDry);
        chance *= (1f + WildfireConfig.SERVER.windSpreadMultiplier.get().floatValue() * climate.wind01());
        chance *= (1f + WildfireConfig.SERVER.fuelLoadSpreadMultiplier.get().floatValue() * fuelLoad);

        // yağmur = sert fren
        chance *= (1f - 0.85f * rain01);
        chance = Mth.clamp(chance, 0f, 0.95f);

        if (random.nextFloat() < chance) {
            // Normal yayılım: vanilla gibi 1 blok komşu ile sınırlı kalmasın.
            // Kurak + sıcak koşullarda daha uzağa "atlayarak" (ground fire run) yayılabilsin.
            trySpreadToNeighbors(level, firePos, random);
            tryExtendedGroundSpread(level, firePos, random, effectiveDry2, climate.avgTempC(), fuelLoad, rain01, frontPressure, frontMul);
        }

        // ember spotting (yoğunluk sistemi)
        if (WildfireConfig.SERVER.emberEnabled.get()) {
            tryEmbers(level, firePos, random, climate.wind(), climate.wind01(), effectiveDry2, fuelLoad, rain01, frontPressure, frontMul);
        }

        // crown fire
        if (WildfireConfig.SERVER.crownEnabled.get()) {
            tryCrownFire(level, firePos, random, climate.wind(), climate.wind01(), effectiveDry2, fuelLoad, frontPressure, frontMul);
        }
    }

    private static void trySpreadToNeighbors(ServerLevel level, BlockPos firePos, RandomSource random) {
        for (Direction d : Direction.values()) {
            tryIgniteAir(level, firePos.relative(d), random);
        }
        int extra = WildfireConfig.SERVER.extraNeighborTries.get();
        for (int i = 0; i < extra; i++) {
            tryIgniteAir(level, firePos.relative(Direction.getRandom(random)), random);
        }
    }

    
/**
 * Ground fire run: çim/ot aralıklı olduğunda alevlerin 2-3 blok öteye "atlayabilmesini" sağlar.
 * Bu vanilla mekanizmayı tamamen değiştirmez; ek bir deneme katmanı ekler.
 */
private static void tryExtendedGroundSpread(ServerLevel level, BlockPos firePos, RandomSource random, float dry, float tempC, float fuel, float rain01, float frontPressure, float frontMul) {
    // Normalde yağmur ground spread'i keser; ama front çok büyüdüyse yağmur bile zor durdurur.
    if (rain01 > (0.15f + 0.18f * frontPressure)) return;
    // Baz radius: 2. Kurak + sıcak olunca 3'e çıkar.
    int radius = 2;
    if (dry > 0.70f) radius += 1;
    if (tempC > 25f) radius += 1;
    // Front büyüdüyse bir tık daha uzak oynayabilsin.
    radius = Mth.clamp(radius + (frontPressure > 0.65f ? 1 : 0), 2, 6);

    // Deneme sayısı: yakıt + kuruluk ile artar. (tehlikeli yapmak için yükselttik)
    int tries = 6 + (int) Math.floor(10f * dry) + (int) Math.floor(6f * fuel);
    tries = (int) Math.round(tries * Math.pow(frontMul, 1.15));
    tries = Mth.clamp(tries, 6, 60);

    // Kuruluk yükseldikçe başarı eşiği artar.
    float p = 0.22f + 0.58f * dry;
    p += 0.18f * frontPressure;
    p *= (0.75f + 0.25f * fuel);
    p *= (1f - 0.65f * rain01);
    p = Mth.clamp(p, 0f, 0.90f);

    if (random.nextFloat() > p) return;

    for (int i = 0; i < tries; i++) {
        int dx = Mth.nextInt(random, -radius, radius);
        int dz = Mth.nextInt(random, -radius, radius);
        if (dx == 0 && dz == 0) continue;

        // Hafif downwind bias (wind01 zaten chance içinde etkili ama burada da biraz olsun)
        // rüzgar modelini burada direkt kullanmıyoruz; basitçe daha uzak noktaları deniyoruz.
        BlockPos probe = firePos.offset(dx, 0, dz);

        // Zemine oturt
        BlockPos ground = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, probe);
        BlockPos air = ground.above();

        // Eğer hedefin altında charred zemin varsa, alev orada da yaşayabilsin (ileten blok).
        // Yoksa normal kurala göre yine flammable neighbor arayacak.
        tryIgniteAir(level, air, random);
    }
}

// --- PUBLIC yardımcılar (TorchIgnition kullanıyor) ---

    public static boolean tryIgniteAir(ServerLevel level, BlockPos airPos, RandomSource random) {
        if (!level.getBlockState(airPos).isAir()) return false;

        BlockState fireState = BaseFireBlock.getState(level, airPos);
        if (!fireState.canSurvive(level, airPos)) return false;

        if (!hasFlammableNeighbor(level, airPos)) return false;

        level.setBlock(airPos, fireState, 11);
        level.scheduleTick(airPos, fireState.getBlock(), 1);

        // TFC çim tabakası yanarken yok olmasın: burningblocktfc'nin charred grass'ına dönüştür.
        // Böylece yangın zemin üzerinden "koşar" ve arkada yanmış çim kalır.
        BurningBlockCompat.tryCharredGrassBelow(level, airPos);
        return true;
    }

    public static BlockPos findNearbyFireSpot(ServerLevel level, BlockPos center, int r) {
        BlockPos[] quick = new BlockPos[]{center, center.above(), center.north(), center.south(), center.east(), center.west()};
        for (BlockPos p : quick) if (canPlace(level, p)) return p;

        for (int dy = -1; dy <= 1; dy++) {
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    BlockPos p = center.offset(dx, dy, dz);
                    if (canPlace(level, p)) return p;
                }
            }
        }
        return null;
    }

    private static boolean canPlace(ServerLevel level, BlockPos p) {
        if (!level.getBlockState(p).isAir()) return false;
        BlockState fireState = BaseFireBlock.getState(level, p);
        return fireState.canSurvive(level, p) && hasFlammableNeighbor(level, p);
    }

    private static boolean hasFlammableNeighbor(ServerLevel level, BlockPos pos) {
    for (Direction d : Direction.values()) {
        BlockPos n = pos.relative(d);
        BlockState ns = level.getBlockState(n);
        boolean flammable = ns.isFlammable(level, n, d.getOpposite());
        if (flammable && ClayPitProtectionTracker.shouldSuppressGrassFlammability(level, n, ns)) {
            flammable = false;
        }
        if (flammable) return true;

        // burningblock / burningblocktfc charred blokları "yanmaz ama iletir":
        // ateş, etrafı tamamen kömürleşmiş olsa bile tekrar alevlenebilsin.
        if (BurningBlockCompat.isCharred(ns)) return true;
    }
    return false;
}



private static boolean isCanopyFuel(ServerLevel level, BlockPos pos, BlockState state) {
    if (state.is(BlockTags.LEAVES) || state.is(BlockTags.LOGS_THAT_BURN)) return true;
    // Registry path bazlı fallback (TFC dahil)
    var key = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(state.getBlock());
    if (key == null) return false;
    String path = key.getPath();
    if (path.contains("leaves") || path.contains("leaf")) return true;
    if (path.contains("log") || path.contains("wood")) return true;
    return false;
}

    // --- Embers (yoğunluk) ---

    private static void tryEmbers(ServerLevel level, BlockPos firePos, RandomSource random, Vec2 wind, float wind01, float dry, float fuel, float rain01, float frontPressure, float frontMul) {
        if (WildfireConfig.SERVER.emberStopInRain.get() && rain01 > (0.01f + 0.10f * frontPressure)) return;
        if (wind.length() < 0.08f) return;

        float density = 1f + WildfireConfig.SERVER.emberDensityMultiplier.get().floatValue()
            * wind01 * (0.35f + 0.65f * fuel) * (0.25f + 0.75f * dry);

        // Apocalypse: front büyüdükçe ember üretimi aşırı artar (super spotting).
        density *= (1f + 1.25f * frontPressure);

        float emberChance = WildfireConfig.SERVER.emberChance.get().floatValue() * density;
        emberChance = Mth.clamp(emberChance, 0f, WildfireConfig.SERVER.emberChanceMax.get().floatValue());

        if (random.nextFloat() > emberChance) return;

        int baseTries = WildfireConfig.SERVER.emberTries.get();
        int tries = (int) Math.round(baseTries * density);
        tries = (int) Math.round(tries * Math.pow(frontMul, 1.10));
        tries = Mth.clamp(tries, 1, Math.max(WildfireConfig.SERVER.emberMaxTries.get(), 120));

        Vec2 windN = wind.normalized();
        int minD = WildfireConfig.SERVER.emberMinDistance.get();
        int maxD = WildfireConfig.SERVER.emberMaxDistance.get();
        int bonus = (int) Math.round(WildfireConfig.SERVER.emberWindDistanceBonus.get().doubleValue() * wind01);
        int maxDist = maxD + bonus;
        // Super spotting: çok rüzgarlı + kuraksa 50-80 bloklara kadar sıçrayabilsin.
        int superMax = 12 + (int) Math.round(70f * wind01 * (0.35f + 0.65f * dry));
        superMax = Mth.clamp(superMax, maxDist, 90);
        // Front büyüyünce daha da artar.
        int frontBonus = (int) Math.round(60f * frontPressure * wind01);
        maxDist = Math.max(maxDist, Math.min(110, superMax + frontBonus));

        float sideJitter = WildfireConfig.SERVER.emberSideJitter.get().floatValue();

        for (int i = 0; i < tries; i++) {
            int dist = Mth.nextInt(random, minD, Math.max(minD, maxDist));
            float side = (random.nextFloat() - 0.5f) * sideJitter;

            float lx = firePos.getX() + 0.5f + windN.x * dist + (-windN.y) * side;
            float lz = firePos.getZ() + 0.5f + windN.y * dist + (windN.x) * side;

            BlockPos probe = BlockPos.containing(lx, firePos.getY(), lz);
            BlockPos ground = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, probe);
            BlockPos air = ground.above();

            if (level.isRainingAt(air)) continue;

            tryIgniteAir(level, air, random);
        }
    }

    // --- Crown fire ---

    private static void tryCrownFire(ServerLevel level, BlockPos firePos, RandomSource random, Vec2 wind, float wind01, float dry, float fuel, float frontPressure, float frontMul) {
        float minDry = WildfireConfig.SERVER.crownMinDryness.get().floatValue() - 0.18f * frontPressure;
        if (dry < minDry) return;
        if (fuel < WildfireConfig.SERVER.crownMinFuelLoad.get().floatValue()) return;
        float minWind = WildfireConfig.SERVER.crownMinWind.get().floatValue() - 0.12f * frontPressure;
        if (wind01 < minWind) return;

        int radius = WildfireConfig.SERVER.crownRadius.get();
        radius = (int) Math.round(radius + 6f * frontPressure);
        int tries = WildfireConfig.SERVER.crownTries.get();
        tries = (int) Math.round(tries * Math.pow(frontMul, 1.15));
        tries = Mth.clamp(tries, WildfireConfig.SERVER.crownTries.get(), 200);
        int hMin = WildfireConfig.SERVER.crownHeightMin.get();
        int hMax = WildfireConfig.SERVER.crownHeightMax.get();

        Vec2 windN = wind.length() > 0.001f ? wind.normalized() : wind;

        // Crown-run + downwind zinciri: uygun koşullarda çizgi halinde çok daha uzağa sıçrasın.
        boolean doChain = (frontPressure > 0.35f) || (dry > 0.86f && wind01 > 0.70f);
        int chainSteps = doChain ? (3 + (frontPressure > 0.65f ? 2 : 1)) : 0; // 3-5
        int chainSpacing = doChain ? (8 + (int) Math.round(10f * wind01)) : 0; // 8-18

        for (int i = 0; i < tries; i++) {
            // downwind bias
            float bias = 0.9f * radius;
            int dx = Mth.floor(windN.x * bias) + random.nextInt(-radius, radius + 1);
            int dz = Mth.floor(windN.y * bias) + random.nextInt(-radius, radius + 1);
            int dy = Mth.nextInt(random, hMin, hMax);

            BlockPos p = firePos.offset(dx, dy, dz);
            BlockState s = level.getBlockState(p);

            // canopy yakıt (yaprak/odun) - TFC yaprakları bazen vanilla tag'de olmayabilir.
            if (!isCanopyFuel(s)) continue;

            // Yaprağın çevresinde bir kaç noktayı dene (agresif crown fire)
            for (int t = 0; t < 3; t++) {
                Direction dir = Direction.Plane.HORIZONTAL.getRandomDirection(random);
                BlockPos air = p.relative(dir);
                tryIgniteAir(level, air, random);
            }

            if (chainSteps > 0) {
                // p noktası canopy olduysa, onun downwind tarafında zincirleme canopy arayıp spot at.
                for (int step = 1; step <= chainSteps; step++) {
                    float jitter = (random.nextFloat() - 0.5f) * (4f + 8f * frontPressure);
                    float jx = (-windN.y) * jitter;
                    float jz = (windN.x) * jitter;
                    int sx = Mth.floor(windN.x * (chainSpacing * step) + jx);
                    int sz = Mth.floor(windN.y * (chainSpacing * step) + jz);

                    BlockPos cp = p.offset(sx, 0, sz);
                    // canopy yüksekliğine oturtmak için birkaç y aralığı tara
                    for (int yy = -2; yy <= 4; yy++) {
                        BlockPos canopy = cp.offset(0, yy, 0);
                        BlockState cs = level.getBlockState(canopy);
                        if (!isCanopyFuel(cs)) continue;

                        // 3-6 nokta ateşle (front büyükse daha çok)
                        int shots = 3 + (frontPressure > 0.65f ? 3 : 1);
                        for (int s2 = 0; s2 < shots; s2++) {
                            Direction dir2 = Direction.Plane.HORIZONTAL.getRandomDirection(random);
                            BlockPos air2 = canopy.relative(dir2);
                            tryIgniteAir(level, air2, random);
                        }
                        break; // o step için yeter
                    }
                }
            }
        }

        // crown olursa hotspot heat bonus
        float bonus = (float) WildfireConfig.SERVER.heatAddCrownBonus.get().doubleValue();
        HotspotTracker.addHeat(level, level.getChunkAt(firePos).getPos(), bonus);
    }

    private static boolean isCanopyFuel(BlockState state) {
        if (state.is(BlockTags.LEAVES) || state.is(BlockTags.LOGS_THAT_BURN)) return true;
        var key = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(state.getBlock());
        if (key == null) return false;
        String p = key.getPath();
        return p.contains("leaves") || p.contains("leaf") || p.contains("log") || p.contains("wood");
    }

}
