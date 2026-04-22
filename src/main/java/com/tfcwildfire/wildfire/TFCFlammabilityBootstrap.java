package com.tfcwildfire.wildfire;

import com.tfcwildfire.config.WildfireConfig;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FireBlock;

/**
 * Makes TFC grasses (and a few groundcover plants) flammable so wildfires can actually run.
 * This is intentionally heuristic: it scans registry names under the "tfc" namespace.
 */
public final class TFCFlammabilityBootstrap {

    private static boolean done = false;

    private TFCFlammabilityBootstrap() {}

    public static void init() {
        if (done) return;
        done = true;

// Config values are not always available during parallel setup work.
// If config isn't loaded yet, fall back to safe defaults (same as config defaults).
boolean enabled;
int encouragement;
int flammability;
try {
    enabled = WildfireConfig.SERVER.tfcGrassFlammabilityEnabled.get();
    encouragement = WildfireConfig.SERVER.tfcGrassEncouragement.get();
    flammability = WildfireConfig.SERVER.tfcGrassFlammability.get();
} catch (IllegalStateException e) {
    enabled = true;
    encouragement = 35;
    flammability = 80;
}
if (!enabled) return;


        final FireBlock fire = (FireBlock) Blocks.FIRE;

        int count = 0;
        for (ResourceLocation id : BuiltInRegistries.BLOCK.keySet()) {
            if (!"tfc".equals(id.getNamespace())) continue;
            final String path = id.getPath();

            // grasses / groundcover style
            if (containsAny(path,
                "grass", "tall_grass", "short_grass", "dry_grass", "bunchgrass",
                "sedge", "shrub", "bush", "groundcover", "litter", "fallen_leaves"
            )) {
                Block b = BuiltInRegistries.BLOCK.get(id);
                // Avoid doing something silly for air
                if (b == Blocks.AIR) continue;

                // Encourage small vegetation to ignite and spread
                try {
                    fire.setFlammable(b, encouragement, flammability);
                    count++;
                } catch (Throwable ignored) {
                    // If Mojang changes the signature, we just skip.
                }
            }
        }

        // Optional: vanilla grasses too (helps for mixed-dimension test worlds)
        try {
            fire.setFlammable(Blocks.SHORT_GRASS, encouragement, flammability);
            fire.setFlammable(Blocks.TALL_GRASS, encouragement, flammability);
        } catch (Throwable ignored) {}
    }

    private static boolean containsAny(String s, String... needles) {
        for (String n : needles) {
            if (s.contains(n)) return true;
        }
        return false;
    }
}
