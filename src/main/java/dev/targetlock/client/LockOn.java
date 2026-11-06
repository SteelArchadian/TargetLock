package dev.targetlock.client;

import dev.targetlock.TLConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.NeutralMob;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.*;

/** Lock-on state machine: picks, switches and validates the target, and turns the view toward it. */
public final class LockOn {
    private static LivingEntity target;
    private static int lostSightTicks;
    private static long lockedAtNanos;
    private static String blacklistRaw = "";
    private static Set<String> blacklist = Set.of();

    /** Debug switch for scripted tests: auto-locks the best target and steps through every theme (3 s each). */
    private static final boolean TEST = Boolean.getBoolean("targetlock.test");
    private static long testStart;

    private LockOn() {}

    public static LivingEntity target() { return target; }
    /** Seconds since the current target was picked. */
    public static float age() { return (System.nanoTime() - lockedAtNanos) / 1.0e9f; }
    static boolean hideCrosshair() { return target != null && TLConfig.HIDE_CROSSHAIR.get(); }

    public static void release() { target = null; lostSightTicks = 0; }

    private static void lockTo(LivingEntity e) { target = e; lostSightTicks = 0; lockedAtNanos = System.nanoTime(); }

    // ---- per client tick ----
    static void tick(Minecraft mc) {
        Player me = mc.player;
        if (me == null || mc.level == null) { release(); return; }
        while (ClientSetup.THEME.consumeClick()) {
            me.displayClientMessage(Component.translatable("targetlock.message.theme", Themes.INSTANCE.cycle()), true);
        }
        if (mc.screen == null) {
            while (ClientSetup.LOCK.consumeClick()) {
                if (target != null && TLConfig.MODE.get() == TLConfig.Mode.TOGGLE) release();
                else if (target == null) { LivingEntity t = pickBest(me); if (t != null) lockTo(t); }
            }
            while (ClientSetup.NEXT.consumeClick()) if (target != null) switchTarget(me, +1);
            while (ClientSetup.PREV.consumeClick()) if (target != null) switchTarget(me, -1);
            if (target != null && TLConfig.MODE.get() == TLConfig.Mode.HOLD && !ClientSetup.LOCK.isDown()) release();
        }
        if (TEST && mc.screen == null) {
            if (target == null) { LivingEntity t = pickBest(me); if (t != null) { lockTo(t); testStart = System.nanoTime(); } }
            else Themes.INSTANCE.testIndex = (int) ((System.nanoTime() - testStart) / 3_000_000_000L);
        }
        headshotCheck(mc, me);
        if (target != null) validate(me);
    }

    private static int prevHurt;
    private static final List<Double> arrowY = new ArrayList<>();

    /** An arrow that was about to land at head height when damage starts breaks the lock. */
    private static void headshotCheck(Minecraft mc, Player me) {
        boolean newHit = me.hurtTime > prevHurt && me.hurtTime > 0;
        prevHurt = me.hurtTime;
        if (newHit && target != null && TLConfig.HEADSHOT_BREAK.get()) {
            double eye = me.getEyeY();
            for (double y : arrowY) if (Math.abs(y - eye) < 0.4) { release(); break; }
        }
        arrowY.clear();
        for (net.minecraft.world.entity.projectile.AbstractArrow a : mc.level.getEntitiesOfClass(
                net.minecraft.world.entity.projectile.AbstractArrow.class, me.getBoundingBox().inflate(6))) {
            if (a.getOwner() == me || a.isNoPhysics() || a.onGround()) continue;
            if (a.getDeltaMovement().lengthSqr() < 0.5) continue;
            arrowY.add(a.getY() + a.getDeltaMovement().y);
        }
    }

    private static void validate(Player me) {
        LivingEntity t = target;
        boolean dead = !t.isAlive() || t.isRemoved();
        if (dead || t.level() != me.level() || t.distanceTo(me) > TLConfig.BREAK_RANGE.get() || t.isInvisibleTo(me)) {
            release();
            if (dead && TLConfig.RETARGET.get()) { LivingEntity n = pickBest(me); if (n != null) lockTo(n); }
            return;
        }
        if (TLConfig.LOS.get() && !visible(me, t)) {
            if (++lostSightTicks > TLConfig.LOS_GRACE.get()) release();
        } else {
            lostSightTicks = 0;
        }
    }

