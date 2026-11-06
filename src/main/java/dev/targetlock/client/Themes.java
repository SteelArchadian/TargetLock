package dev.targetlock.client;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.targetlock.TLConfig;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Reader;
import java.util.*;

/**
 * Reticle themes are JSON files at assets/&lt;namespace&gt;/reticles/&lt;name&gt;.json, so a resource pack can add or override them.
 * See README.md for the format.
 */
public final class Themes implements ResourceManagerReloadListener {
    public static final Themes INSTANCE = new Themes();
    private static final Logger LOG = LoggerFactory.getLogger("TargetLock");
    static final ResourceLocation DEFAULT = ResourceLocation.fromNamespaceAndPath("targetlock", "zelda");

    public record Layer(ResourceLocation texture, float size, int count, float radius, float radiusPulse, float pulseSpeed,
                        float orbitSpeed, float spin, boolean radial, boolean corners, String anchor, float yOffset,
                        float bob, float bobSpeed, float flap, float scalePulse, float fit, float targetScale, boolean tint, int color,
                        float alpha, boolean additive) {}

    public record Theme(ResourceLocation id, String name, float fitPadding, List<Layer> layers) {}

    private volatile Map<ResourceLocation, Theme> themes = new LinkedHashMap<>();
    /** Set only by the -Dtargetlock.test debug switch: forces the theme by list position. */
    volatile int testIndex = -1;

    @Override
    public void onResourceManagerReload(ResourceManager rm) {
        Map<ResourceLocation, Theme> out = new TreeMap<>(Comparator.comparing(ResourceLocation::toString));
        FileToIdConverter conv = FileToIdConverter.json("reticles");
        for (Map.Entry<ResourceLocation, Resource> e : conv.listMatchingResources(rm).entrySet()) {
            ResourceLocation id = conv.fileToId(e.getKey());
            try (Reader r = e.getValue().openAsReader()) {
                out.put(id, parse(id, JsonParser.parseReader(r).getAsJsonObject()));
            } catch (Exception ex) {
                LOG.warn("Bad reticle theme {}: {}", e.getKey(), ex.toString());
            }
        }
        themes = out;
        LOG.info("Loaded {} reticle themes: {}", out.size(), out.keySet());
    }

    private static Theme parse(ResourceLocation id, JsonObject o) {
        List<Layer> layers = new ArrayList<>();
        for (JsonElement le : o.getAsJsonArray("layers")) {
            JsonObject l = le.getAsJsonObject();
            String placement = str(l, "placement", "orbit");
            layers.add(new Layer(ResourceLocation.parse(l.get("texture").getAsString()), f(l, "size", 32), (int) f(l, "count", 1),
                    f(l, "radius", 0), f(l, "radius_pulse", 0), f(l, "pulse_speed", 1), f(l, "orbit_speed", 0), f(l, "spin", 0),
                    str(l, "orient", "none").equals("radial"), placement.equals("corners"), str(l, "anchor", "center"),
                    f(l, "y_offset", 0), f(l, "bob", 0), f(l, "bob_speed", 1), f(l, "flap", 0), f(l, "scale_pulse", 0), f(l, "fit", 0), f(l, "target_scale", 0),
                    !l.has("tint") || l.get("tint").getAsBoolean(), color(str(l, "color", "#FFFFFF")), f(l, "alpha", 1),
                    l.has("additive") && l.get("additive").getAsBoolean()));
        }
        return new Theme(id, str(o, "name", id.toString()), f(o, "fit_padding", 6), layers);
    }

    private static float f(JsonObject o, String k, float d) { return o.has(k) ? o.get(k).getAsFloat() : d; }
    private static String str(JsonObject o, String k, String d) { return o.has(k) ? o.get(k).getAsString() : d; }

    static int color(String s) {
        try { return Integer.parseInt(s.strip().replace("#", ""), 16) & 0xFFFFFF; } catch (NumberFormatException e) { return -1; }
    }

    private static ResourceLocation id(String s) {
        String t = s.strip();
        if (t.equals("majora") || t.equals("targetlock:majora")) t = "zelda"; // old name
        ResourceLocation r = ResourceLocation.tryParse(t.contains(":") ? t : "targetlock:" + t);
        return r == null ? DEFAULT : r;
    }

    /** The theme the config asks for; falls back to the default, then to any theme. */
    public Theme current() {
        Map<ResourceLocation, Theme> m = themes;
        if (testIndex >= 0 && !m.isEmpty()) return new ArrayList<>(m.values()).get(testIndex % m.size());
        Theme t = m.get(id(TLConfig.THEME.get()));
        if (t == null) t = m.get(DEFAULT);
        if (t == null && !m.isEmpty()) t = m.values().iterator().next();
        return t;
    }

    /** Select the next theme in the list and save it. Returns its display name. */
    public String cycle() {
        Map<ResourceLocation, Theme> m = themes;
        if (m.isEmpty()) return "-";
        List<Theme> list = new ArrayList<>(m.values());
        Theme cur = current();
        int i = cur == null ? -1 : list.indexOf(cur);
        Theme next = list.get(Math.floorMod(i + 1, list.size()));
        TLConfig.THEME.set(next.id().toString());
        TLConfig.SPEC.save();
        return next.name();
    }
}
