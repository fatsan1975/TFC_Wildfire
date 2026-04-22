package com.tfcwildfire.wildfire;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.phys.Vec2;

import java.lang.reflect.Method;

public final class TFCClimateAdapter {

    private static final boolean TFC_CLIMATE_AVAILABLE;
    private static Method climateGet;
    private static Method averageTemp;
    private static Method averageRain;
    private static Method instantRain;
    private static Method wind;

    static {
        boolean ok = false;
        try {
            Class<?> climateClass = Class.forName("net.dries007.tfc.util.climate.Climate");
            Class<?> modelClass = Class.forName("net.dries007.tfc.util.climate.ClimateModel");
            climateGet = resolveMethod(climateClass, "get",
                new Class<?>[]{Level.class},
                new Class<?>[]{ServerLevel.class});
            averageTemp = resolveMethod(climateClass, "getAverageTemperature",
                new Class<?>[]{Level.class, BlockPos.class},
                new Class<?>[]{ServerLevel.class, BlockPos.class});
            averageRain = resolveMethod(climateClass, "getAverageRainfall",
                new Class<?>[]{Level.class, BlockPos.class},
                new Class<?>[]{ServerLevel.class, BlockPos.class});
            instantRain = resolveMethod(modelClass, "getInstantRainfall",
                new Class<?>[]{LevelReader.class, BlockPos.class},
                new Class<?>[]{Level.class, BlockPos.class},
                new Class<?>[]{ServerLevel.class, BlockPos.class});
            wind = resolveMethod(modelClass, "getWind",
                new Class<?>[]{Level.class, BlockPos.class},
                new Class<?>[]{ServerLevel.class, BlockPos.class});
            ok = true;
        } catch (Throwable ignored) {
        }
        TFC_CLIMATE_AVAILABLE = ok;
    }

    private TFCClimateAdapter() {}

    private static Method resolveMethod(Class<?> owner, String name, Class<?>[]... signatures) throws NoSuchMethodException {
        for (Class<?>[] signature : signatures) {
            try {
                return owner.getMethod(name, signature);
            } catch (NoSuchMethodException ignored) {
            }
        }
        throw new NoSuchMethodException(owner.getName() + "#" + name);
    }

    public record ClimateSnapshot(
        float avgTempC,
        float avgRainfallMm,
        float instantRainMm,
        Vec2 wind
    ) {
        public float avgRainfall01() { return Mth.clamp(avgRainfallMm / 500f, 0f, 1f); }
        public float instantRain01() { return Mth.clamp(instantRainMm / 1000f, 0f, 1f); }

        public float humidityProxy01() {
            return Mth.clamp(0.65f * instantRain01() + 0.35f * avgRainfall01(), 0f, 1f);
        }

        public float wind01() {
            return Mth.clamp(wind.length(), 0f, 1f);
        }
    }

    public static ClimateSnapshot sample(ServerLevel level, BlockPos pos) {
        if (TFC_CLIMATE_AVAILABLE) {
            try {
                Object model = climateGet.invoke(null, level);
                float avgTemp = ((Number) averageTemp.invoke(null, level, pos)).floatValue();
                float avgRain = ((Number) averageRain.invoke(null, level, pos)).floatValue();
                float instant = ((Number) instantRain.invoke(model, level, pos)).floatValue();
                Vec2 w = (Vec2) wind.invoke(model, level, pos);
                return new ClimateSnapshot(avgTemp, avgRain, instant, w);
            } catch (Throwable ignored) {
            }
        }

        // fallback (no TFC climate classes on compile/runtime)
        float vanillaRain = level.isRainingAt(pos) ? level.getRainLevel(1.0f) : 0f;
        return new ClimateSnapshot(15f, vanillaRain * 300f, vanillaRain * 1000f, Vec2.ZERO);
    }

    public static float rain01(ServerLevel level, BlockPos pos, ClimateSnapshot s) {
        float worldRain = level.isRainingAt(pos) ? Mth.clamp(level.getRainLevel(1.0f), 0f, 1f) : 0f;
        return Math.max(worldRain, s.instantRain01());
    }

    public static float baseDryness01(ClimateSnapshot s) {
        float temp = Mth.clamp((s.avgTempC - 5f) / 25f, 0f, 1f);
        float humidity = s.humidityProxy01();
        float avgRain = s.avgRainfall01();

        float dry = 0.15f + 0.55f * temp + 0.35f * (1f - humidity);
        dry *= (1f - 0.55f * avgRain);
        return Mth.clamp(dry, 0f, 1f);
    }
}
