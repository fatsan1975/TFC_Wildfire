package com.tfcwildfire.wildfire;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec2;

/**
 * TFC tarafında "rüzgar" API'si yoksa (çoğu sürümde yok), v0 için basit bir rüzgar modeli kullanıyoruz.
 * Sonra istersek gerçek rüzgar modlarından / datadan besleriz.
 */
public final class WindModel {

    public record Wind(Vec2 dir, float speed01) { }

    private WindModel() {}

    public static Wind get(ServerLevel level, BlockPos pos) {
        // Deterministik: gün + konum -> yön/sürat
        final long day = level.getDayTime() / 24000L;
        final double t = (day * 0.13) + (pos.getX() * 0.002) + (pos.getZ() * 0.002);

        final float angle = (float) (t % (Math.PI * 2.0));
        final float speed = Mth.clamp(0.15f + 0.85f * (float) Math.abs(Math.sin(t * 0.7)), 0f, 1f);

        final float dx = Mth.cos(angle);
        final float dz = Mth.sin(angle);

        return new Wind(new Vec2(dx, dz), speed);
    }
}
