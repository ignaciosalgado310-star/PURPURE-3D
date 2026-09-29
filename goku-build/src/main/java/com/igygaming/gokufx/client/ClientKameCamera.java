package com.igygaming.gokufx.client;

import com.igygaming.gokufx.GokuFxMod;
import com.igygaming.gokufx.KameTiming;
import com.igygaming.gokufx.network.GokuEffectPacket;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Cinematica del Kame final:
 * Goku/carga -> salida y frente viajando -> seguimiento del frente/jugador -> cleanup.
 */
@Mod.EventBusSubscriber(modid = GokuFxMod.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ClientKameCamera {
    private static final Vec3 BEAM_DIR = new Vec3(1.0D, -0.04D, 0.0D).normalize();
    private static final Vec3 SIDE_DIR = new Vec3(-BEAM_DIR.z, 0.0D, BEAM_DIR.x).normalize();
    private static final double GOKU_BACK = 15.75D;
    private static final double SOURCE_BACK = 14.0D;

    private static CameraType previousCameraType;
    private static boolean forcedThirdPerson;
    private static Vec3 smoothCamera;

    private ClientKameCamera() {}

    @SubscribeEvent
    public static void clientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) {
            restore(mc);
            return;
        }

        int raw = ClientGokuEffects.rawEffectTick(mc.player.getUUID(), GokuEffectPacket.KAMEHAMEHA);
        int hits = Math.max(1, ClientGokuEffects.hits(mc.player.getUUID(), GokuEffectPacket.KAMEHAMEHA));
        boolean active = raw >= 0 && raw <= KameTiming.totalTicks(hits) + 2;

        if (active) {
            if (!forcedThirdPerson) {
                previousCameraType = mc.options.getCameraType();
                mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
                forcedThirdPerson = true;
                smoothCamera = null;
            }
        } else {
            restore(mc);
        }
    }

    @SubscribeEvent
    public static void cameraAngles(ViewportEvent.ComputeCameraAngles event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || !forcedThirdPerson) return;

        int raw = ClientGokuEffects.rawEffectTick(mc.player.getUUID(), GokuEffectPacket.KAMEHAMEHA);
        int hits = Math.max(1, ClientGokuEffects.hits(mc.player.getUUID(), GokuEffectPacket.KAMEHAMEHA));
        if (raw < 0 || raw > KameTiming.totalTicks(hits) + 2) return;

        Vec3 frozenOrigin = ClientGokuEffects.origin(mc.player.getUUID(), GokuEffectPacket.KAMEHAMEHA);
        if (frozenOrigin == null) frozenOrigin = mc.player.position();

        Vec3 focus;
        Vec3 desired;
        Vec3 lookAt;

        if (raw < KameTiming.BEAM_START_TICK) {
            // Durante la frase/carga, Goku queda fijo aunque el objetivo vaya a moverse despues.
            Vec3 gokuFeet = frozenOrigin.add(BEAM_DIR.scale(-GOKU_BACK));
            focus = gokuFeet.add(0.0D, 1.30D, 0.0D);

            float chargeP = smoother(Mth.clamp(raw / (float) KameTiming.CHARGE_TICKS, 0.0F, 1.0F));
            double side = Mth.lerp(chargeP, 3.80D, 2.85D);
            double back = Mth.lerp(chargeP, 5.00D, 4.10D);
            double up = Mth.lerp(chargeP, 2.35D, 1.85D);

            desired = focus
                    .subtract(BEAM_DIR.scale(back))
                    .add(SIDE_DIR.scale(side))
                    .add(0.0D, up, 0.0D);
            lookAt = focus.add(BEAM_DIR.scale(1.25D)).add(0.0D, 0.10D, 0.0D);
        } else if (raw < KameTiming.FRONT_CONTACT_TICK + 10) {
            // Justo al salir: la camara deja ver la segunda esfera/frente separandose de las manos.
            Vec3 startTarget = frozenOrigin.add(0.0D, 1.15D, 0.0D);
            Vec3 source = startTarget.add(BEAM_DIR.scale(-SOURCE_BACK));

            float p = smooth(KameTiming.BEAM_START_TICK,
                    KameTiming.FRONT_CONTACT_TICK + 5.0F, raw);
            Vec3 currentFront = raw < KameTiming.FRONT_CONTACT_TICK
                    ? source.lerp(startTarget, smooth(KameTiming.BEAM_START_TICK,
                    KameTiming.FRONT_CONTACT_TICK, raw))
                    : mc.player.position().add(0.0D, 1.15D, 0.0D).add(BEAM_DIR.scale(0.65D));

            focus = currentFront;
            desired = focus
                    .subtract(BEAM_DIR.scale(Mth.lerp(p, 5.0D, 6.2D)))
                    .add(SIDE_DIR.scale(Mth.lerp(p, 4.5D, 3.5D)))
                    .add(0.0D, Mth.lerp(p, 2.65D, 2.25D), 0.0D);
            lookAt = currentFront.add(BEAM_DIR.scale(2.1D));
        } else {
            // El frente ya alcanzo al jugador: seguirlo mientras lo empuja y destruye el corredor.
            focus = mc.player.position().add(0.0D, 1.15D, 0.0D);
            desired = focus
                    .subtract(BEAM_DIR.scale(5.70D))
                    .add(SIDE_DIR.scale(3.25D))
                    .add(0.0D, 2.35D, 0.0D);
            lookAt = focus.add(BEAM_DIR.scale(1.70D));
        }

        HitResult hit = mc.level.clip(new ClipContext(
                focus, desired,
                ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE,
                mc.player
        ));
        if (hit.getType() != HitResult.Type.MISS) {
            Vec3 hitPos = hit.getLocation();
            Vec3 toward = focus.subtract(hitPos);
            desired = toward.lengthSqr() > 0.0001D
                    ? hitPos.add(toward.normalize().scale(0.38D))
                    : hitPos;
        }

        if (smoothCamera == null || smoothCamera.distanceToSqr(desired) > 324.0D) {
            smoothCamera = desired;
        } else {
            double lerp = raw < KameTiming.BEAM_START_TICK ? 0.14D : 0.19D;
            smoothCamera = smoothCamera.lerp(desired, lerp);
        }

        event.getCamera().setPosition(smoothCamera.x, smoothCamera.y, smoothCamera.z);

        Vec3 look = lookAt.subtract(smoothCamera);
        double horizontal = Math.sqrt(look.x * look.x + look.z * look.z);
        float yaw = (float)(Math.atan2(-look.x, look.z) * (180.0D / Math.PI));
        float pitch = (float)(-Math.atan2(look.y, Math.max(0.0001D, horizontal)) * (180.0D / Math.PI));
        event.setYaw(yaw);
        event.setPitch(pitch);

        float shakeStrength = raw < KameTiming.BEAM_START_TICK ? 0.10F : 0.30F;
        event.setRoll(Mth.sin(raw * 0.12F) * shakeStrength);
    }

    private static void restore(Minecraft mc) {
        if (forcedThirdPerson) {
            if (previousCameraType != null) mc.options.setCameraType(previousCameraType);
            forcedThirdPerson = false;
            previousCameraType = null;
            smoothCamera = null;
        }
    }

    private static float smooth(float from, float to, float value) {
        if (to <= from) return value >= to ? 1.0F : 0.0F;
        float p = Mth.clamp((value - from) / (to - from), 0.0F, 1.0F);
        return p * p * (3.0F - 2.0F * p);
    }

    private static float smoother(float x) {
        x = Mth.clamp(x, 0.0F, 1.0F);
        return x * x * x * (x * (x * 6.0F - 15.0F) + 10.0F);
    }
}
