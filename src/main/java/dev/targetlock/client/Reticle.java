package dev.targetlock.client;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.targetlock.TLConfig;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/** Draws the lock-on reticle (screen-space, projected from the target) and the optional cinematic bars. */
public final class Reticle {
    static volatile double fov = 70;
    private static float bars;

    private Reticle() {}

    /** Projects a world point to GUI coordinates; null when it is behind the camera. */
    private static float[] project(Camera cam, Vec3 p, int gw, int gh, double aspect) {
        Vec3 v = p.subtract(cam.getPosition());
        Vector3f look = cam.getLookVector(), left = cam.getLeftVector(), up = cam.getUpVector();
        double z = v.x * look.x + v.y * look.y + v.z * look.z;
        if (z < 0.05) return null;
        double x = v.x * left.x + v.y * left.y + v.z * left.z;
        double y = v.x * up.x + v.y * up.y + v.z * up.z;
        double th = Math.tan(Math.toRadians(fov) / 2.0);
        double ndcX = -(x / z) / (th * aspect), ndcY = (y / z) / th;
        return new float[]{(float) ((ndcX * 0.5 + 0.5) * gw), (float) ((0.5 - ndcY * 0.5) * gh)};
    }

    static void renderBars(GuiGraphics gg, DeltaTracker dt) {
        boolean want = LockOn.target() != null && TLConfig.LETTERBOX.get();
        float step = dt.getRealtimeDeltaTicks() / 20f * 5f;
        bars = want ? Math.min(1f, bars + step) : Math.max(0f, bars - step);
        if (bars <= 0f) return;
        float e = bars * bars * (3 - 2 * bars);
        int h = Math.round(gg.guiHeight() * 0.075f * e);
        gg.fill(0, 0, gg.guiWidth(), h, 0xFF000000);
        gg.fill(0, gg.guiHeight() - h, gg.guiWidth(), gg.guiHeight(), 0xFF000000);
    }

