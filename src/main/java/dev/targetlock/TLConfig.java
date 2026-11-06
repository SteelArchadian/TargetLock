package dev.targetlock;

import net.neoforged.neoforge.common.ModConfigSpec;

public final class TLConfig {
    public enum Mode { HOLD, TOGGLE }
    public enum Priority { BEST, NEAREST, CROSSHAIR }

    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.EnumValue<Mode> MODE;
    public static final ModConfigSpec SSPEC;
    public static final Safe<Double> RANGE, BREAK_RANGE;
    public static final Safe<Integer> ACQUIRE_ANGLE, LOS_GRACE;
    public static final ModConfigSpec.EnumValue<Priority> PRIORITY;
    public static final ModConfigSpec.BooleanValue RETARGET, WHEEL;
    public static final Safe<Boolean> LOS, HEADSHOT_BREAK;
    public static final Safe<Boolean> HOSTILE, NEUTRAL, PASSIVE, PLAYERS, PETS;
    public static final Safe<String> BLACKLIST;
    public static final ModConfigSpec.DoubleValue CAMERA_SPEED, AIM_HEIGHT;
    public static final ModConfigSpec.BooleanValue LOCK_PITCH, LETTERBOX, HIDE_CROSSHAIR;
    public static final ModConfigSpec.ConfigValue<String> THEME, COLOR;
    public static final ModConfigSpec.DoubleValue SIZE, TIGHTNESS, OPACITY, ANIM_SPEED;
    public static final ModConfigSpec.BooleanValue SCALE_WITH_TARGET;
    public static final ModConfigSpec.BooleanValue POP_IN;
    public static final Safe<Boolean> AUTO_AIM, AIM_LEAD;
    public static final Safe<Double> AIM_ACCURACY;

    /** A SERVER-config value that falls back to its default when no server config is loaded (e.g. a server without this mod). */
    public static final class Safe<T> {
        private final ModConfigSpec.ConfigValue<T> v;
        Safe(ModConfigSpec.ConfigValue<T> v) { this.v = v; }
        public T get() {
            try { return v.get(); } catch (IllegalStateException e) { return v.getDefault(); }
        }
    }

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();
        b.push("general");
        MODE = b.comment("HOLD: stay locked while the key is held. TOGGLE: press once to lock, again to release.")
                .defineEnum("mode", Mode.TOGGLE);
        PRIORITY = b.comment("BEST: balances closeness to your crosshair and distance. NEAREST: closest mob. CROSSHAIR: the one nearest your crosshair.")
                .defineEnum("priority", Priority.BEST);
        RETARGET = b.comment("When the target dies, immediately lock onto the next best one instead of releasing.")
                .define("retargetOnDeath", true);
        WHEEL = b.comment("While locked, scrolling the mouse wheel switches between targets (instead of changing hotbar slot).")
                .define("wheelSwitch", true);
        b.pop();
        b.push("camera");
        CAMERA_SPEED = b.comment("How fast the view turns toward the target. Low is lazy and smooth, high is snappy (60 = almost instant).")
                .defineInRange("cameraSpeed", 14.0, 1.0, 60.0);
        LOCK_PITCH = b.comment("Also tilt the view up and down to follow the target (off keeps your pitch).")
                .define("lockPitch", true);
        AIM_HEIGHT = b.comment("Where on the target the camera aims: 0 = feet, 0.5 = middle, 1 = top of head.")
                .defineInRange("aimHeight", 0.6, 0.0, 1.0);
        LETTERBOX = b.comment("Show Zelda-style black bars at the top and bottom of the screen while locked.")
                .define("letterbox", false);
        HIDE_CROSSHAIR = b.comment("Hide the normal crosshair while locked on.").define("hideCrosshair", true);
        b.pop();
        b.push("reticle");
        THEME = b.comment("Reticle look: targetlock:zelda (four triangles circling the target) or targetlock:arrow (arrow above its head). "
                        + "Resource packs can add more (assets/<namespace>/reticles/<name>.json). "
                        + "Bind the Cycle reticle theme key to switch in game.")
                .define("theme", "targetlock:zelda");
        COLOR = b.comment("Colour override as #RRGGBB (e.g. #FF4040). Leave empty to use the theme's own colours.")
                .define("color", "");
        SCALE_WITH_TARGET = b.comment("Big creatures get a bigger reticle, small ones a smaller one. Off = one fixed size for everything.")
                .define("scaleWithTarget", true);
        TIGHTNESS = b.comment("How close the circling triangles hug the target. Lower = tighter, higher = wider (1 = default).")
                .defineInRange("tightness", 1.0, 0.5, 2.5);
        SIZE = b.comment("Reticle size multiplier.").defineInRange("size", 1.0, 0.25, 4.0);
        OPACITY = b.comment("Reticle opacity (1 = solid).").defineInRange("opacity", 1.0, 0.1, 1.0);
        ANIM_SPEED = b.comment("Speed multiplier for reticle animation (0 = still).").defineInRange("animSpeed", 1.0, 0.0, 4.0);
        POP_IN = b.comment("Reticle zooms in when you lock on.").define("popIn", true);
        b.pop();
        SPEC = b.build();

