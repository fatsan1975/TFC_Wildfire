package com.tfcwildfire.wildfire;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Yakıt yükü (fuel load): yakındaki bitki örtüsü / odun / yaprak yoğunluğunu 0..1'e çevirir.
 * Daha sonra yayılım ve ember üretimini bununla çarpıyoruz.
 */
public final class FuelCalculator {

    private FuelCalculator() {}

    public static float fuelLoad(ServerLevel level, BlockPos center) {
        // Küçük bir hacim: 5x5x6 (performans için küçük tuttuk)
        int r = 2;
        float fuel = 0f;

        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                for (int dy = -1; dy <= 4; dy++) {
                    BlockPos p = center.offset(dx, dy, dz);
                    BlockState s = level.getBlockState(p);

                    // Su/ıslak bloklar yakıtı düşürsün
                    if (!s.getFluidState().isEmpty()) {
                        fuel -= 0.05f;
                        continue;
                    }

                    if (s.is(BlockTags.LOGS)) fuel += 0.10f;
                    else if (s.is(BlockTags.LEAVES)) fuel += 0.07f;
                    else if (s.is(BlockTags.SAPLINGS)) fuel += 0.03f;
                    else if (s.is(BlockTags.FLOWERS)) fuel += 0.02f;
                    else if (s.is(BlockTags.REPLACEABLE_BY_TREES)) fuel += 0.02f;

                    // Yanıcı bloklar (genel) az da olsa katkı yapsın
                    if (s.isFlammable(level, p, Direction.UP)) fuel += 0.005f;
                }
            }
        }

        return Mth.clamp(fuel, 0f, 1f);
    }

    public static boolean hasCanopy(ServerLevel level, BlockPos firePos) {
        // Crown fire için: üstte yaprak/odun var mı?
        for (int dy = 2; dy <= 7; dy++) {
            BlockState s = level.getBlockState(firePos.above(dy));
            if (s.is(BlockTags.LEAVES) || s.is(BlockTags.LOGS)) return true;
        }
        return false;
    }
}
