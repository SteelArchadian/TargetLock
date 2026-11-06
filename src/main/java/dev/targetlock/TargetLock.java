package dev.targetlock;

import dev.targetlock.client.ClientSetup;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;

/** Zelda-style target lock. Client-side only. Made with AI assistance. */
@Mod(value = TargetLock.MOD_ID, dist = Dist.CLIENT)
public final class TargetLock {
    public static final String MOD_ID = "targetlock";

    public TargetLock(IEventBus bus, ModContainer container) {
        container.registerConfig(ModConfig.Type.CLIENT, TLConfig.SPEC);
        container.registerConfig(ModConfig.Type.SERVER, TLConfig.SSPEC);
        ClientSetup.init(bus, container);
    }
}