        // Server rules: per world, editable only by an operator (or in singleplayer); fall back to defaults on servers without the mod.
        ModConfigSpec.Builder s = new ModConfigSpec.Builder();
        s.push("limits");
        RANGE = new Safe<>(s.comment("How far away a target can be when you press the lock key.")
                .defineInRange("range", 24.0, 4.0, 128.0));
        BREAK_RANGE = new Safe<>(s.comment("The lock breaks when the target gets farther than this.")
                .defineInRange("breakRange", 32.0, 4.0, 192.0));
        ACQUIRE_ANGLE = new Safe<>(s.comment("Only targets within this many degrees of where you look can be picked (180 = anything around you).")
                .defineInRange("acquireAngle", 75, 5, 180));
        LOS = new Safe<>(s.comment("Only lock onto targets with a clear line between your head and theirs, and drop the lock when solid blocks hide them.")
                .define("requireLineOfSight", true));
        LOS_GRACE = new Safe<>(s.comment("How long (in ticks, 20 = 1 second) the target may stay hidden behind solid blocks before the lock breaks.")
                .defineInRange("losGraceTicks", 40, 0, 400));
        HEADSHOT_BREAK = new Safe<>(s.comment("Getting hit in the head by an arrow breaks the lock (you flinch).")
                .define("headshotBreaks", true));
        s.pop();
        s.push("targets");
        HOSTILE = new Safe<>(s.comment("Monsters and other hostile mobs.").define("hostile", true));
        NEUTRAL = new Safe<>(s.comment("Mobs that fight back only when provoked (wolves, endermen, bees, golems...) or are currently hunting you.")
                .define("neutral", true));
        PASSIVE = new Safe<>(s.comment("Animals, villagers and other harmless mobs.").define("passive", false));
        PLAYERS = new Safe<>(s.comment("Other players.").define("players", false));
        PETS = new Safe<>(s.comment("Your own tamed pets.").define("pets", false));
        BLACKLIST = new Safe<>(s.comment("Entity ids that can never be targeted, separated by commas (e.g. minecraft:bat, minecraft:silverfish).")
                .define("blacklist", "minecraft:bat"));
        s.pop();
        s.push("bow");
        AUTO_AIM = new Safe<>(s.comment("While locked on, bows aim for you: the shot arcs to hit the target.").define("autoAim", true));
        AIM_LEAD = new Safe<>(s.comment("Aim ahead of moving targets.").define("lead", true));
        AIM_ACCURACY = new Safe<>(s.comment("How accurate the auto-aim is. 1 = perfect, lower = shots drift off more.")
                .defineInRange("accuracy", 0.85, 0.0, 1.0));
        s.pop();
        SSPEC = s.build();
    }

    private TLConfig() {}
}
