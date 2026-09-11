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
 * V15 cinematic camera. Mantiene tercera persona durante TODO el viaje con
 * Hollow Purple, pero concentra el movimiento de cámara en la preparación y
 * liberación para evitar vibración, mareo y rubberband visual.
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
        if (t < 0.0f) {
            restore();
            return;
        }

        if (!controllingCamera) {
            previousCamera = mc.options.getCameraType();
            controllingCamera = true;
        }

        // Minecraft ya aplica colisión/raycast a su cámara de tercera persona.
        // Así la cámara se acerca frente a paredes y vuelve a salir al tener espacio.
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
        if (t < 0.0f) return;

        t += (float)event.getPartialTick();

        float intro = smooth(0.0f, 34.0f, t) * (1.0f - smooth(72.0f, 96.0f, t));
        float setup = smooth(70.0f, 105.0f, t) * (1.0f - smooth(138.0f, 156.0f, t));
        float fusion = smooth(138.0f, 154.0f, t) * (1.0f - smooth(184.0f, 199.0f, t));
        float launch = smooth(190.0f, 202.0f, t) * (1.0f - smooth(220.0f, 236.0f, t));

        event.setYaw(event.getYaw()
                + Mth.sin(t * 0.040f) * 1.75f * intro
                + Mth.sin(t * 0.033f) * 1.10f * setup
                + Mth.sin(t * 0.12f) * 0.45f * fusion);
        event.setPitch(event.getPitch()
                - 1.10f * intro
                - 0.65f * setup
                + 0.45f * launch);
        event.setRoll(event.getRoll()
                + Mth.sin(t * 0.075f) * 0.22f * setup
                + Mth.sin(t * 0.20f) * 0.28f * fusion);
    }

    @SubscribeEvent
    public static void overlay(RenderGuiOverlayEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        float t = ClientPurpureEffects.effectTick(mc.player.getUUID());
        if (t < 0.0f) return;

        int w = event.getWindow().getGuiScaledWidth();
        int h = event.getWindow().getGuiScaledHeight();

        float bars = smooth(0.0f, 18.0f, t);
        int barHeight = (int)(h * 0.036f * bars);
        if (barHeight > 0) {
            event.getGuiGraphics().fill(0, 0, w, barHeight, 0xB8000000);
            event.getGuiGraphics().fill(0, h - barHeight, w, h, 0xB8000000);
        }

        // Tinte solo en la fusión/liberación; durante el arrastre el mundo vuelve a verse limpio.
        float purpleTint = smooth(145.0f, 166.0f, t) * (1.0f - smooth(221.0f, 238.0f, t));
        int pa = Mth.clamp((int)(purpleTint * 11.0f), 0, 13);
        if (pa > 0) {
            event.getGuiGraphics().fill(0, 0, w, h, (pa << 24) | 0x6200E8);
        }

        float birthFlash = 1.0f - Mth.clamp(Math.abs(t - 166.0f) / 5.0f, 0.0f, 1.0f);
        float launchFlash = 1.0f - Mth.clamp(Math.abs(t - 220.0f) / 5.5f, 0.0f, 1.0f);
        int fa = Mth.clamp((int)(birthFlash * 20.0f + launchFlash * 28.0f), 0, 30);
        if (fa > 0) {
            event.getGuiGraphics().fill(0, 0, w, h, (fa << 24) | 0x9B35FF);
        }
    }

    private static float smooth(float start, float end, float value) {
        float x = Mth.clamp((value - start) / (end - start), 0.0f, 1.0f);
        return x * x * (3.0f - 2.0f * x);
    }
}
