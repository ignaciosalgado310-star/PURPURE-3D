package com.igygaming.gokufx.client;

import com.igygaming.gokufx.GenkiTiming;
import com.igygaming.gokufx.GokuFxMod;
import com.igygaming.gokufx.network.GokuEffectPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Pantallazo azul corto y limpio al impacto de la Genkidama. */
@Mod.EventBusSubscriber(modid = GokuFxMod.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ClientGenkiFlash {
    private ClientGenkiFlash() {}

    @SubscribeEvent
    public static void onOverlay(RenderGuiOverlayEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        int raw = ClientGokuEffects.rawEffectTick(mc.player.getUUID(), GokuEffectPacket.GENKI_DAMA);
        if (raw < GenkiTiming.IMPACT_TICK || raw > GenkiTiming.IMPACT_TICK + 18) return;

        float age = raw - GenkiTiming.IMPACT_TICK + event.getPartialTick();
        float p = Mth.clamp(age / 18.0F, 0.0F, 1.0F);
        float envelope = 1.0F - (float) Mth.smoothstep(p);
        int alpha = Mth.clamp((int) (215.0F * envelope), 0, 215);
        int color = (alpha << 24) | 0x168DFF;

        int w = mc.getWindow().getGuiScaledWidth();
        int h = mc.getWindow().getGuiScaledHeight();
        event.getGuiGraphics().fill(0, 0, w, h, color);
    }
}
