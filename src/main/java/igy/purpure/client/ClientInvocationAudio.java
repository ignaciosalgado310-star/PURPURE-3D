package igy.purpure.client;

import igy.purpure.PurpureMod;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.common.Mod;

/**
 * V15: la pista del video de referencia se reproduce como audio maestro del ritual
 * desde servidor. Este antiguo disparador queda vacío para impedir voces duplicadas
 * o una segunda invocación fuera de sincronía.
 */
@Mod.EventBusSubscriber(modid = PurpureMod.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ClientInvocationAudio {
    private ClientInvocationAudio() {}
}
