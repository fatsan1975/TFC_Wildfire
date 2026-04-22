package com.tfcwildfire.wildfire;

import com.tfcwildfire.config.WildfireConfig;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import org.joml.Vector3f;

public final class WildfireFogEffects {
    private static final Vector3f BROWN_FOG = new Vector3f(0.47f, 0.35f, 0.24f);

    private WildfireFogEffects() {}

    public static void tick(ServerLevel level) {
        if (!WildfireConfig.SERVER.frontFogEnabled.get()) return;
        if (level.getGameTime() % WildfireConfig.SERVER.frontFogIntervalTicks.get() != 0) return;

        float pressure = WildfireFrontTracker.getPressure(level);
        if (pressure < WildfireConfig.SERVER.frontFogMinPressure.get().floatValue()) return;

        var center = WildfireFrontTracker.getFrontCenter(level);
        if (center == null) return;

        float radius = (float) WildfireConfig.SERVER.frontFogBaseRadius.get().doubleValue()
            + pressure * (float) WildfireConfig.SERVER.frontFogRadiusBonus.get().doubleValue();
        int ringPoints = Mth.clamp((int) (radius * 1.2f), 24, 160);
        int hazePoints = Mth.clamp((int) (20 + pressure * 90f), 20, 140);

        DustParticleOptions dust = new DustParticleOptions(BROWN_FOG, 2.2f + pressure * 1.8f);

        for (int i = 0; i < ringPoints; i++) {
            float a = (Mth.TWO_PI * i) / ringPoints;
            double px = center.x + Math.cos(a) * radius;
            double pz = center.z + Math.sin(a) * radius;
            double py = center.y + 1.5 + Math.sin(level.getGameTime() * 0.04 + i * 0.3) * 0.7;
            level.sendParticles(dust, px, py, pz, 1, 1.2, 0.8, 1.2, 0.0);
        }

        for (int i = 0; i < hazePoints; i++) {
            float a = level.random.nextFloat() * Mth.TWO_PI;
            float rr = level.random.nextFloat() * radius;
            double px = center.x + Math.cos(a) * rr;
            double pz = center.z + Math.sin(a) * rr;
            double py = center.y + 1.0 + level.random.nextDouble() * (2.8 + 2.2 * pressure);
            level.sendParticles(dust, px, py, pz, 1, 0.6, 0.35, 0.6, 0.0);
        }
    }
}
