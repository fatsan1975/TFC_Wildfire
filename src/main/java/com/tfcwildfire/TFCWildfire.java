package com.tfcwildfire;

import com.tfcwildfire.config.WildfireConfig;
import com.tfcwildfire.wildfire.ServerTickEvents;
import com.tfcwildfire.wildfire.ClayPitProtectionEvents;
import com.tfcwildfire.wildfire.TFCFlammabilityBootstrap;
import com.tfcwildfire.wildfire.TorchIgnitionEvents;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.common.NeoForge;

@Mod(TFCWildfire.MOD_ID)
public final class TFCWildfire {
    public static final String MOD_ID = "tfcwildfire";

    public TFCWildfire(IEventBus modBus, ModContainer container) {
        container.registerConfig(net.neoforged.fml.config.ModConfig.Type.SERVER, WildfireConfig.SERVER_SPEC);

        // common setup (register flammability)
        modBus.addListener(this::commonSetup);

        // server-side ticks + torch ignition
        NeoForge.EVENT_BUS.addListener(TorchIgnitionEvents::onEntityTick);
        NeoForge.EVENT_BUS.addListener(ServerTickEvents::onServerTick);
        NeoForge.EVENT_BUS.addListener(ClayPitProtectionEvents::onBlockPlaced);
        NeoForge.EVENT_BUS.addListener(ClayPitProtectionEvents::onBlockBroken);
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        event.enqueueWork(TFCFlammabilityBootstrap::init);
    }
}
