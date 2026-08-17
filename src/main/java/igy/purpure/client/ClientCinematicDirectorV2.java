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

@Mod.EventBusSubscriber(modid = PurpureMod.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ClientCinematicDirectorV2 {
    private static final float TIME_SCALE = 0.68f;
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

        float raw = ClientPurpureEffects.effectTick(mc.player.getUUID());
        boolean cinematic = raw >= 0.0f && raw <= 345.0f;
        if (!cinematic) {
            restore();
            return;
        }

        if (!controllingCamera) {
            previousCamera = mc.options.getCameraType();
            controllingCamera = true;
        }

        float t = raw * TIME_SCALE;
        mc.options.setCameraType(t < 116.0f ? CameraType.THIRD_PERSON_FRONT : CameraType.THIRD_PERSON_BACK);
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
        float raw = ClientPurpureEffects.effectTick(mc.player.getUUID());
        if (raw < 0.0f || raw > 345.0f) return;

        float t = (raw + (float) event.getPartialTick()) * TIME_SCALE;
        float intro = smooth(0.0f, 42.0f, t) * (1.0f - smooth(66.0f, 86.0f, t));
        float control = smooth(52.0f, 82.0f, t) * (1.0f - smooth(118.0f, 140.0f, t));
        float fusion = smooth(112.0f, 144.0f, t) * (1.0f - smooth(168.0f, 188.0f, t));
        float launch = smooth(166.0f, 205.0f, t);

        event.setYaw(event.getYaw()
                + Mth.sin(t * 0.043f) * 7.0f * intro
                + Mth.sin(t * 0.056f) * 5.2f * control
                + Mth.sin(t * 0.19f) * 1.0f * fusion);
        event.setPitch(event.getPitch() - 2.0f * intro - 1.3f * control + 1.0f * launch);
        event.setRoll(event.getRoll()
                + Mth.sin(t * 0.076f) * 0.7f * control
                + Mth.sin(t * 0.31f) * 0.65f * fusion);
    }

    @SubscribeEvent
    public static void overlay(RenderGuiOverlayEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        float raw = ClientPurpureEffects.effectTick(mc.player.getUUID());
        if (raw < 0.0f || raw > 345.0f) return;
        float t = raw * TIME_SCALE;

        int w = event.getWindow().getGuiScaledWidth();
        int h = event.getWindow().getGuiScaledHeight();

        float bars = smooth(0.0f, 16.0f, t) * (1.0f - smooth(204.0f, 230.0f, t));
        int barHeight = (int)(h * 0.045f * bars);
        if (barHeight > 0) {
            event.getGuiGraphics().fill(0, 0, w, barHeight, 0xC9000000);
            event.getGuiGraphics().fill(0, h - barHeight, w, h, 0xC9000000);
        }

        // Tinte mucho mas suave que V6/V7 para no tapar a Gojo ni meter la camara en una pared morada.
        float purple = smooth(132.0f, 166.0f, t) * (1.0f - smooth(204.0f, 228.0f, t));
        int pa = Mth.clamp((int)(purple * 18.0f), 0, 22);
        if (pa > 0) {
            event.getGuiGraphics().fill(0, 0, w, h, (pa << 24) | 0x6500E8);
        }

        // Flash corto en el nacimiento y otro pequeno al lanzamiento.
        float birthFlash = 1.0f - Mth.clamp(Math.abs(t - 148.0f) / 4.0f, 0.0f, 1.0f);
        float launchFlash = 1.0f - Mth.clamp(Math.abs(t - 202.0f) / 5.5f, 0.0f, 1.0f);
        int wa = Mth.clamp((int)(birthFlash * 46.0f + launchFlash * 34.0f), 0, 54);
        if (wa > 0) {
            event.getGuiGraphics().fill(0, 0, w, h, (wa << 24) | 0xFFFFFF);
        }
    }

    private static float smooth(float start, float end, float value) {
        float x = Mth.clamp((value - start) / (end - start), 0.0f, 1.0f);
        return x * x * (3.0f - 2.0f * x);
    }
}
