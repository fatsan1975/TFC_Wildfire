package com.tfcwildfire.wildfire;

import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import com.tfcwildfire.TFCWildfire;
import com.tfcwildfire.config.WildfireConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

public final class TorchIgnitionEvents {

    private static final TagKey<Item> IGNITION_SOURCES =
        TagKey.create(Registries.ITEM, ResourceLocation.fromNamespaceAndPath(TFCWildfire.MOD_ID, "ignition_sources"));

    private TorchIgnitionEvents() {}

    // EntityTickEvent.Post NeoForge 1.21.x’de var :contentReference[oaicite:3]{index=3}
    public static void onEntityTick(EntityTickEvent.Post event) {
        Entity ent = event.getEntity();
        if (!(ent instanceof ItemEntity item)) return;
        if (!(item.level() instanceof ServerLevel level)) return;
        if (!WildfireConfig.SERVER.torchIgnitionEnabled.get()) return;

        ItemStack stack = item.getItem();
        if (stack.isEmpty()) return;

        // Tag veya registry path “torch” içeriyorsa kabul et (TFC torch id farklı çıkarsa da çalışsın)
        ResourceLocation key = BuiltInRegistries.ITEM.getKey(stack.getItem());
        String path = key != null ? key.getPath() : "";
        boolean isTorchLike = path.contains("torch");
        if (!stack.is(IGNITION_SOURCES) && !isTorchLike) return;

        // kaç saniye yerde kalsın?
        int age = item.getAge();
        int delay = WildfireConfig.SERVER.torchDelaySeconds.get() * 20;
        if (age < delay) return;

        // check interval
        int interval = WildfireConfig.SERVER.torchCheckIntervalTicks.get();
        if (interval > 1 && (age % interval) != 0) return;

        BlockPos pos = item.blockPosition();

        // yağmurda asla başlatma
        if (level.isRainingAt(pos)) return;

        // iklim
        var climate = TFCClimateAdapter.sample(level, pos);
        float rain01 = TFCClimateAdapter.rain01(level, pos, climate);
        if (rain01 > 0.01f) return;

        float baseDry = TFCClimateAdapter.baseDryness01(climate);
        ChunkPos cp = level.getChunkAt(pos).getPos();
        float wetness = MoistureTracker.getWetness(level, cp, rain01, climate.avgTempC(), climate.wind01());

        float effectiveDry = Mth.clamp(baseDry * (1f - wetness), 0f, 1f);
        if (effectiveDry < WildfireConfig.SERVER.torchMinDryness.get().floatValue()) return;

        float fuel = FuelLoad.sample(level, pos, level.random);
        if (fuel < WildfireConfig.SERVER.torchMinFuelLoad.get().floatValue()) return;

        float chance = WildfireConfig.SERVER.torchChancePerCheck.get().floatValue();
        chance *= (0.35f + 0.65f * effectiveDry);
        chance *= (0.25f + 0.75f * fuel);
        chance *= (1.0f + 0.6f * climate.wind01());
        chance = Mth.clamp(chance, 0f, 0.70f);

        // küçük “ısınma dumanı”
        if (level.random.nextFloat() < 0.08f) {
            level.sendParticles(ParticleTypes.SMOKE, pos.getX() + 0.5, pos.getY() + 0.2, pos.getZ() + 0.5, 1, 0.15, 0.05, 0.15, 0.0);
        }

        if (level.random.nextFloat() >= chance) return;

        // ateş yeri bul
        BlockPos firePos = WildfireSpread.findNearbyFireSpot(level, pos, WildfireConfig.SERVER.torchSearchRadius.get());
        if (firePos == null) return;

        if (!WildfireSpread.tryIgniteAir(level, firePos, level.random)) return;

        level.sendParticles(ParticleTypes.FLAME, firePos.getX() + 0.5, firePos.getY() + 0.2, firePos.getZ() + 0.5, 4, 0.2, 0.1, 0.2, 0.01);

        if (WildfireConfig.SERVER.torchConsumeItem.get()) {
            stack.shrink(1);
            if (stack.isEmpty()) item.discard();
            else item.setItem(stack);
        }
    }
}