    // ---- per rendered frame: turn the player's view ----
    static void frame(Minecraft mc) {
        LivingEntity t = target;
        Player me = mc.player;
        if (t == null || me == null || mc.screen != null || mc.isPaused()) return;
        // Eat mouse movement so it can't fight the lock.
        mc.mouseHandler.accumulatedDX = 0;
        mc.mouseHandler.accumulatedDY = 0;

        float pt = mc.getTimer().getGameTimeDeltaPartialTick(false);
        double tx = Mth.lerp(pt, t.xo, t.getX());
        double ty = Mth.lerp(pt, t.yo, t.getY()) + t.getBbHeight() * TLConfig.AIM_HEIGHT.get();
        double tz = Mth.lerp(pt, t.zo, t.getZ());
        Vec3 eye = me.getEyePosition(pt);
        double dx = tx - eye.x, dy = ty - eye.y, dz = tz - eye.z;
        float wantYaw = (float) (Mth.atan2(dz, dx) * 57.29577951308232) - 90f;
        float wantPitch = (float) -(Mth.atan2(dy, Math.sqrt(dx * dx + dz * dz)) * 57.29577951308232);

        if (TLConfig.AUTO_AIM.get() && bowDrawn(me)) {
            float[] a = ballistic(me, t, pt);
            if (a != null) { wantYaw = a[0]; wantPitch = a[1]; }
        }

        float dt = Math.min(0.1f, mc.getTimer().getRealtimeDeltaTicks() / 20f);
        float k = 1f - (float) Math.exp(-TLConfig.CAMERA_SPEED.get() * dt);
        float yaw = me.getYRot() + Mth.wrapDegrees(wantYaw - me.getYRot()) * k;
        float pitch = TLConfig.LOCK_PITCH.get() ? me.getXRot() + (wantPitch - me.getXRot()) * k : me.getXRot();
        pitch = Mth.clamp(pitch, -90f, 90f);
        me.setYRot(yaw);
        me.setXRot(pitch);
        me.yRotO = yaw;
        me.xRotO = pitch;
    }

    private static float aimSeed;
    private static boolean wasDrawn;

    private static boolean bowDrawn(Player me) {
        boolean d = me.isUsingItem() && (me.getUseItem().getItem() instanceof net.minecraft.world.item.BowItem
                || (me.getUseItem().getItem() instanceof net.minecraft.world.item.CrossbowItem));
        if (d && !wasDrawn) aimSeed = (float) (Math.random() * 2 - 1);
        wasDrawn = d;
        return d && me.getUseItem().getItem() instanceof net.minecraft.world.item.BowItem;
    }

    /** Yaw/pitch (Minecraft convention) that makes an arrow released now hit the target; null if out of reach. */
    private static float[] ballistic(Player me, LivingEntity t, float pt) {
        double v = 3.0; // aim for a full draw so the view stays steady while the bow charges
        Vec3 from = me.getEyePosition(pt).add(0, -0.1, 0);
        Vec3 vel = TLConfig.AIM_LEAD.get() ? t.getDeltaMovement() : Vec3.ZERO;
        if (t.onGround()) vel = new Vec3(vel.x, 0, vel.z);
        Vec3 pos = new Vec3(Mth.lerp(pt, t.xo, t.getX()), Mth.lerp(pt, t.yo, t.getY()) + t.getBbHeight() * 0.6, Mth.lerp(pt, t.zo, t.getZ()));
        double flight = 0, pitch = 0;
        Vec3 aim = pos;
        for (int it = 0; it < 4; it++) {
            aim = pos.add(vel.scale(flight));
            double dx = aim.x - from.x, dz = aim.z - from.z, hd = Math.sqrt(dx * dx + dz * dz), dy = aim.y - from.y;
            double lo = -80, hi = 80;
            double ft = 0;
            for (int i = 0; i < 18; i++) {
                double mid = (lo + hi) / 2, rad = Math.toRadians(mid);
                double hx = 0, hy = 0, vx = Math.cos(rad) * v, vy = Math.sin(rad) * v;
                int n = 0;
                while (hx < hd && n < 200) { hx += vx; hy += vy; vx *= 0.99; vy = vy * 0.99 - 0.05; n++; }
                if (hy < dy) lo = mid; else hi = mid;
                ft = n;
            }
            pitch = (lo + hi) / 2;
            flight = ft;
            if (hd < 0.01) break;
        }
        double dx = aim.x - from.x, dz = aim.z - from.z;
        float yaw = (float) (Mth.atan2(dz, dx) * 57.29577951308232) - 90f;
        float err = (1f - TLConfig.AIM_ACCURACY.get().floatValue()) * 5f;
        return new float[]{yaw + aimSeed * err, (float) -pitch + aimSeed * err * 0.6f};
    }

    static boolean onScroll(double delta) {
        Minecraft mc = Minecraft.getInstance();
        if (target == null || mc.player == null || mc.screen != null || delta == 0 || !TLConfig.WHEEL.get()) return false;
        switchTarget(mc.player, delta > 0 ? -1 : +1);
        return true;
    }

