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
 * Gojo del visor final aprobado V15.
 *
 * Conserva la skin empaquetada del visor y usa exactamente la misma línea de
 * tiempo normalizada (11.8 s / 236 ticks) para levantar primero el brazo de
 * Blue, después el de Red, juntar ambos durante la fusión y acompañar Purple.
 *
 * Gojo permanece visible hasta que el servidor envía STOP, es decir, no se
 * limpia mientras queden tótems/daño/final por ejecutar.
 */
@Mod.EventBusSubscriber(modid = PurpureMod.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ClientGojoSkinCaster {
    private static final ResourceLocation GOJO_SKIN =
            new ResourceLocation(PurpureMod.MODID, "textures/entity/gojo_skin.png");

    // Se conserva el tamaño visual ya establecido por el proyecto.
    private static final float GOJO_SCALE = 1.68f;
    private static final float TIMELINE_TICKS = 236.0f;

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

    private static void renderGojo(Minecraft mc,
                                   PoseStack pose,
                                   Camera camera,
                                   AbstractClientPlayer target,
                                   float t) {
        // Fade-in del visor. Sin fade-out: STOP del servidor decide la limpieza.
        float alpha = smooth(0.0f, 12.0f, t);
        if (alpha <= 0.01f) return;

        double ox = ClientPurpureEffects.effectX(target.getUUID());
        double oy = ClientPurpureEffects.effectY(target.getUUID());
        double oz = ClientPurpureEffects.effectZ(target.getUUID());
        if (Double.isNaN(ox) || Double.isNaN(oy) || Double.isNaN(oz)) return;

        resetModel();
        animateApprovedV15(t);

        double gx = ox + 4.0;
        double gy = oy + 0.03;
        double gz = oz;

        pose.pushPose();
        pose.translate(
                gx - camera.getPosition().x,
                gy - camera.getPosition().y,
                gz - camera.getPosition().z
        );

        // Gojo mira desde +X hacia el objetivo/origen del ritual.
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

    private static void animateApprovedV15(float t) {
        float p = Mth.clamp(t / TIMELINE_TICKS, 0.0f, 1.0f);

        float leftRaise = smooth01(0.08f, 0.19f, p)
                * (1.0f - smooth01(0.42f, 0.52f, p));
        float rightRaise = smooth01(0.28f, 0.39f, p)
                * (1.0f - smooth01(0.46f, 0.56f, p));
        float bothGather = smooth01(0.45f, 0.68f, p);
        float release = smooth01(0.77f, 0.87f, p);

        // Blue: brazo izquierdo se levanta desde el hombro y la palma apunta a la energía.
        model.leftArm.xRot = Mth.lerp(leftRaise, 0.0f, -Mth.PI / 2.05f);
        model.leftArm.yRot = Mth.lerp(leftRaise, 0.0f, -1.18f);
        model.leftArm.zRot = Mth.lerp(leftRaise, 0.0f, 0.08f);

        // Red: mismo gesto en el brazo derecho unos segundos después.
        model.rightArm.xRot = Mth.lerp(rightRaise, 0.0f, -Mth.PI / 2.05f);
        model.rightArm.yRot = Mth.lerp(rightRaise, 0.0f, 1.18f);
        model.rightArm.zRot = Mth.lerp(rightRaise, 0.0f, -0.08f);

        // Durante la fusión ambos brazos se cierran hacia la masa Blue + Red.
        if (bothGather > 0.0f) {
            model.leftArm.xRot = Mth.lerp(bothGather, -Mth.PI / 2.05f, -1.08f);
            model.leftArm.yRot = Mth.lerp(bothGather, -1.18f, -0.39f);
            model.leftArm.zRot = Mth.lerp(bothGather, 0.08f, -0.17f);

            model.rightArm.xRot = Mth.lerp(bothGather, -Mth.PI / 2.05f, -1.08f);
            model.rightArm.yRot = Mth.lerp(bothGather, 1.18f, 0.39f);
            model.rightArm.zRot = Mth.lerp(bothGather, -0.08f, 0.17f);

            model.head.yRot = -0.07f * bothGather;
        }

        // Gesto final aprobado antes/durante la salida de Purple.
        if (release > 0.0f) {
            model.leftArm.xRot = Mth.lerp(release, -1.08f, -0.68f);
            model.leftArm.yRot = Mth.lerp(release, -0.39f, -0.28f);
            model.leftArm.zRot = Mth.lerp(release, -0.17f, 0.14f);

            model.rightArm.xRot = Mth.lerp(release, -1.08f, -1.50f);
            model.rightArm.yRot = Mth.lerp(release, 0.39f, 0.14f);
            model.rightArm.zRot = Mth.lerp(release, 0.17f, -0.08f);

            model.head.yRot = -0.10f * release;
        }

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

    private static float smooth01(float start, float end, float value) {
        float x = Mth.clamp((value - start) / (end - start), 0.0f, 1.0f);
        return x * x * (3.0f - 2.0f * x);
    }
}
