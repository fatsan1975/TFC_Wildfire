package com.tfcwildfire.wildfire;

import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.state.BlockState;

import java.util.UUID;

/**
 * Yere atılan meşaleler belli süre yerde kalırsa (despawn olmadan önce) yangın başlatabilsin.
 * - Event ile ItemEntity dünyaya girince izlemeye alıyoruz.
 * - Her tick/2 tick kontrol edip koşullar uygunsa tutuşma deniyoruz.
 */
public final class TorchIgnitionTracker {

    // UUID -> igniteAtGameTime
    private static final Object2LongOpenHashMap<UUID> igniteAt = new Object2LongOpenHashMap<>();

    // 30 saniye (600 tick) default
    private static final long DELAY_TICKS = 20L * 30L;

    private TorchIgnitionTracker() {}

    public static void maybeTrackTorch(ServerLevel level, ItemEntity itemEntity) {
        ItemStack stack = itemEntity.getItem();
        if (stack.isEmpty()) return;

        ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        String path = id.getPath();

        // Basit: ismi torch içeren itemleri kabul et
        if (!path.contains("torch")) return;

        long now = level.getGameTime();
        igniteAt.put(itemEntity.getUUID(), now + DELAY_TICKS);
    }

    public static void tick(ServerLevel level) {
        if (igniteAt.isEmpty()) return;

        long now = level.getGameTime();

        // Her tick hepsini kontrol etmeyelim: yarısını kontrol eder gibi "seyreltme"
        // (basit bir performans optimizasyonu)
        int checked = 0;
        var it = igniteAt.object2LongEntrySet().fastIterator();
        while (it.hasNext() && checked < 80) {
            var e = it.next();
            UUID uuid = e.getKey();
            long when = e.getLongValue();

            if (now < when) {
                checked++;
                continue;
            }

            Entity entity = level.getEntity(uuid);
            if (!(entity instanceof ItemEntity itemEntity) || !itemEntity.isAlive()) {
                it.remove();
                checked++;
                continue;
            }

            BlockPos pos = itemEntity.blockPosition();
            var climate = TFCClimateAdapter.sample(level, pos);
            float rain01 = TFCClimateAdapter.rain01(level, pos, climate);

            float baseDry = TFCClimateAdapter.baseDryness01(climate);
            float wetness = MoistureTracker.getWetness(level, level.getChunkAt(pos).getPos(), rain01, climate.avgTempC(), climate.wind01());

            float dryness = net.minecraft.util.Mth.clamp(baseDry * (1f - wetness), 0f, 1f);


            // Torch düştüğü yüzey/zemin yanabiliyor mu?
            BlockPos ground = pos;
            if (level.getBlockState(ground).isAir()) ground = ground.below();

            boolean nearBurnable = isBurnableSurface(level, ground);
            if (!nearBurnable) {
                // Yanabilir bir şeye temas etmiyorsa: biraz daha seyrek kontrol
                igniteAt.put(uuid, now + 20L * 20L);
                checked++;
                continue;
            }

            float frontPressure = WildfireFrontTracker.getPressure(level);
            float frontMul = WildfireFrontTracker.getMultiplier(level);

            // Yağmurda genelde olmaz; ama apocalypse modunda (front büyükse) "sıcaklık" baskın gelebilir.
            if (level.isRainingAt(ground.above()) && frontPressure < 0.65f) {
                igniteAt.put(uuid, now + 20L * 10L);
                checked++;
                continue;
            }

            // Kuruluk eşiği: normalde orta-üst; ama front büyüdükçe eşik düşer.
            float minDry = 0.55f - 0.25f * frontPressure; // 0.55 -> 0.30
            if (dryness < minDry) {
                igniteAt.put(uuid, now + 20L * 10L);
                checked++;
                continue;
            }

            float fuel = FuelCalculator.fuelLoad(level, ground);
            float chance = 0.02f;
            chance += 0.35f * dryness;
            chance += 0.25f * fuel;
            chance += 0.25f * frontPressure;
            chance = Mth.clamp(chance, 0f, 0.90f);

            if (level.random.nextFloat() < (0.08f * chance)) {
                BlockPos igniteSpot = findIgnitionSpot(level, ground);
                boolean ignited = igniteSpot != null && WildfireSpread.tryIgniteAir(level, igniteSpot, level.random);

                if (ignited) {
                    // meşale "alev aldı" gibi: entity'i yok et
                    itemEntity.discard();
                    it.remove();
                } else {
                    igniteAt.put(uuid, now + 20L * 8L);
                }
            } else {
                igniteAt.put(uuid, now + 20L * 8L);
            }

            checked++;
        }
    }

    private static boolean isBurnableSurface(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (state.isAir()) return false;
        if (BurningBlockCompat.isCharred(state)) return true;
        if (ClayPitProtectionTracker.shouldSuppressGrassFlammability(level, pos, state)) return false;
        return state.isFlammable(level, pos, net.minecraft.core.Direction.UP);
    }

    private static BlockPos findIgnitionSpot(ServerLevel level, BlockPos ground) {
        BlockPos up = ground.above();
        if (canPlaceFire(level, up)) return up;

        for (var d : net.minecraft.core.Direction.Plane.HORIZONTAL) {
            BlockPos side = ground.relative(d);
            if (isBurnableSurface(level, side) && canPlaceFire(level, side.above())) return side.above();
            if (canPlaceFire(level, side)) return side;
        }
        return null;
    }

    private static boolean canPlaceFire(ServerLevel level, BlockPos p) {
        if (!level.getBlockState(p).isAir()) return false;
        BlockState fireState = BaseFireBlock.getState(level, p);
        return fireState.canSurvive(level, p);
    }
}
