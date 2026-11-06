package dev.targetlock.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.client.event.*;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import net.neoforged.neoforge.common.NeoForge;
import org.lwjgl.glfw.GLFW;

public final class ClientSetup {
    static final String CAT = "key.categories.targetlock";
    public static final KeyMapping LOCK = key("lock", GLFW.GLFW_KEY_Z);
    public static final KeyMapping NEXT = key("next", GLFW.GLFW_KEY_PERIOD);
    public static final KeyMapping PREV = key("prev", GLFW.GLFW_KEY_COMMA);
    public static final KeyMapping THEME = key("theme", GLFW.GLFW_KEY_UNKNOWN);

    private static KeyMapping key(String n, int code) {
        return new KeyMapping("key.targetlock." + n, KeyConflictContext.IN_GAME, InputConstants.Type.KEYSYM, code, CAT);
    }

    public static void init(IEventBus bus, ModContainer container) {
        if (ModList.get().isLoaded("configpanel")) PanelHook.register(container);
        else container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);

        bus.addListener((RegisterKeyMappingsEvent e) -> { e.register(LOCK); e.register(NEXT); e.register(PREV); e.register(THEME); });
        bus.addListener((RegisterClientReloadListenersEvent e) -> e.registerReloadListener(Themes.INSTANCE));
        bus.addListener((RegisterGuiLayersEvent e) -> {
            e.registerBelowAll(ResourceLocation.fromNamespaceAndPath("targetlock", "letterbox"), Reticle::renderBars);
            e.registerAboveAll(ResourceLocation.fromNamespaceAndPath("targetlock", "reticle"), Reticle::render);
        });
        NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post e) -> LockOn.tick(Minecraft.getInstance()));
        NeoForge.EVENT_BUS.addListener((RenderFrameEvent.Pre e) -> LockOn.frame(Minecraft.getInstance()));
        NeoForge.EVENT_BUS.addListener((ViewportEvent.ComputeFov e) -> { if (e.usedConfiguredFov()) Reticle.fov = e.getFOV(); });
        NeoForge.EVENT_BUS.addListener((InputEvent.MouseScrollingEvent e) -> {
            if (LockOn.onScroll(e.getScrollDeltaY())) e.setCanceled(true);
        });
        NeoForge.EVENT_BUS.addListener((RenderGuiLayerEvent.Pre e) -> {
            if (e.getName().equals(VanillaGuiLayers.CROSSHAIR) && LockOn.hideCrosshair()) e.setCanceled(true);
        });
        NeoForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggingOut e) -> LockOn.release());
    }
}
