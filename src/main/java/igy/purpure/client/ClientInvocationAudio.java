package igy.purpure.client;

import igy.purpure.PurpureMod;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.UUID;

/**
 * Reproduce la frase corta de invocacion justo cuando Hollow Purple empieza
 * a lanzarse. Solo audio cliente: no toca dano, totems ni logica del ritual.
 */
@Mod.EventBusSubscriber(modid = PurpureMod.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ClientInvocationAudio {
    private static final float INVOKE_TICK = 218.0f;
    private static final Set<UUID> PLAYED = new HashSet<>();

    private ClientInvocationAudio() {}

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.isPaused()) {
            if (mc.level == null) PLAYED.clear();
            return;
        }

        Set<UUID> activeNow = new HashSet<>();

        for (AbstractClientPlayer target : mc.level.players()) {
            float t = ClientPurpureEffects.effectTick(target.getUUID());
            if (t < 0.0f) continue;

            UUID id = target.getUUID();
            activeNow.add(id);

            if (t >= INVOKE_TICK && PLAYED.add(id)) {
                mc.level.playLocalSound(
                        target.getX() + 4.0,
                        target.getY() + 1.55,
                        target.getZ(),
                        PurpureMod.HOLLOW_PURPLE_INVOKE.get(),
                        SoundSource.MASTER,
                        5.0f,
                        1.0f,
                        false
                );
            }
        }

        Iterator<UUID> it = PLAYED.iterator();
        while (it.hasNext()) {
            if (!activeNow.contains(it.next())) it.remove();
        }
    }
}
