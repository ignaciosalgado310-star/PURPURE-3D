package igy.purpure.client;

import igy.purpure.PurpureMod;
import igy.purpure.network.PurpureEffectPacket;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * V15: estado/reloj del ritual en cliente.
 * Sin shake permanente: la cinematica se controla desde ClientCinematicDirectorV2.
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
            if (fx != null) {
                long expanded = (long)fx.hits + packet.hits();
                fx.hits = (int)Math.min(Integer.MAX_VALUE / 4L, expanded);
            }
        } else {
            ACTIVE.remove(packet.target());
        }
    }

    public static float effectTick(UUID playerId) {
        FX fx = ACTIVE.get(playerId);
        return fx == null ? -1.0f : fx.t;
    }

    public static double effectX(UUID playerId) {
        FX fx = ACTIVE.get(playerId);
        return fx == null ? Double.NaN : fx.x;
    }

    public static double effectY(UUID playerId) {
        FX fx = ACTIVE.get(playerId);
        return fx == null ? Double.NaN : fx.y;
    }

    public static double effectZ(UUID playerId) {
        FX fx = ACTIVE.get(playerId);
        return fx == null ? Double.NaN : fx.z;
    }

    public static int effectHits(UUID playerId) {
        FX fx = ACTIVE.get(playerId);
        return fx == null ? 0 : fx.hits;
    }

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (event.phase == TickEvent.Phase.END && mc.level != null && !mc.isPaused()) {
            ACTIVE.values().forEach(fx -> fx.t++);
        }
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
