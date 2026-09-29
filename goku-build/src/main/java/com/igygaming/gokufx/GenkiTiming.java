package com.igygaming.gokufx;

/** Timeline sincronizada con el audio completo enviado para la Genki Dama. */
public final class GenkiTiming {
    public static final long AUDIO_DURATION_MS = 9910L;
    public static final int CHARGE_TICKS = (int) Math.ceil(AUDIO_DURATION_MS / 50.0D); // 199

    public static final int THROW_START_TICK = CHARGE_TICKS + 1;
    public static final int APPROACH_TICKS = 24;
    public static final int CONTACT_TICK = THROW_START_TICK + APPROACH_TICKS;
    public static final int DRAG_TICKS = 12;
    public static final int IMPACT_TICK = CONTACT_TICK + DRAG_TICKS;

    public static final int MIN_ACTIVE_TICKS = 118;
    public static final int MAX_CINEMATIC_ACTIVE_TICKS = 2400;
    public static final int TICKS_PER_TOTEM = 2;
    public static final int MAX_TOTEMS_PER_TICK = 64;
    public static final int IMPACT_HOLD_TICKS = 68;
    public static final int FADE_TICKS = 30;

    public static final double GOKU_X = -14.0D;
    public static final double GOKU_Y = 4.2D;
    public static final double SPHERE_Y = 17.2D; // radio final 10.4 + borde inferior fijo 6.8
    public static final double CONTACT_Y = 2.35D;
    // La Genkidama impacta al objetivo donde fue activada; no lo arrastra por el mapa.
    public static final double DRAG_DISTANCE = 0.0D;
    public static final float FINAL_RADIUS = 10.4F;

    private GenkiTiming() {}

    public static int activeTicks(int hits) {
        long safeHits = Math.max(1L, (long) hits);
        long visibleCadence = safeHits * TICKS_PER_TOTEM;
        long batchedMinimum = (safeHits + MAX_TOTEMS_PER_TICK - 1L) / MAX_TOTEMS_PER_TICK;
        long cinematic = Math.max(MIN_ACTIVE_TICKS, Math.min(MAX_CINEMATIC_ACTIVE_TICKS, visibleCadence));
        return (int) Math.min(Integer.MAX_VALUE / 4L, Math.max(cinematic, batchedMinimum));
    }

    public static int hitStartTick() {
        // Totem damage begins only after the visible impact/explosion.
        return IMPACT_TICK + 2;
    }

    public static int hitEndTick(int hits) {
        return safeAdd(hitStartTick(), activeTicks(hits));
    }

    public static int fadeStartTick(int hits) {
        return Math.max(IMPACT_TICK + IMPACT_HOLD_TICKS, safeAdd(hitEndTick(hits), 8));
    }

    public static int totalTicks(int hits) {
        return safeAdd(fadeStartTick(hits), FADE_TICKS);
    }

    public static float chargeProgress(float age) {
        return clamp(age / CHARGE_TICKS);
    }

    public static float approachProgress(float age) {
        return clamp((age - THROW_START_TICK) / (float) APPROACH_TICKS);
    }

    public static float dragProgress(float age) {
        return clamp((age - CONTACT_TICK) / (float) DRAG_TICKS);
    }

    public static float activeProgress(float age, int hits) {
        return clamp((age - hitStartTick()) / (float) Math.max(1, activeTicks(hits)));
    }

    public static float fade(float age, int hits) {
        int start = fadeStartTick(hits);
        if (age <= start) return 1.0F;
        return 1.0F - clamp((age - start) / (float) FADE_TICKS);
    }

    public static float smooth(float p) {
        p = clamp(p);
        return p * p * (3.0F - 2.0F * p);
    }

    public static float smoother(float p) {
        p = clamp(p);
        return p * p * p * (p * (p * 6.0F - 15.0F) + 10.0F);
    }

    public static float heavy(float p) {
        p = clamp(p);
        return smoother((float) Math.pow(p, 1.28D));
    }

    public static float dragEase(float p) {
        p = clamp(p);
        if (p < 0.16F) {
            return 0.16F * smoother(p / 0.16F);
        }
        return 0.16F + 0.84F * smoother((p - 0.16F) / 0.84F);
    }

    private static int safeAdd(int a, int b) {
        long v = (long) a + b;
        return (int) Math.min(Integer.MAX_VALUE / 2L, v);
    }

    private static float clamp(float v) {
        return Math.max(0.0F, Math.min(1.0F, v));
    }
}
