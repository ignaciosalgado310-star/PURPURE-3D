package com.igygaming.gokufx.client;

import com.igygaming.gokufx.GenkiTiming;
import com.igygaming.gokufx.ModSounds;
import com.igygaming.gokufx.network.GokuEffectPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.util.Mth;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Charge audio anchored to Goku's position during the rebuilt Genki scene. */
public final class ClientGenkiAudio {
    private static final Map<UUID, ChargeSound> ACTIVE = new HashMap<>();

    private ClientGenkiAudio() {}

    public static void start(UUID target) {
        stop(target);

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;

        ChargeSound sound = new ChargeSound(target);
        ACTIVE.put(target, sound);
        mc.getSoundManager().play(sound);
    }

    public static void stop(UUID target) {
        ChargeSound sound = ACTIVE.remove(target);
        if (sound != null) {
            sound.finish();
        }
    }

    private static AbstractClientPlayer findPlayer(UUID target) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return null;

        for (AbstractClientPlayer player : mc.level.players()) {
            if (player.getUUID().equals(target)) {
                return player;
            }
        }

        return null;
    }

    private static final class ChargeSound extends AbstractTickableSoundInstance {
        private final UUID target;

        private ChargeSound(UUID target) {
            super(
                    ModSounds.GENKI_CHARGE.get(),
                    SoundSource.PLAYERS,
                    RandomSource.create()
            );

            this.target = target;
            this.looping = false;
            this.delay = 0;
            this.attenuation = SoundInstance.Attenuation.LINEAR;
            this.relative = false;
            this.volume = 1.45F;
            this.pitch = 0.94F;

            updatePosition();
        }

        @Override
        public void tick() {
            int raw = ClientGokuEffects.rawEffectTick(
                    target,
                    GokuEffectPacket.GENKI_DAMA
            );

            if (raw < 0 || raw > GenkiTiming.CHARGE_TICKS + 3) {
                finish();
                return;
            }

            float progress = Mth.clamp(raw / (float) GenkiTiming.CHARGE_TICKS, 0.0F, 1.0F);
            this.volume = 1.45F + progress * 0.55F;
            this.pitch = 0.94F + progress * 0.06F;
            updatePosition();
        }

        private void updatePosition() {
            AbstractClientPlayer player = findPlayer(target);
            if (player == null) return;

            this.x = player.getX() + GenkiTiming.GOKU_X;
            this.y = player.getY() + GenkiTiming.GOKU_Y + 3.2D;
            this.z = player.getZ();
        }

        private void finish() {
            stop();
            ACTIVE.remove(target, this);
        }
    }
}
