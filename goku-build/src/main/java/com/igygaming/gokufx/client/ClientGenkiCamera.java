package com.igygaming.gokufx.client;

import com.igygaming.gokufx.GenkiTiming;
import com.igygaming.gokufx.GokuFxMod;
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
 * Cinematic director for /genki. Charge timing stays untouched; the camera
 * alternates between Goku, the victim and wide compositions, then follows the
 * curved drag across the map.
 */
@Mod.EventBusSubscriber(modid = GokuFxMod.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ClientGenkiCamera {
    private static CameraType previousCameraType;
    private static boolean forcedThirdPerson;
    private static Vec3 smoothCamera;
    private static Integer lastShot;

    private ClientGenkiCamera() {}

    @SubscribeEvent
    public static void clientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) {
            restore(mc);
            return;
        }

        int kame = ClientGokuEffects.rawEffectTick(mc.player.getUUID(), GokuEffectPacket.KAMEHAMEHA);
        if (kame >= 0) {
            restore(mc);
            return;
        }

        int raw = ClientGokuEffects.rawEffectTick(mc.player.getUUID(), GokuEffectPacket.GENKI_DAMA);
        int hits = Math.max(1, ClientGokuEffects.hits(mc.player.getUUID(), GokuEffectPacket.GENKI_DAMA));
        boolean active = raw >= 0 && raw <= GenkiTiming.totalTicks(hits);

        if (active) {
            if (!forcedThirdPerson) {
                previousCameraType = mc.options.getCameraType();
                mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
                forcedThirdPerson = true;
                smoothCamera = null;
                lastShot = null;
            }
        } else {
            restore(mc);
        }
    }

    @SubscribeEvent
    public static void cameraAngles(ViewportEvent.ComputeCameraAngles event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || !forcedThirdPerson) return;

        int raw = ClientGokuEffects.rawEffectTick(mc.player.getUUID(), GokuEffectPacket.GENKI_DAMA);
        int hits = Math.max(1, ClientGokuEffects.hits(mc.player.getUUID(), GokuEffectPacket.GENKI_DAMA));
        if (raw < 0 || raw > GenkiTiming.totalTicks(hits)) return;

        Vec3 origin = ClientGokuEffects.origin(mc.player.getUUID(), GokuEffectPacket.GENKI_DAMA);
        if (origin == null) origin = mc.player.position();

        float age = raw + (float) event.getPartialTick();
        Vec3 goku = origin.add(GenkiTiming.GOKU_X, GenkiTiming.GOKU_Y + 1.05D, 0.0D);
        Vec3 sphereStart = origin.add(GenkiTiming.GOKU_X, GenkiTiming.SPHERE_Y, 0.0D);
        Vec3 contact = origin.add(0.0D, GenkiTiming.CONTACT_Y, 0.0D);
        Vec3 impact = contact.add(GenkiTiming.DRAG_DISTANCE, 0.0D, 0.0D);
        Vec3 playerFocus = mc.player.position().add(0.0D, 1.0D, 0.0D);

        final int shot;
        final Vec3 desired;
        final Vec3 lookAt;
        final double smoothing;
        final float roll;

        if (age < 45.0F) {
            // Goku reveal: close enough to read the smaller blocky model.
            shot = 0;
            float p = GenkiTiming.smoother(age / 45.0F);
            desired = goku.add(-4.8D + p * 0.8D, 2.2D + p * 0.3D, 7.2D - p * 0.6D);
            lookAt = goku.add(0.0D, 0.65D, 0.0D);
            smoothing = 0.14D;
            roll = 0.0F;

        } else if (age < 86.0F) {
            // Cut to the victim so the scene clearly establishes who is being attacked.
            shot = 1;
            float p = GenkiTiming.smoother((age - 45.0F) / 41.0F);
            desired = playerFocus.add(6.8D - p * 1.2D, 3.0D + p * 0.6D, -7.5D);
            lookAt = playerFocus.lerp(goku, 0.18D + p * 0.08D);
            smoothing = 0.18D;
            roll = -0.05F;

        } else if (age < 132.0F) {
            // Return to Goku + growing Genkidama.
            shot = 2;
            float p = GenkiTiming.smoother((age - 86.0F) / 46.0F);
            desired = goku.add(-6.2D, 3.0D + p * 1.2D, -10.0D - p * 1.8D);
            lookAt = goku.lerp(sphereStart, 0.52D + p * 0.12D);
            smoothing = 0.15D;
            roll = 0.06F;

        } else if (age < GenkiTiming.THROW_START_TICK) {
            // Wide composition includes Goku, player and sphere without changing charge behavior.
            shot = 3;
            float p = GenkiTiming.smoother((age - 132.0F) /
                    Math.max(1.0F, GenkiTiming.THROW_START_TICK - 132.0F));
            Vec3 middle = goku.lerp(playerFocus, 0.48D).lerp(sphereStart, 0.20D);
            desired = middle.add(-10.0D - p * 2.0D, 7.0D + p * 1.6D, 16.0D + p * 2.0D);
            lookAt = middle;
            smoothing = 0.14D;
            roll = 0.0F;

        } else if (age < GenkiTiming.CONTACT_TICK) {
            float rawP = GenkiTiming.approachProgress(age);
            float p = GenkiTiming.heavy(rawP);
            Vec3 orb = sphereStart.lerp(contact, p);

            if (rawP < 0.50F) {
                shot = 4;
                desired = orb.add(-9.0D, 5.0D, 12.0D);
                lookAt = orb.lerp(contact, 0.20D);
                smoothing = 0.24D;
                roll = 0.10F;
            } else {
                shot = 5;
                desired = playerFocus.add(6.0D, 3.0D, -7.2D);
                lookAt = orb;
                smoothing = 0.28D;
                roll = -0.10F;
            }

        } else if (age < GenkiTiming.IMPACT_TICK) {
            float rawP = GenkiTiming.dragProgress(age);
            Vec3 orb = curvedDragCenter(contact, impact, rawP);
            Vec3 victim = mc.player.position().add(0.0D, 1.0D, 0.0D);

            if (rawP < 0.30F) {
                shot = 6;
                desired = victim.add(-8.5D, 3.4D, 7.8D);
                lookAt = orb.add(1.8D, -0.2D, 0.0D);
                smoothing = 0.30D;
                roll = Mth.sin(age * 0.22F) * 0.16F;
            } else if (rawP < 0.62F) {
                shot = 7;
                desired = victim.add(7.8D, 2.8D, -7.0D);
                lookAt = victim.add(-1.0D, 0.3D, 0.0D);
                smoothing = 0.32D;
                roll = -Mth.sin(age * 0.20F) * 0.15F;
            } else if (rawP < 0.84F) {
                shot = 8;
                desired = victim.add(-7.0D, 6.4D, -8.0D);
                lookAt = orb;
                smoothing = 0.28D;
                roll = 0.08F;
            } else {
                shot = 9;
                desired = victim.add(-11.0D, 7.0D, 9.0D);
                lookAt = impact;
                smoothing = 0.25D;
                roll = 0.0F;
            }

        } else {
            shot = 10;
            float impactAge = age - GenkiTiming.IMPACT_TICK;
            float p = GenkiTiming.smoother(Mth.clamp(impactAge / 36.0F, 0.0F, 1.0F));
            desired = impact.add(-14.0D - p * 6.0D, 8.0D + p * 5.0D, 15.0D + p * 5.0D);
            lookAt = impact.add(0.0D, 0.5D, 0.0D);
            smoothing = 0.20D;
            roll = impactAge < 10.0F
                    ? Mth.sin(impactAge * 0.70F) * (1.0F - impactAge / 10.0F) * 0.16F
                    : 0.0F;
        }

        Vec3 safeDesired = avoidBlocks(mc, lookAt, desired);
        if (lastShot == null || lastShot != shot) {
            smoothCamera = safeDesired;
            lastShot = shot;
        } else if (smoothCamera == null || smoothCamera.distanceToSqr(safeDesired) > 625.0D) {
            smoothCamera = safeDesired;
        } else {
            smoothCamera = smoothCamera.lerp(safeDesired, smoothing);
        }

        event.getCamera().setPosition(smoothCamera.x, smoothCamera.y, smoothCamera.z);
        Vec3 look = lookAt.subtract(smoothCamera);
        double horizontal = Math.sqrt(look.x * look.x + look.z * look.z);
        float yaw = (float) (Math.atan2(-look.x, look.z) * (180.0D / Math.PI));
        float pitch = (float) (-Math.atan2(look.y, Math.max(0.0001D, horizontal)) * (180.0D / Math.PI));
        event.setYaw(yaw);
        event.setPitch(pitch);
        event.setRoll(roll);
    }

    private static Vec3 curvedDragCenter(Vec3 contact, Vec3 impact, float rawP) {
        float p = GenkiTiming.dragEase(rawP);
        Vec3 base = contact.lerp(impact, p);
        double envelope = Math.sin(Math.PI * p);
        double side = Math.sin(p * Math.PI * 4.0D) * 3.8D * envelope;
        double lift = Math.sin(p * Math.PI * 2.0D) * 2.4D * envelope;
        return base.add(0.0D, lift, side);
    }

    private static Vec3 avoidBlocks(Minecraft mc, Vec3 focus, Vec3 desired) {
        HitResult hit = mc.level.clip(new ClipContext(
                focus, desired, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, mc.player));
        if (hit.getType() == HitResult.Type.MISS) return desired;
        Vec3 hitPos = hit.getLocation();
        Vec3 towardFocus = focus.subtract(hitPos);
        if (towardFocus.lengthSqr() < 0.0001D) return hitPos;
        return hitPos.add(towardFocus.normalize().scale(0.45D));
    }

    private static void restore(Minecraft mc) {
        if (!forcedThirdPerson) return;
        if (previousCameraType != null) mc.options.setCameraType(previousCameraType);
        forcedThirdPerson = false;
        previousCameraType = null;
        smoothCamera = null;
        lastShot = null;
    }
}
