package igy.purpure.client;

import igy.purpure.PurpureMod;
import igy.purpure.network.PurpureEffectPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Solo mantiene el reloj y estado del ritual en cliente.
 * V8: el renderer antiguo fue eliminado por completo para que no vuelva a aparecer
 * la columna vertical ni las esferas duplicadas.
 */
@Mod.EventBusSubscriber(modid = PurpureMod.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ClientPurpureEffects {
    private static final Map<UUID, FX> ACTIVE = new LinkedHashMap<>();

    private ClientPurpureEffects() {}

    public static void accept(PurpureEffectPacket packet) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || !mc.level.dimension().location().toString().equals(packet.dimension())) return;

        if (packet.mode() == PurpureEffectPacket.START) {
            ACTIVE.put(packet.target(), new FX(packet));
        } else if (packet.mode() == PurpureEffectPacket.EXTEND) {
            FX fx = ACTIVE.get(packet.target());
            if (fx != null) fx.hits = Math.min(2000, fx.hits + packet.hits());
        } else {
            ACTIVE.remove(packet.target());
        }
    }

    public static float effectTick(UUID playerId) {
        FX fx = ACTIVE.get(playerId);
        return fx == null ? -1.0f : fx.t;
    }

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (event.phase == TickEvent.Phase.END && mc.level != null && !mc.isPaused()) {
            ACTIVE.values().forEach(fx -> fx.t++);
        }
    }

    @SubscribeEvent
    public static void shake(ViewportEvent.ComputeCameraAngles event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        for (FX fx : ACTIVE.values()) {
            float t = fx.t + (float) event.getPartialTick();
            double distanceSq = mc.player.distanceToSqr(fx.x, fx.y, fx.z);
            if (distanceSq > 3600.0 || t < 220.0f) continue;

            float distanceFactor = (float)Math.max(0.0, 1.0 - Math.sqrt(distanceSq) / 60.0);
            float fusion = smooth(225.0f, 250.0f, t) * (1.0f - smooth(260.0f, 280.0f, t));
            float impact = smooth(330.0f, 342.0f, t) * (1.0f - smooth(350.0f, 365.0f, t));
            float power = fusion * 0.26f + impact * 0.70f;

            event.setYaw(event.getYaw() + Mth.sin(t * 0.91f) * 0.55f * distanceFactor * power);
            event.setPitch(event.getPitch() + Mth.cos(t * 1.11f) * 0.45f * distanceFactor * power);
            event.setRoll(event.getRoll() + Mth.sin(t * 0.67f) * 0.75f * distanceFactor * power);
        }
    }

    private static float smooth(float start, float end, float value) {
        float x = Mth.clamp((value - start) / (end - start), 0.0f, 1.0f);
        return x * x * (3.0f - 2.0f * x);
    }

    private static final class FX {
        final double x;
        final double y;
        final double z;
        final long seed;
        int hits;
        int t;

        FX(PurpureEffectPacket packet) {
            x = packet.x();
            y = packet.y();
            z = packet.z();
            seed = packet.seed();
            hits = packet.hits();
        }
    }
}
