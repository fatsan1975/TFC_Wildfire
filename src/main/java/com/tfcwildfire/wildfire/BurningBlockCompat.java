package com.tfcwildfire.wildfire;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * burningblocktfc uyumluluğu.
 *
 * Amaç: TFC'nin zemin "grass/<soil>" blokları yanarken yok olup gitmesin;
 * burningblocktfc'nin "charred_grass/<soil>" bloklarına dönüşsün.
 *
 * Not: burningblocktfc yüklü değilse hiçbir şey yapmaz.
 */
public final class BurningBlockCompat {
    private BurningBlockCompat() {}

    private static final String TFC_NS = "tfc";
    private static final String BB_TFC_NS = "burningblocktfc";


/** Charred (yanmış/kömürleşmiş) blok mu? burningblock veya burningblocktfc'den gelir. */
public static boolean isCharred(BlockState state) {
    ResourceLocation key = BuiltInRegistries.BLOCK.getKey(state.getBlock());
    if (key == null) return false;
    String ns = key.getNamespace();
    if (!"burningblocktfc".equals(ns) && !"burningblock".equals(ns)) return false;
    String p = key.getPath();
    return p.startsWith("charred_") || p.contains("/charred_") || p.contains("charred");
}

    public static void tryCharredGrassBelow(ServerLevel level, BlockPos fireAirPos) {
        final BlockPos below = fireAirPos.below();
        final BlockState belowState = level.getBlockState(below);

        // TFC zemin çimleri: tfc:grass/<soil> ve tfc:grass_path/<soil>
        final ResourceLocation key = BuiltInRegistries.BLOCK.getKey(belowState.getBlock());
        if (key == null || !TFC_NS.equals(key.getNamespace())) return;

        final String path = key.getPath();
        final String soil;
        if (path.startsWith("grass/")) {
            soil = path.substring("grass/".length());
        } else if (path.startsWith("grass_path/")) {
            soil = path.substring("grass_path/".length());
        } else {
            return;
        }

        // burningblocktfc:charred_grass/<soil>
        final ResourceLocation charredId = ResourceLocation.fromNamespaceAndPath(BB_TFC_NS, "charred_grass/" + soil);
        final Block charred = BuiltInRegistries.BLOCK.get(charredId);

        // Registry'de yoksa (mod yüklü değil / başka isim) çık.
        if (charred == null || charred == Blocks.AIR) return;

        // Eğer zaten charred ise tekrar setleme.
        if (belowState.getBlock() == charred) return;

        level.setBlock(below, charred.defaultBlockState(), 11);
    }
}
