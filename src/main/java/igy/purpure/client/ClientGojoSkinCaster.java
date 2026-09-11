package igy.purpure.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import igy.purpure.PurpureMod;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * V15: conserva el Gojo visual de PURPURE-3D, pero lo hace protagonista:
 * 1.68x, ojos Six Eyes reforzados en la skin, full-bright y anclado al punto
 * original del ritual aunque el objetivo empiece a viajar con Hollow Purple.
 */
@Mod.EventBusSubscriber(modid = PurpureMod.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ClientGojoSkinCaster {
    private static final ResourceLocation GOJO_SKIN =
            new ResourceLocation(PurpureMod.MODID, "textures/entity/gojo_skin.png");
    private static final float GOJO_SCALE = 1.68f;

    private static PlayerModel<AbstractClientPlayer> model;

    private ClientGojoSkinCaster() {}

    @SubscribeEvent
    public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;

        if (model == null) {
            model = new PlayerModel<>(mc.getEntityModels().bakeLayer(ModelLayers.PLAYER), false);
        }

        Camera camera = event.getCamera();
        PoseStack pose = event.getPoseStack();
        float partial = event.getPartialTick();

        for (AbstractClientPlayer target : mc.level.players()) {
            float base = ClientPurpureEffects.effectTick(target.getUUID());
            if (base < 0.0f) continue;
            renderGojo(mc, pose, camera, target, base + partial);
        }
    }

    private static void renderGojo(Minecraft mc, PoseStack pose, Camera camera,
                                   AbstractClientPlayer target, float t) {
        float alpha = smooth(0.0f, 12.0f, t) * (1.0f - smooth(216.0f, 238.0f, t));
        if (alpha <= 0.01f) return;

        double ox = ClientPurpureEffects.effectX(target.getUUID());
        double oy = ClientPurpureEffects.effectY(target.getUUID());
        double oz = ClientPurpureEffects.effectZ(target.getUUID());
        if (Double.isNaN(ox) || Double.isNaN(oy) || Double.isNaN(oz)) return;

        resetModel();
        animateModel(t);

        double gx = ox + 4.0;
        double gy = oy + 0.03;
        double gz = oz;

        pose.pushPose();
        pose.translate(
                gx - camera.getPosition().x,
                gy - camera.getPosition().y,
                gz - camera.getPosition().z
        );

        // Mira desde +X hacia el punto donde comenzó el ritual.
        pose.mulPose(Axis.YP.rotationDegrees(90.0f));
        pose.scale(-GOJO_SCALE, -GOJO_SCALE, GOJO_SCALE);
        pose.translate(0.0f, -1.501f, 0.0f);

        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        RenderType type = RenderType.entityTranslucent(GOJO_SKIN);
        VertexConsumer consumer = buffers.getBuffer(type);
        model.renderToBuffer(
                pose,
                consumer,
                LightTexture.FULL_BRIGHT,
                OverlayTexture.NO_OVERLAY,
                1.0f, 1.0f, 1.0f, alpha
        );
        buffers.endBatch(type);
        pose.popPose();
    }

    private static void resetModel() {
        model.head.xRot = 0.0f;
        model.head.yRot = 0.0f;
        model.head.zRot = 0.0f;
        model.body.xRot = 0.0f;
        model.body.yRot = 0.0f;
        model.body.zRot = 0.0f;
        model.rightArm.xRot = 0.0f;
        model.rightArm.yRot = 0.0f;
        model.rightArm.zRot = 0.0f;
        model.leftArm.xRot = 0.0f;
        model.leftArm.yRot = 0.0f;
        model.leftArm.zRot = 0.0f;
        model.rightLeg.xRot = 0.0f;
        model.rightLeg.yRot = 0.0f;
        model.rightLeg.zRot = 0.0f;
        model.leftLeg.xRot = 0.0f;
        model.leftLeg.yRot = 0.0f;
        model.leftLeg.zRot = 0.0f;

        model.hat.visible = true;
        model.jacket.visible = true;
        model.leftSleeve.visible = true;
        model.rightSleeve.visible = true;
        model.leftPants.visible = true;
        model.rightPants.visible = true;
    }

    private static void animateModel(float t) {
        float breathe = Mth.sin(t * 0.105f) * 0.035f;
        float sway = Mth.sin(t * 0.050f) * 0.035f;

        model.body.yRot = sway;
        model.head.yRot = -sway * 0.72f;
        model.head.xRot = -0.055f + Mth.sin(t * 0.047f) * 0.022f;
        model.rightLeg.xRot = 0.035f + Mth.sin(t * 0.043f) * 0.018f;
        model.leftLeg.xRot = -0.030f - Mth.sin(t * 0.043f) * 0.016f;

        float rx, ry, rz;
        float lx, ly, lz;

        if (t < 82.0f) {
            float q = smooth(8.0f, 76.0f, t);
            rx = lerp(q, 0.08f, -0.86f);
            ry = lerp(q, 0.0f, -0.52f);
            rz = lerp(q, 0.05f, -0.44f);
            lx = lerp(q, -0.03f, -0.84f);
            ly = lerp(q, 0.0f, 0.52f);
            lz = lerp(q, -0.05f, 0.44f);
        } else if (t < 150.0f) {
            float q = smooth(82.0f, 140.0f, t);
            rx = lerp(q, -0.86f, -1.26f);
            ry = lerp(q, -0.52f, -0.72f);
            rz = lerp(q, -0.44f, -0.26f);
            lx = lerp(q, -0.84f, -1.26f);
            ly = lerp(q, 0.52f, 0.72f);
            lz = lerp(q, 0.44f, 0.26f);
        } else if (t < 194.0f) {
            float q = smooth(150.0f, 188.0f, t);
            rx = lerp(q, -1.26f, -1.68f);
            ry = lerp(q, -0.72f, -0.10f);
            rz = lerp(q, -0.26f, -0.05f);
            lx = lerp(q, -1.26f, -1.68f);
            ly = lerp(q, 0.72f, 0.10f);
            lz = lerp(q, 0.26f, 0.05f);
        } else {
            float q = smooth(194.0f, 220.0f, t);
            rx = lerp(q, -1.68f, -1.50f);
            ry = lerp(q, -0.10f, 0.0f);
            rz = lerp(q, -0.05f, 0.0f);
            lx = lerp(q, -1.68f, -0.40f);
            ly = lerp(q, 0.10f, 0.18f);
            lz = lerp(q, 0.05f, 0.18f);
            model.body.xRot = -0.04f * q;
            model.head.xRot -= 0.03f * q;
        }

        model.rightArm.xRot = rx + breathe;
        model.rightArm.yRot = ry;
        model.rightArm.zRot = rz;
        model.leftArm.xRot = lx - breathe;
        model.leftArm.yRot = ly;
        model.leftArm.zRot = lz;

        model.hat.copyFrom(model.head);
        model.rightSleeve.copyFrom(model.rightArm);
        model.leftSleeve.copyFrom(model.leftArm);
        model.rightPants.copyFrom(model.rightLeg);
        model.leftPants.copyFrom(model.leftLeg);
        model.jacket.copyFrom(model.body);
    }

    private static float smooth(float start, float end, float value) {
        float x = Mth.clamp((value - start) / (end - start), 0.0f, 1.0f);
        return x * x * (3.0f - 2.0f * x);
    }

    private static float lerp(float q, float a, float b) {
        return a + (b - a) * q;
    }
}
