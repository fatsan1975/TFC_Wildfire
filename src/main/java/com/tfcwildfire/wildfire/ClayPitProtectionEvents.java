package com.tfcwildfire.wildfire;

import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.event.level.BlockEvent;

public final class ClayPitProtectionEvents {
    private ClayPitProtectionEvents() {}

    public static void onBlockPlaced(BlockEvent.EntityPlaceEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        ClayPitProtectionTracker.onBlockPlaced(level, event.getPos(), event.getPlacedBlock());
    }

    public static void onBlockBroken(BlockEvent.BreakEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        ClayPitProtectionTracker.onBlockBroken(level, event.getPos());
    }
}
