package com.igygaming.gokufx.client;

import com.igygaming.gokufx.GokuFxMod;
import com.igygaming.gokufx.KameTiming;
import com.igygaming.gokufx.ModSounds;
import com.igygaming.gokufx.network.GokuEffectPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/**
 * Conserva el audio Kame personalizado que ya tenia el mod.
 * Solo cambia su origen espacial: carga desde Goku y, al salir el Kame,
 * sigue al jugador/impacto como antes.
 */
@Mod.EventBusSubscriber(modid = GokuFxMod.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ClientKameAudio {
    private static final Map<UUID, FullKameSound> ACTIVE = new HashMap<>();
    private static final Vec3 BEAM_DIR = new Vec3(1.0D, -0.04D, 0.0D).normalize();
    private static final double GOKU_BACK = 15.75D;

    private ClientKameAudio() {}

    public static void start(UUID target, int hits) {
        stop(target);
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        FullKameSound sound = new FullKameSound(target);
        ACTIVE.put(target, sound);
        mc.getSoundManager().play(sound);
    }

    public static void stop(UUID target) {
        FullKameSound sound = ACTIVE.remove(target);
        if (sound != null) sound.finish();
    }

    @SubscribeEvent
    public static void clientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            for (FullKameSound sound : ACTIVE.values()) sound.finish();
            ACTIVE.clear();
            return;
        }

        Iterator<Map.Entry<UUID, FullKameSound>> it = ACTIVE.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, FullKameSound> entry = it.next();
            if (ClientGokuEffects.rawEffectTick(entry.getKey(), GokuEffectPacket.KAMEHAMEHA) < 0) {
                entry.getValue().finish();
                it.remove();
            }
        }
    }

    private static AbstractClientPlayer findPlayer(UUID target) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return null;
        for (AbstractClientPlayer player : mc.level.players()) {
            if (player.getUUID().equals(target)) return player;
        }
        return null;
    }

    private static final class FullKameSound extends AbstractTickableSoundInstance {
        private final UUID target;

        private FullKameSound(UUID target) {
            super(ModSounds.KAME_INTRO.get(), SoundSource.PLAYERS, RandomSource.create());
            this.target = target;
            this.looping = false;
            this.delay = 0;
            this.attenuation = SoundInstance.Attenuation.LINEAR;
            this.relative = false;
            this.volume = 1.60F;
            this.pitch = 1.0F;
            updatePosition();
        }

        @Override
        public void tick() {
            if (ClientGokuEffects.rawEffectTick(target, GokuEffectPacket.KAMEHAMEHA) < 0) {
                stop();
                return;
            }
            updatePosition();
        }

        private void updatePosition() {
            AbstractClientPlayer player = findPlayer(target);
            if (player == null) return;
            int raw = ClientGokuEffects.rawEffectTick(target, GokuEffectPacket.KAMEHAMEHA);

            Vec3 pos;
            if (raw >= 0 && raw < KameTiming.BEAM_START_TICK) {
                pos = player.position().add(BEAM_DIR.scale(-GOKU_BACK)).add(0.0D, 1.25D, 0.0D);
            } else {
                pos = player.position().add(0.0D, 1.0D, 0.0D);
            }
            this.x = pos.x;
            this.y = pos.y;
            this.z = pos.z;
        }

        private void finish() {
            stop();
            ACTIVE.remove(target, this);
        }
    }
}
