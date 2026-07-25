package myau.module.modules;

import myau.module.Module;
import myau.property.properties.BooleanProperty;
import myau.property.properties.IntProperty;

public class Particles extends Module {
    public final IntProperty multiplier;
    public final BooleanProperty alwaysSharpness;
    public final BooleanProperty alwaysCriticals;
    public final BooleanProperty ignoreHurtTime;
    public final BooleanProperty forceParticles;

    public Particles() {
        super("Particles", false);
        this.multiplier = new IntProperty("Multiplier", 2, 0, 10);
        this.alwaysSharpness = new BooleanProperty("Always-sharpness", false);
        this.alwaysCriticals = new BooleanProperty("Always-criticals", false);
        this.ignoreHurtTime = new BooleanProperty("Ignore-hurt-time", true);
        this.forceParticles = new BooleanProperty("Force-particles", true);
    }

    public static boolean shouldOverrideParticles() {
        Particles particles = (Particles) myau.Myau.moduleManager.modules.get(Particles.class);
        return particles != null && particles.isEnabled();
    }

    public static int getCriticalsMultiplier(boolean should) {
        Particles particles = (Particles) myau.Myau.moduleManager.modules.get(Particles.class);
        if (particles == null || !particles.isEnabled()) return should ? 1 : 0;
        if (particles.multiplier.getValue() == 0) return 0;
        if (!should && !particles.alwaysCriticals.getValue()) return 0;
        return particles.multiplier.getValue();
    }

    public static int getSharpnessMultiplier(boolean should) {
        Particles particles = (Particles) myau.Myau.moduleManager.modules.get(Particles.class);
        if (particles == null || !particles.isEnabled()) return should ? 1 : 0;
        if (particles.multiplier.getValue() == 0) return 0;
        if (!should && !particles.alwaysSharpness.getValue()) return 0;
        return particles.multiplier.getValue();
    }

    public static boolean alwaysCriticals() {
        Particles particles = (Particles) myau.Myau.moduleManager.modules.get(Particles.class);
        return particles != null && particles.isEnabled() && particles.alwaysCriticals.getValue();
    }

    public static boolean alwaysSharpness() {
        Particles particles = (Particles) myau.Myau.moduleManager.modules.get(Particles.class);
        return particles != null && particles.isEnabled() && particles.alwaysSharpness.getValue();
    }

    public static boolean shouldIgnoreHurtTime() {
        Particles particles = (Particles) myau.Myau.moduleManager.modules.get(Particles.class);
        return particles != null && particles.isEnabled() && particles.ignoreHurtTime.getValue();
    }

    public static boolean shouldForceParticles() {
        Particles particles = (Particles) myau.Myau.moduleManager.modules.get(Particles.class);
        return particles != null && particles.isEnabled() && particles.forceParticles.getValue();
    }
}