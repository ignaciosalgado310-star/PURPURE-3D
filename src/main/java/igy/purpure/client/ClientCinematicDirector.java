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
public final class ClientCinematicDirector {
    private static CameraType previousCamera;
    private static boolean controllingCamera;

    private ClientCinematicDirector() {}

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) {
            restoreCamera();
            return;
        }

        float t = ClientPurpureEffects.effectTick(mc.player.getUUID());
        boolean cinematic = t >= 0.0f && t <= 205.0f;

        if (cinematic) {
            if (!controllingCamera) {
                previousCamera = mc.options.getCameraType();
                controllingCamera = true;
            }
            mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
        } else {
            restoreCamera();
        }
    }

    private static void restoreCamera() {
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
        if (t < 0.0f || t > 205.0f) return;

        t += (float) event.getPartialTick();
        float intro = smooth(0.0f, 42.0f, t) * (1.0f - smooth(52.0f, 72.0f, t));
        float orbit = smooth(42.0f, 72.0f, t) * (1.0f - smooth(98.0f, 118.0f, t));
        float fusion = smooth(102.0f, 130.0f, t) * (1.0f - smooth(146.0f, 162.0f, t));
        float finish = smooth(158.0f, 190.0f, t);

        event.setYaw(event.getYaw()
                + Mth.sin(t * 0.047f) * 12.5f * intro
                + Mth.sin(t * 0.061f) * 8.5f * orbit
                + Mth.sin(t * 0.19f) * 1.6f * fusion);
        event.setPitch(event.getPitch() - 3.5f * intro - 2.2f * orbit + 1.6f * finish);
        event.setRoll(event.getRoll()
                + Mth.sin(t * 0.083f) * 1.3f * orbit
                + Mth.sin(t * 0.34f) * 1.0f * fusion);
    }

    @SubscribeEvent
    public static void overlay(RenderGuiOverlayEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        float t = ClientPurpureEffects.effectTick(mc.player.getUUID());
        if (t < 0.0f || t > 205.0f) return;

        int w = event.getWindow().getGuiScaledWidth();
        int h = event.getWindow().getGuiScaledHeight();

        float bars = smooth(0.0f, 16.0f, t) * (1.0f - smooth(174.0f, 202.0f, t));
        int barHeight = (int) (h * 0.07f * bars);
        if (barHeight > 0) {
            event.getGuiGraphics().fill(0, 0, w, barHeight, 0xD8000000);
            event.getGuiGraphics().fill(0, h - barHeight, w, h, 0xD8000000);
        }

        float fusion = smooth(104.0f, 132.0f, t) * (1.0f - smooth(148.0f, 164.0f, t));
        float impact = smooth(178.0f, 190.0f, t) * (1.0f - smooth(196.0f, 205.0f, t));
        int purpleAlpha = Mth.clamp((int) (fusion * 30.0f + impact * 46.0f), 0, 64);
        if (purpleAlpha > 0) {
            event.getGuiGraphics().fill(0, 0, w, h, (purpleAlpha << 24) | 0x6F00FF);
        }

        float whiteFlash = 1.0f - Mth.clamp(Math.abs(t - 139.0f) / 4.5f, 0.0f, 1.0f);
        int whiteAlpha = Mth.clamp((int) (whiteFlash * 65.0f), 0, 65);
        if (whiteAlpha > 0) {
            event.getGuiGraphics().fill(0, 0, w, h, (whiteAlpha << 24) | 0xFFFFFF);
        }
    }

    private static float smooth(float start, float end, float value) {
        float x = Mth.clamp((value - start) / (end - start), 0.0f, 1.0f);
        return x * x * (3.0f - 2.0f * x);
    }
}
