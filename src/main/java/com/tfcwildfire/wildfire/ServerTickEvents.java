package com.tfcwildfire.wildfire;

import com.tfcwildfire.config.WildfireConfig;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

// ServerTickEvent.Post NeoForge’ta var :contentReference[oaicite:4]{index=4}
public final class ServerTickEvents {
    private ServerTickEvents() {}

    public static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();

        // Front/Apocalypse hesapla (her tick)
        WildfireFrontTracker.postServerTick(server);

        for (ServerLevel level : server.getAllLevels()) {
            WildfireFogEffects.tick(level);

            if (!WildfireConfig.SERVER.smolderEnabled.get()) continue;
            long t = level.getGameTime();
            int interval = WildfireConfig.SERVER.smolderIntervalTicks.get();
            if (interval > 0 && (t % interval) == 0) {
                HotspotTracker.processSmolder(level);
            }
        }
    }
}
