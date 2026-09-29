package com.igygaming.gokufx.client;

import com.igygaming.gokufx.GenkiTiming;
import com.igygaming.gokufx.GokuFxMod;
import com.igygaming.gokufx.KameTiming;
import com.igygaming.gokufx.network.GokuEffectPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

@Mod.EventBusSubscriber(modid = GokuFxMod.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ClientGokuEffects {
    private static final Map<UUID, Effect> KAME = new HashMap<>();
    private static final Map<UUID, Effect> GENKI = new HashMap<>();

    private ClientGokuEffects() {}

    public static void accept(GokuEffectPacket packet) {
        if (packet.type() == GokuEffectPacket.KAMEHAMEHA_STOP) {
            KAME.remove(packet.target());
            ClientKameAudio.stop(packet.target());
            return;
        }
        if (packet.type() == GokuEffectPacket.GENKI_DAMA_STOP) {
            GENKI.remove(packet.target());
            ClientGenkiAudio.stop(packet.target());
            return;
        }

        Map<UUID, Effect> map = packet.type() == GokuEffectPacket.GENKI_DAMA ? GENKI : KAME;
        Vec3 origin = packet.type() == GokuEffectPacket.GENKI_DAMA ? currentPosition(packet.target()) : null;
        map.put(packet.target(), new Effect(0, packet.hits(), origin));

        if (packet.type() == GokuEffectPacket.KAMEHAMEHA) {
            ClientKameAudio.start(packet.target(), packet.hits());
        } else if (packet.type() == GokuEffectPacket.GENKI_DAMA) {
            ClientGenkiAudio.start(packet.target());
        }
    }

    /** Kamehameha conserva exactamente su renderer anterior. */
    public static float effectTick(UUID target, byte type) {
        Map<UUID, Effect> map = type == GokuEffectPacket.GENKI_DAMA ? GENKI : KAME;
        Effect effect = map.get(target);
        if (effect == null) return -1.0F;
        if (type == GokuEffectPacket.KAMEHAMEHA) {
            return KameTiming.toLegacyRenderTick(effect.age, effect.hits);
        }
        if (type == GokuEffectPacket.GENKI_DAMA) return -1.0F;
        return effect.age;
    }

    public static int rawEffectTick(UUID target, byte type) {
        Map<UUID, Effect> map = type == GokuEffectPacket.GENKI_DAMA ? GENKI : KAME;
        Effect effect = map.get(target);
        return effect == null ? -1 : effect.age;
    }

    public static int hits(UUID target, byte type) {
        Map<UUID, Effect> map = type == GokuEffectPacket.GENKI_DAMA ? GENKI : KAME;
        Effect effect = map.get(target);
        return effect == null ? 0 : effect.hits;
    }

    /** Posición de inicio congelada para que la escena no se desplace al arrastrar al objetivo. */
    public static Vec3 origin(UUID target, byte type) {
        Map<UUID, Effect> map = type == GokuEffectPacket.GENKI_DAMA ? GENKI : KAME;
        Effect effect = map.get(target);
        if (effect == null) return null;
        if (effect.origin == null) effect.origin = currentPosition(target);
        return effect.origin;
    }

    @SubscribeEvent
    public static void clientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        tickKame();
        tickGenki();
    }

    private static void tickKame() {
        Iterator<Map.Entry<UUID, Effect>> iterator = KAME.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, Effect> entry = iterator.next();
            Effect effect = entry.getValue();
            effect.age++;
            if (effect.age > KameTiming.totalTicks(effect.hits) + 6) {
                ClientKameAudio.stop(entry.getKey());
                iterator.remove();
            }
        }
    }

    private static void tickGenki() {
        Iterator<Map.Entry<UUID, Effect>> iterator = GENKI.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, Effect> entry = iterator.next();
            Effect effect = entry.getValue();
            if (effect.origin == null) effect.origin = currentPosition(entry.getKey());
            effect.age++;
            if (effect.age > GenkiTiming.totalTicks(effect.hits) + 8) {
                ClientGenkiAudio.stop(entry.getKey());
                iterator.remove();
            }
        }
    }

    private static Vec3 currentPosition(UUID target) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return null;
        for (AbstractClientPlayer player : mc.level.players()) {
            if (player.getUUID().equals(target)) return player.position();
        }
        return null;
    }

    private static final class Effect {
        int age;
        final int hits;
        Vec3 origin;

        Effect(int age, int hits, Vec3 origin) {
            this.age = age;
            this.hits = hits;
            this.origin = origin;
        }
    }
}
