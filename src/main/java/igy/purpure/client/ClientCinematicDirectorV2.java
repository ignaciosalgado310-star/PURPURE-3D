package igy.purpure.client;

import igy.purpure.PurpureMod;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * V8 cinematic camera. El jugador mira a Gojo desde tercera persona trasera,
 * para que Gojo permanezca enfrente y visible durante toda la preparacion.
 */
@Mod.EventBusSubscriber(modid = PurpureMod.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ClientCinematicDirectorV2 {
    private static CameraType previousCamera;
    private static boolean controllingCamera;

    private ClientCinematicDirectorV2() {}

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) {
            restore();
            return;
        }

        float t = ClientPurpureEffects.effectTick(mc.player.getUUID());
        boolean cinematic = t >= 0.0f && t <= 365.0f;
        if (!cinematic) {
            restore();
            return;
        }

        if (!controllingCamera) {
            previousCamera = mc.options.getCameraType();
            controllingCamera = true;
        }

        // SIEMPRE detras del jugador: como el servidor lo hace mirar a Gojo,
        // Gojo queda enfrente de la camara y no escondido detras.
        mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
    }

    private static void restore() {
        Minecraft mc = Minecraft.getInstance();
        if (controllingCamera && previousCamera != null) {
            mc.options.setCameraType(previousCamera);
        }
        previousCamera = null;
        controllingCamera = false;
    }

    @SubscribeEvent
    public static void camera(ViewportEvent.ComputeCameraAngles event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        float t = ClientPurpureEffects.effectTick(mc.player.getUUID());
        if (t < 0.0f || t > 365.0f) return;

        t += (float)event.getPartialTick();

        float intro = smooth(0.0f, 42.0f, t) * (1.0f - smooth(72.0f, 100.0f, t));
        float control = smooth(80.0f, 120.0f, t) * (1.0f - smooth(205.0f, 225.0f, t));
        float fusion = smooth(220.0f, 245.0f, t) * (1.0f - smooth(270.0f, 290.0f, t));
        float launch = smooth(300.0f, 340.0f, t) * (1.0f - smooth(350.0f, 365.0f, t));

        // Movimiento pequeno: no giramos tanto la camara como para sacar a Gojo del encuadre.
        event.setYaw(event.getYaw()
                + Mth.sin(t * 0.045f) * 2.4f * intro
                + Mth.sin(t * 0.035f) * 1.5f * control
                + Mth.sin(t * 0.20f) * 0.65f * fusion);
        event.setPitch(event.getPitch() - 1.5f * intro - 0.8f * control + 0.6f * launch);
        event.setRoll(event.getRoll()
                + Mth.sin(t * 0.080f) * 0.40f * control
                + Mth.sin(t * 0.31f) * 0.55f * fusion);
    }

    @SubscribeEvent
    public static void overlay(RenderGuiOverlayEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        float t = ClientPurpureEffects.effectTick(mc.player.getUUID());
        if (t < 0.0f || t > 365.0f) return;

        int w = event.getWindow().getGuiScaledWidth();
        int h = event.getWindow().getGuiScaledHeight();

        float bars = smooth(0.0f, 18.0f, t) * (1.0f - smooth(342.0f, 365.0f, t));
        int barHeight = (int)(h * 0.040f * bars);
        if (barHeight > 0) {
            event.getGuiGraphics().fill(0, 0, w, barHeight, 0xC2000000);
            event.getGuiGraphics().fill(0, h - barHeight, w, h, 0xC2000000);
        }

        // Morado SOLO cuando ya empieza la fusion real.
        float purpleTint = smooth(238.0f, 265.0f, t) * (1.0f - smooth(338.0f, 360.0f, t));
        int pa = Mth.clamp((int)(purpleTint * 13.0f), 0, 16);
        if (pa > 0) {
            event.getGuiGraphics().fill(0, 0, w, h, (pa << 24) | 0x6200E8);
        }

        // Antes era blanco. Ahora los dos flashes son morados y suaves.
        float birthFlash = 1.0f - Mth.clamp(Math.abs(t - 255.0f) / 4.5f, 0.0f, 1.0f);
        float launchFlash = 1.0f - Mth.clamp(Math.abs(t - 338.0f) / 5.5f, 0.0f, 1.0f);
        int fa = Mth.clamp((int)(birthFlash * 30.0f + launchFlash * 25.0f), 0, 34);
        if (fa > 0) {
            event.getGuiGraphics().fill(0, 0, w, h, (fa << 24) | 0xA52CFF);
        }
    }

    private static float smooth(float start, float end, float value) {
        float x = Mth.clamp((value - start) / (end - start), 0.0f, 1.0f);
        return x * x * (3.0f - 2.0f * x);
    }
}