    // ---- selection ----
    private static boolean allowedKind(Player me, LivingEntity e) {
        if (e == me || !e.isAlive() || e.isSpectator() || e instanceof ArmorStand || e.isInvisibleTo(me)) return false;
        String raw = TLConfig.BLACKLIST.get();
        if (!raw.equals(blacklistRaw)) {
            blacklistRaw = raw;
            Set<String> s = new HashSet<>();
            for (String p : raw.split(",")) if (!p.isBlank()) s.add(p.strip().toLowerCase(Locale.ROOT));
            blacklist = s;
        }
        if (!blacklist.isEmpty() && blacklist.contains(BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()).toString())) return false;
        if (e instanceof TamableAnimal ta && ta.isOwnedBy(me)) return TLConfig.PETS.get();
        if (e instanceof Player) return TLConfig.PLAYERS.get();
        if (e instanceof Enemy) return TLConfig.HOSTILE.get();
        if (e instanceof Mob m) {
            if (m instanceof NeutralMob || m.getTarget() == me) return TLConfig.NEUTRAL.get();
            return TLConfig.PASSIVE.get();
        }
        return false;
    }

    /** One ray between the two heads; only full solid blocks (stone, logs...) block it, not glass, leaves or partial blocks. */
    static boolean visible(Player me, LivingEntity e) {
        Vec3 from = me.getEyePosition(), to = e.getEyePosition();
        double len = from.distanceTo(to);
        int steps = Math.max(1, (int) (len / 0.25));
        net.minecraft.core.BlockPos.MutableBlockPos bp = new net.minecraft.core.BlockPos.MutableBlockPos();
        for (int i = 1; i < steps; i++) {
            Vec3 p = from.lerp(to, (double) i / steps);
            bp.set(p.x, p.y, p.z);
            net.minecraft.world.level.block.state.BlockState st = me.level().getBlockState(bp);
            if (st.isSolidRender(me.level(), bp)) return false;
        }
        return true;
    }

    private static List<LivingEntity> candidates(Player me, boolean cone) {
        double range = TLConfig.RANGE.get();
        List<LivingEntity> out = new ArrayList<>();
        Vec3 eye = me.getEyePosition();
        Vec3 look = me.getLookAngle();
        double cosMax = Math.cos(Math.toRadians(TLConfig.ACQUIRE_ANGLE.get()));
        for (LivingEntity e : me.level().getEntitiesOfClass(LivingEntity.class, me.getBoundingBox().inflate(range), x -> allowedKind(me, x))) {
            if (Math.sqrt(e.getBoundingBox().distanceToSqr(eye)) > range) continue;
            if (cone && look.dot(e.getBoundingBox().getCenter().subtract(eye).normalize()) < cosMax) continue;
            if (TLConfig.LOS.get() && !visible(me, e)) continue;
            out.add(e);
        }
        return out;
    }

    private static LivingEntity pickBest(Player me) {
        Vec3 eye = me.getEyePosition();
        Vec3 look = me.getLookAngle();
        double range = TLConfig.RANGE.get();
        LivingEntity best = null;
        double bestScore = Double.MAX_VALUE;
        for (LivingEntity e : candidates(me, true)) {
            Vec3 d = e.getBoundingBox().getCenter().subtract(eye);
            double dist = d.length();
            double angle = Math.toDegrees(Math.acos(Mth.clamp(look.dot(d.normalize()), -1, 1)));
            double score = switch (TLConfig.PRIORITY.get()) {
                case NEAREST -> dist;
                case CROSSHAIR -> angle;
                case BEST -> angle / 90.0 + dist / range - (e instanceof Mob m && m.getTarget() == me ? 0.2 : 0);
            };
            if (score < bestScore) { bestScore = score; best = e; }
        }
        return best;
    }

    private static double bearing(Player me, LivingEntity e) {
        Vec3 d = e.getBoundingBox().getCenter().subtract(me.getEyePosition());
        return Math.toDegrees(Mth.atan2(d.z, d.x)) - 90.0;
    }

    /** dir +1 = next target to the right, -1 = to the left, relative to the current target as seen from the player. */
    static void switchTarget(Player me, int dir) {
        LivingEntity cur = target;
        if (cur == null) return;
        double curB = bearing(me, cur);
        LivingEntity best = null, wrap = null;
        double bestD = Double.MAX_VALUE, wrapD = -1;
        for (LivingEntity e : candidates(me, false)) {
            if (e == cur) continue;
            double delta = Mth.wrapDegrees(bearing(me, e) - curB) * dir;
            if (delta > 0.5) { if (delta < bestD) { bestD = delta; best = e; } }
            else if (-delta > wrapD) { wrapD = -delta; wrap = e; }
        }
        LivingEntity pick = best != null ? best : wrap;
        if (pick != null) lockTo(pick);
    }
}
