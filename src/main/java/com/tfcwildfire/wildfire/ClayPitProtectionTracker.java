package com.tfcwildfire.wildfire;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongTag;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Tracks player-placed TFC grasses used around clay pits and makes a 7x7 area fire-protected.
 * Event-driven + SavedData (no periodic world scan).
 */
public final class ClayPitProtectionTracker {
    private static final String DATA_ID = "tfcwildfire_clay_pit_protection";
    private static final int RADIUS = 3;

    private ClayPitProtectionTracker() {}

    public static void onBlockPlaced(ServerLevel level, BlockPos pos, BlockState state) {
        Data data = data(level);
        if (isPlacedTfcGrass(level, state)) {
            data.addCenter(pos);
        } else {
            data.removeCenter(pos);
        }
    }

    public static void onBlockBroken(ServerLevel level, BlockPos pos) {
        data(level).removeCenter(pos);
    }

    public static boolean shouldSuppressGrassFlammability(ServerLevel level, BlockPos pos, BlockState state) {
        if (!isAnyGrass(level, state)) return false;
        return data(level).isProtected(pos);
    }

    public static boolean shouldSuppressFireAt(ServerLevel level, BlockPos firePos) {
        BlockPos below = firePos.below();
        BlockState belowState = level.getBlockState(below);
        return shouldSuppressGrassFlammability(level, below, belowState);
    }

    private static boolean isPlacedTfcGrass(Level level, BlockState state) {
        ResourceLocation key = BuiltInRegistries.BLOCK.getKey(state.getBlock());
        if (key == null || !"tfc".equals(key.getNamespace())) return false;
        String path = key.getPath();
        return path.contains("grass") || path.contains("groundcover") || path.contains("sod");
    }

    private static boolean isAnyGrass(Level level, BlockState state) {
        ResourceLocation key = BuiltInRegistries.BLOCK.getKey(state.getBlock());
        if (key == null) return false;
        String path = key.getPath();
        return path.contains("grass") || path.contains("groundcover") || path.contains("sod");
    }

    private static Data data(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(
            new SavedData.Factory<>(Data::new, Data::load, null),
            DATA_ID
        );
    }

    private static final class Data extends SavedData {
        private final Set<Long> centers = new HashSet<>();
        private final Map<Long, List<Long>> byChunk = new HashMap<>();

        private Data() {}

        private static Data load(CompoundTag tag, HolderLookup.Provider registries) {
            Data d = new Data();
            ListTag list = tag.getList("centers", LongTag.TAG_LONG);
            for (int i = 0; i < list.size(); i++) {
                d.addCenter(BlockPos.of(((LongTag) list.get(i)).getAsLong()));
            }
            return d;
        }

        @Override
        public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
            ListTag list = new ListTag();
            for (long p : centers) list.add(LongTag.valueOf(p));
            tag.put("centers", list);
            return tag;
        }

        void addCenter(BlockPos pos) {
            long packed = pos.asLong();
            if (centers.add(packed)) {
                byChunk.computeIfAbsent(chunkKey(pos), k -> new ArrayList<>()).add(packed);
                setDirty();
            }
        }

        void removeCenter(BlockPos pos) {
            long packed = pos.asLong();
            if (centers.remove(packed)) {
                List<Long> list = byChunk.get(chunkKey(pos));
                if (list != null) {
                    list.remove(packed);
                    if (list.isEmpty()) byChunk.remove(chunkKey(pos));
                }
                setDirty();
            }
        }

        boolean isProtected(BlockPos target) {
            int minCx = (target.getX() - RADIUS) >> 4;
            int maxCx = (target.getX() + RADIUS) >> 4;
            int minCz = (target.getZ() - RADIUS) >> 4;
            int maxCz = (target.getZ() + RADIUS) >> 4;

            for (int cx = minCx; cx <= maxCx; cx++) {
                for (int cz = minCz; cz <= maxCz; cz++) {
                    List<Long> list = byChunk.get(chunkKey(cx, cz));
                    if (list == null || list.isEmpty()) continue;
                    for (long packed : list) {
                        BlockPos c = BlockPos.of(packed);
                        if (Math.abs(c.getX() - target.getX()) <= RADIUS
                            && Math.abs(c.getZ() - target.getZ()) <= RADIUS
                            && Math.abs(c.getY() - target.getY()) <= 2) {
                            return true;
                        }
                    }
                }
            }
            return false;
        }

        private static long chunkKey(BlockPos pos) {
            return chunkKey(pos.getX() >> 4, pos.getZ() >> 4);
        }

        private static long chunkKey(int cx, int cz) {
            return (((long) cx) << 32) ^ (cz & 0xffffffffL);
        }
    }
}