    static void render(GuiGraphics gg, DeltaTracker dt) {
        Minecraft mc = Minecraft.getInstance();
        LivingEntity t = LockOn.target();
        if (t == null || mc.options.hideGui) return;
        Themes.Theme theme = Themes.INSTANCE.current();
        if (theme == null) return;

        float pt = dt.getGameTimeDeltaPartialTick(false);
        Camera cam = mc.gameRenderer.getMainCamera();
        int gw = gg.guiWidth(), gh = gg.guiHeight();
        double aspect = (double) mc.getWindow().getWidth() / mc.getWindow().getHeight();

        Vec3 shift = new Vec3(Mth.lerp(pt, t.xo, t.getX()) - t.getX(), Mth.lerp(pt, t.yo, t.getY()) - t.getY(), Mth.lerp(pt, t.zo, t.getZ()) - t.getZ());
        AABB box = t.getBoundingBox().move(shift);
        Vec3 c = box.getCenter();
        float[] pc = project(cam, c, gw, gh, aspect);
        if (pc == null) return;
        float[] ptop = project(cam, new Vec3(c.x, box.maxY + 0.1, c.z), gw, gh, aspect);
        float[] pfeet = project(cam, new Vec3(c.x, box.minY, c.z), gw, gh, aspect);

        // On-screen rectangle of the target's bounding box.
        float minX = Float.MAX_VALUE, minY = Float.MAX_VALUE, maxX = -Float.MAX_VALUE, maxY = -Float.MAX_VALUE;
        for (int i = 0; i < 8; i++) {
            float[] p = project(cam, new Vec3((i & 1) == 0 ? box.minX : box.maxX, (i & 2) == 0 ? box.minY : box.maxY,
                    (i & 4) == 0 ? box.minZ : box.maxZ), gw, gh, aspect);
            if (p == null) continue;
            minX = Math.min(minX, p[0]); maxX = Math.max(maxX, p[0]);
            minY = Math.min(minY, p[1]); maxY = Math.max(maxY, p[1]);
        }
        if (minX > maxX) { minX = maxX = pc[0]; minY = maxY = pc[1]; }
        float cx = (minX + maxX) / 2f, cy = (minY + maxY) / 2f;
        float rw = Math.max(maxX - minX, 24f), rh = Math.max(maxY - minY, 24f);
        if (ptop == null) ptop = new float[]{cx, minY};
        if (pfeet == null) pfeet = new float[]{cx, maxY};
        float topX = ptop[0], topY = Math.min(ptop[1], minY);

        float anim = TLConfig.ANIM_SPEED.get().floatValue();
        float time = (System.nanoTime() % 3_600_000_000_000L) / 1.0e9f * anim;
        float pop = TLConfig.POP_IN.get() ? Mth.clamp(LockOn.age() / 0.22f, 0f, 1f) : 1f;
        pop = 1f - (1f - pop) * (1f - pop);
        float sizeMul = TLConfig.SIZE.get().floatValue();
        float opacity = TLConfig.OPACITY.get().floatValue();
        int override = TLConfig.COLOR.get().isBlank() ? -1 : Themes.color(TLConfig.COLOR.get());

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        for (Themes.Layer l : theme.layers()) {
            int rgb = l.tint() && override >= 0 ? override : l.color() < 0 ? 0xFFFFFF : l.color();
            float r = ((rgb >> 16) & 255) / 255f, g = ((rgb >> 8) & 255) / 255f, b = (rgb & 255) / 255f;
            float alpha = l.alpha() * opacity * pop;
            RenderSystem.setShaderColor(r, g, b, alpha);
            if (l.additive()) RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);

            float size = l.size() * sizeMul * (0.5f + 0.5f * pop);
            if (l.fit() > 0 && l.count() <= 1 && !l.corners()) size = Math.max(size, Math.max(rw, rh) * l.fit() * (0.5f + 0.5f * pop));
            float maxd = Math.max(rw, rh);
            boolean scaleT = TLConfig.SCALE_WITH_TARGET.get();
            if (scaleT && l.targetScale() > 0) size *= Mth.clamp(maxd / l.targetScale(), 0.7f, 1.7f);
            if (l.scalePulse() > 0) size *= 1f + l.scalePulse() * (float) Math.sin(time * l.pulseSpeed() * Mth.TWO_PI);

            float ax = cx, ay = cy;
            switch (l.anchor()) {
                case "above" -> { ax = topX; ay = topY; }
                case "feet" -> { ax = pfeet[0]; ay = pfeet[1]; }
                default -> { }
            }
            ay += (l.yOffset() * sizeMul) + l.bob() * sizeMul * (float) Math.sin(time * l.bobSpeed() * Mth.TWO_PI);

            float sx = size, sy = size;
            if (l.flap() > 0) sx *= 1f - 0.35f * (0.5f + 0.5f * (float) Math.sin(time * l.flap() * Mth.TWO_PI));
            float pulse = l.radiusPulse() * (float) Math.sin(time * l.pulseSpeed() * Mth.TWO_PI);

            if (l.corners()) {
                float pad = theme.fitPadding() * sizeMul + (1f - pop) * 40f;
                float[][] pos = {{minX - pad - pulse, minY - pad - pulse, 1, 1}, {maxX + pad + pulse, minY - pad - pulse, -1, 1},
                        {maxX + pad + pulse, maxY + pad + pulse, -1, -1}, {minX - pad - pulse, maxY + pad + pulse, 1, -1}};
                for (int i = 0; i < 4; i++) {
                    float off = 0.375f * size;
                    draw(gg, l, pos[i][0] + off * pos[i][2], pos[i][1] + off * pos[i][3], 90f * i, sx, sy);
                }
            } else {
                int n = Math.max(1, l.count());
                float radius = (l.radius() + pulse) * sizeMul;
                if (n > 1 && l.fit() > 0) {
                    if (scaleT) {
                        radius = Math.max(radius, maxd * l.fit() * 0.5f + 4f);
                        sx *= Mth.clamp(radius / (30f * sizeMul), 0.5f, 2.4f);
                        sy = sx;
                    }
                    radius *= TLConfig.TIGHTNESS.get().floatValue();
                }
                radius = Math.min(radius, 300f) * (1f + (1f - pop) * 2f);
                for (int i = 0; i < n; i++) {
                    float a = 360f * i / n + l.orbitSpeed() * time;
                    float rad = a * Mth.DEG_TO_RAD;
                    float x = ax + (n > 1 || radius > 0 ? Mth.sin(rad) * radius : 0);
                    float y = ay + (n > 1 || radius > 0 ? -Mth.cos(rad) * radius : 0);
                    draw(gg, l, x, y, (l.radial() ? a : 0f) + l.spin() * time, sx, sy);
                }
            }
            if (l.additive()) RenderSystem.defaultBlendFunc();
        }
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
        RenderSystem.disableBlend();
    }

    private static void draw(GuiGraphics gg, Themes.Layer l, float x, float y, float rot, float sx, float sy) {
        PoseStack ps = gg.pose();
        ps.pushPose();
        ps.translate(x, y, 0);
        ps.mulPose(Axis.ZP.rotationDegrees(rot));
        ps.scale(sx / 64f, sy / 64f, 1f);
        gg.blit(l.texture(), -32, -32, 64, 64, 0f, 0f, 64, 64, 64, 64);
        ps.popPose();
    }
}
