package com.igygaming.gokufx;

/**
 * Una sola fuente de verdad para la sincronización servidor/cliente del Kamehameha.
 * La descarga comienza sobre el golpe fuerte del audio proporcionado (~9.0 s).
 */
public final class KameTiming {
    public static final int CHARGE_TICKS = 180;          // 9.0 s
    public static final int MIN_BEAM_TICKS = 72;         // total mínimo ~12.60 s, igual al audio completo
    public static final int TICKS_PER_TOTEM = 2;
    public static final int BEAM_START_TICK = CHARGE_TICKS + 1;
    // El frente visible tarda unos pocos ticks en separarse de las manos y alcanzar al objetivo.
    public static final int FRONT_CONTACT_DELAY_TICKS = 3;
    public static final int FRONT_CONTACT_TICK = BEAM_START_TICK + FRONT_CONTACT_DELAY_TICKS;
    public static final int FADE_TICKS = 10;

    public static final int AUDIO_SUSTAIN_START_TICK = 184;
    public static final int AUDIO_OUTRO_TICKS = 24;

    private KameTiming() {}

    public static int beamTicks(int hits) {
        long scaled = (long) Math.max(1, hits) * (long) TICKS_PER_TOTEM;
        long safeMax = Integer.MAX_VALUE - (long) CHARGE_TICKS - 64L;
        return (int) Math.min(safeMax, Math.max((long) MIN_BEAM_TICKS, scaled));
    }

    public static int totalTicks(int hits) {
        long total = (long) CHARGE_TICKS + (long) beamTicks(hits);
        return (int) Math.min(Integer.MAX_VALUE - 32L, total);
    }

    public static int hitStartTick() {
        return FRONT_CONTACT_TICK;
    }

    public static int hitEndTick(int hits) {
        return Math.max(hitStartTick(), totalTicks(hits) - FADE_TICKS - 1);
    }

    public static int outroStartTick(int hits) {
        return Math.max(AUDIO_SUSTAIN_START_TICK, totalTicks(hits) - AUDIO_OUTRO_TICKS);
    }

    public static float toLegacyRenderTick(int rawAge, int hits) {
        if (rawAge < 0) return -1.0F;

        if (rawAge <= CHARGE_TICKS) {
            return 60.0F * (rawAge / (float) CHARGE_TICKS);
        }

        int beamTicks = beamTicks(hits);
        int elapsed = rawAge - CHARGE_TICKS;
        if (elapsed <= 16) {
            return 60.0F + elapsed;
        }

        int fadeStartElapsed = Math.max(17, beamTicks - FADE_TICKS);
        if (elapsed < fadeStartElapsed) {
            float p = (elapsed - 16.0F) / Math.max(1.0F, fadeStartElapsed - 16.0F);
            return 77.0F + 70.0F * p;
        }

        float fadeP = (elapsed - fadeStartElapsed) / (float) Math.max(1, FADE_TICKS);
        return 148.0F + 10.0F * Math.min(1.0F, Math.max(0.0F, fadeP));
    }
}
