package com.tfcwildfire.config;

import net.neoforged.neoforge.common.ModConfigSpec;
import org.apache.commons.lang3.tuple.Pair;

public final class WildfireConfig {

    public static final ModConfigSpec SERVER_SPEC;
    public static final Server SERVER;

    static {
        Pair<Server, ModConfigSpec> pair = new ModConfigSpec.Builder().configure(Server::new);
        SERVER = pair.getLeft();
        SERVER_SPEC = pair.getRight();
    }

    public static final class Server {
        // master
        public final ModConfigSpec.BooleanValue enabled;

        // spread + fuel
        public final ModConfigSpec.DoubleValue baseSpreadChance;
        public final ModConfigSpec.IntValue extraNeighborTries;
        public final ModConfigSpec.DoubleValue windSpreadMultiplier;
        public final ModConfigSpec.DoubleValue fuelLoadSpreadMultiplier;

        // fuel load sampling
        public final ModConfigSpec.BooleanValue fuelLoadEnabled;
        public final ModConfigSpec.IntValue fuelLoadRadius;
        public final ModConfigSpec.IntValue fuelLoadSamples;

        // moisture (kuruma gecikmesi)
        public final ModConfigSpec.BooleanValue moistureEnabled;
        public final ModConfigSpec.DoubleValue wetnessMax;
        public final ModConfigSpec.DoubleValue rainWettingRatePerSecond;
        public final ModConfigSpec.DoubleValue baseDryingRatePerSecond;
        public final ModConfigSpec.DoubleValue windDryingBonus;
        public final ModConfigSpec.DoubleValue tempDryingBonus;

        // embers (ember yoğunluğu)
        public final ModConfigSpec.BooleanValue emberEnabled;
        public final ModConfigSpec.BooleanValue emberStopInRain;
        public final ModConfigSpec.DoubleValue emberChance;
        public final ModConfigSpec.DoubleValue emberChanceMax;
        public final ModConfigSpec.IntValue emberTries;
        public final ModConfigSpec.IntValue emberMaxTries;
        public final ModConfigSpec.IntValue emberMinDistance;
        public final ModConfigSpec.IntValue emberMaxDistance;
        public final ModConfigSpec.DoubleValue emberDensityMultiplier;
        public final ModConfigSpec.DoubleValue emberWindDistanceBonus;
        public final ModConfigSpec.DoubleValue emberSideJitter;
        public final ModConfigSpec.IntValue emberDownSearch;

        // crown fire
        public final ModConfigSpec.BooleanValue crownEnabled;
        public final ModConfigSpec.DoubleValue crownMinDryness;
        public final ModConfigSpec.DoubleValue crownMinFuelLoad;
        public final ModConfigSpec.DoubleValue crownMinWind;
        public final ModConfigSpec.IntValue crownRadius;
        public final ModConfigSpec.IntValue crownTries;
        public final ModConfigSpec.IntValue crownHeightMin;
        public final ModConfigSpec.IntValue crownHeightMax;

        // particles
        public final ModConfigSpec.BooleanValue particlesEnabled;
        public final ModConfigSpec.IntValue smokeParticles;
        public final ModConfigSpec.IntValue emberParticles;

        // fire-front brown fog
        public final ModConfigSpec.BooleanValue frontFogEnabled;
        public final ModConfigSpec.DoubleValue frontFogMinPressure;
        public final ModConfigSpec.DoubleValue frontFogBaseRadius;
        public final ModConfigSpec.DoubleValue frontFogRadiusBonus;
        public final ModConfigSpec.IntValue frontFogIntervalTicks;

        // smoldering (yanmış alan tekrar tutuşabilir)
        public final ModConfigSpec.BooleanValue smolderEnabled;
        public final ModConfigSpec.IntValue smolderIntervalTicks;
        public final ModConfigSpec.IntValue smolderMaxChunksPerRun;
        public final ModConfigSpec.DoubleValue heatMax;
        public final ModConfigSpec.DoubleValue heatAddPerFireTick;
        public final ModConfigSpec.DoubleValue heatAddCrownBonus;
        public final ModConfigSpec.DoubleValue heatDecayPerSecond;
        public final ModConfigSpec.DoubleValue smolderMinHeat01;
        public final ModConfigSpec.DoubleValue smolderReigniteChance;

        // dropped torch ignition
        public final ModConfigSpec.BooleanValue torchIgnitionEnabled;
        public final ModConfigSpec.IntValue torchDelaySeconds;
        public final ModConfigSpec.IntValue torchCheckIntervalTicks;
        public final ModConfigSpec.DoubleValue torchChancePerCheck;
        public final ModConfigSpec.DoubleValue torchMinDryness;
        public final ModConfigSpec.DoubleValue torchMinFuelLoad;
        public final ModConfigSpec.IntValue torchSearchRadius;
        public final ModConfigSpec.BooleanValue torchConsumeItem;

        // TFC grass / plants flammability (compat)
        public final ModConfigSpec.BooleanValue tfcGrassFlammabilityEnabled;
        public final ModConfigSpec.IntValue tfcGrassEncouragement;
        public final ModConfigSpec.IntValue tfcGrassFlammability;

