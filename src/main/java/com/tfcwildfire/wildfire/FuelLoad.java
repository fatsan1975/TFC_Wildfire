package com.tfcwildfire.wildfire;

import com.tfcwildfire.config.WildfireConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 0..1 "yakıt yükü".
 * 0 = çevrede yanacak az
 * 1 = sık orman / çok yanacak şey
 */
public final class FuelLoad {
    private FuelLoad() {}

    public static float sample(ServerLevel level, BlockPos origin, RandomSource random) {
        if (!WildfireConfig.SERVER.fuelLoadEnabled.get()) return 0.5f;

        int radius = WildfireConfig.SERVER.fuelLoadRadius.get();
        int samples = WildfireConfig.SERVER.fuelLoadSamples.get();

        float sum = 0f;

        for (int i = 0; i < samples; i++) {
            int dx = random.nextInt(-radius, radius + 1);
            int dz = random.nextInt(-radius, radius + 1);
            int dy = random.nextInt(-1, 3);

            BlockPos p = origin.offset(dx, dy, dz);
            BlockState s = level.getBlockState(p);
            if (s.isAir()) continue;

            float score = flammableScore(level, p, s);

            if (s.is(BlockTags.LEAVES)) score *= 1.35f;          // canopy yakıt
            if (s.is(BlockTags.REPLACEABLE)) score *= 1.15f;     // ot/çiçek vs

            sum += score;
        }

        float avg = sum / (float) samples;
        return Mth.clamp(avg, 0f, 1f);
    }

    private static float flammableScore(ServerLevel level, BlockPos pos, BlockState s) {
        int best = 0;
        for (Direction d : Direction.values()) {
            if (s.isFlammable(level, pos, d)) {
                best = Math.max(best, s.getFlammability(level, pos, d));
            }
        }
        // normalize
        return Mth.clamp(best / 300f, 0f, 1f);
    }
}
