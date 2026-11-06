package dev.targetlock.client;

import net.neoforged.fml.ModContainer;

/** Isolated so the optional Config Panel class is only touched when that mod is present. */
final class PanelHook {
    static void register(ModContainer c) { dev.configpanel.ConfigPanel.register(c); }
}