        Server(ModConfigSpec.Builder b) {
            b.push("wildfire");

            enabled = b.define("enabled", true);

            b.push("spread");
            baseSpreadChance = b.defineInRange("baseSpreadChance", 0.42d, 0d, 1d);
            extraNeighborTries = b.defineInRange("extraNeighborTries", 6, 0, 32);
            windSpreadMultiplier = b.defineInRange("windSpreadMultiplier", 3.0d, 0d, 10d);
            fuelLoadSpreadMultiplier = b.defineInRange("fuelLoadSpreadMultiplier", 2.8d, 0d, 10d);
            b.pop();

            b.push("fuelLoad");
            fuelLoadEnabled = b.define("enabled", true);
            fuelLoadRadius = b.defineInRange("radius", 6, 1, 32);
            fuelLoadSamples = b.defineInRange("samples", 24, 6, 128);
            b.pop();

            b.push("moisture");
            moistureEnabled = b.define("enabled", true);
            wetnessMax = b.defineInRange("wetnessMax", 1.0d, 0.1d, 5.0d);
            rainWettingRatePerSecond = b.defineInRange("rainWettingRatePerSecond", 0.20d, 0.0d, 2.0d);
            baseDryingRatePerSecond = b.defineInRange("baseDryingRatePerSecond", 0.018d, 0.0d, 1.0d);
            windDryingBonus = b.defineInRange("windDryingBonus", 1.2d, 0.0d, 10.0d);
            tempDryingBonus = b.defineInRange("tempDryingBonus", 1.0d, 0.0d, 10.0d);
            b.pop();

            b.push("embers");
            emberEnabled = b.define("enabled", true);
            emberStopInRain = b.define("stopInRain", true);
            emberChance = b.defineInRange("chance", 0.14d, 0d, 1d);
            emberChanceMax = b.defineInRange("chanceMax", 0.60d, 0d, 1d);
            emberTries = b.defineInRange("tries", 3, 1, 64);
            emberMaxTries = b.defineInRange("maxTries", 22, 1, 256);
            emberMinDistance = b.defineInRange("minDistance", 8, 1, 256);
            emberMaxDistance = b.defineInRange("maxDistance", 42, 1, 2048);
            emberDensityMultiplier = b.defineInRange("densityMultiplier", 2.8d, 0d, 20d);
            emberWindDistanceBonus = b.defineInRange("windDistanceBonus", 18d, 0d, 256d);
            emberSideJitter = b.defineInRange("sideJitter", 6d, 0d, 64d);
            emberDownSearch = b.defineInRange("downSearch", 24, 4, 128);
            b.pop();

            b.push("crown");
            crownEnabled = b.define("enabled", true);
            crownMinDryness = b.defineInRange("minDryness", 0.55d, 0d, 1d);
            crownMinFuelLoad = b.defineInRange("minFuelLoad", 0.55d, 0d, 1d);
            crownMinWind = b.defineInRange("minWind01", 0.35d, 0d, 1d);
            crownRadius = b.defineInRange("radius", 6, 1, 24);
            crownTries = b.defineInRange("tries", 10, 1, 80);
            crownHeightMin = b.defineInRange("heightMin", 3, 1, 16);
            crownHeightMax = b.defineInRange("heightMax", 9, 2, 32);
            b.pop();

            b.push("particles");
            particlesEnabled = b.define("enabled", true);
            smokeParticles = b.defineInRange("smokeParticlesPerTick", 10, 0, 80);
            emberParticles = b.defineInRange("emberParticlesPerTick", 4, 0, 80);
            b.pop();

            b.push("frontFog");
            frontFogEnabled = b.define("enabled", true);
            frontFogMinPressure = b.defineInRange("minPressure", 0.42d, 0d, 1d);
            frontFogBaseRadius = b.defineInRange("baseRadius", 18d, 4d, 256d);
            frontFogRadiusBonus = b.defineInRange("radiusBonus", 64d, 0d, 512d);
            frontFogIntervalTicks = b.defineInRange("intervalTicks", 5, 1, 40);
            b.pop();

            b.push("smolder");
            smolderEnabled = b.define("enabled", true);
            smolderIntervalTicks = b.defineInRange("intervalTicks", 40, 10, 200);
            smolderMaxChunksPerRun = b.defineInRange("maxChunksPerRun", 12, 1, 200);
            heatMax = b.defineInRange("heatMax", 1.0d, 0.1d, 10.0d);
            heatAddPerFireTick = b.defineInRange("heatAddPerFireTick", 0.050d, 0.0d, 1.0d);
            heatAddCrownBonus = b.defineInRange("heatAddCrownBonus", 0.020d, 0.0d, 1.0d);
            heatDecayPerSecond = b.defineInRange("heatDecayPerSecond", 0.004d, 0.0d, 1.0d);
            smolderMinHeat01 = b.defineInRange("minHeat01", 0.25d, 0.0d, 1.0d);
            smolderReigniteChance = b.defineInRange("reigniteChance", 0.38d, 0.0d, 1.0d);
            b.pop();

            b.push("torch");
            torchIgnitionEnabled = b.define("enabled", true);
            torchDelaySeconds = b.defineInRange("delaySeconds", 20, 0, 300);
            torchCheckIntervalTicks = b.defineInRange("checkIntervalTicks", 20, 1, 200);
            torchChancePerCheck = b.defineInRange("chancePerCheck", 0.12d, 0d, 1d);
            torchMinDryness = b.defineInRange("minDryness", 0.35d, 0d, 1d);
            torchMinFuelLoad = b.defineInRange("minFuelLoad", 0.25d, 0d, 1d);
            torchSearchRadius = b.defineInRange("searchRadius", 3, 0, 6);
            torchConsumeItem = b.define("consumeItem", true);
            b.pop();


            b.push("compat");
            tfcGrassFlammabilityEnabled = b.define("tfcGrassFlammabilityEnabled", true);
            tfcGrassEncouragement = b.defineInRange("tfcGrassEncouragement", 35, 0, 300);
            tfcGrassFlammability = b.defineInRange("tfcGrassFlammability", 80, 0, 300);
            b.pop();
            b.pop();
        }
    }
}
