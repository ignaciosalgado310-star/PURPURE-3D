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
 * Gojo como jugador Minecraft real visual: usa PlayerModel + una skin 64x64
 * incluida dentro del mod. Solo existe visualmente durante /purpure.
 */
@Mod.EventBusSubscriber(modid = PurpureMod.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ClientGojoSkinCaster {
    private static final ResourceLocation GOJO_SKIN =
            new ResourceLocation(PurpureMod.MODID, "textures/entity/gojo_skin.png");

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

            // Mismo ritmo visual que el ataque actual.
            float t = (base + partial) * 0.68f;
            if (t > 236.0f) continue;

            renderGojo(mc, pose, camera, target, t);
        }
    }

    private static void renderGojo(Minecraft mc, PoseStack pose, Camera camera,
                                   AbstractClientPlayer target, float t) {
        float appear = smooth(0.0f, 16.0f, t);
        float vanish = 1.0f - smooth(218.0f, 236.0f, t);
        float alpha = Mth.clamp(appear * vanish, 0.0f, 1.0f);
        if (alpha <= 0.01f) return;

        resetModel();
        animateModel(t);

        double gx = target.getX() + 3.45;
        double gy = target.getY();
        double gz = target.getZ();

        pose.pushPose();
        pose.translate(
                gx - camera.getPosition().x,
                gy - camera.getPosition().y,
                gz - camera.getPosition().z
        );

        // Equivalente a un jugador con yaw 90: mira hacia el objetivo en el origen.
        pose.mulPose(Axis.YP.rotationDegrees(90.0f));
        pose.scale(-1.0f, -1.0f, 1.0f);
        pose.translate(0.0f, -1.501f, 0.0f);

        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        RenderType type = RenderType.entityCutoutNoCull(GOJO_SKIN);
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
        // Respiracion y postura: nunca queda completamente congelado.
        float breathe = Mth.sin(t * 0.10f) * 0.035f;
        float bodySway = Mth.sin(t * 0.052f) * 0.035f;
        model.body.yRot = bodySway;
        model.head.yRot = -bodySway * 0.70f;
        model.head.xRot = -0.04f + Mth.sin(t * 0.045f) * 0.025f;
        model.rightLeg.xRot = 0.04f + Mth.sin(t * 0.045f) * 0.025f;
        model.leftLeg.xRot = -0.03f - Mth.sin(t * 0.045f) * 0.020f;

        float rx, ry, rz;
        float lx, ly, lz;

        if (t < 48.0f) {
            // Entrada: levanta las manos y prepara Azul/Rojo.
            float q = smooth(8.0f, 48.0f, t);
            rx = lerp(q, 0.08f, -1.02f);
            ry = lerp(q, 0.0f, -0.48f);
            rz = lerp(q, 0.05f, -0.42f);
            lx = lerp(q, -0.03f, -0.92f);
            ly = lerp(q, 0.0f, 0.48f);
            lz = lerp(q, -0.05f, 0.42f);
        } else if (t < 108.0f) {
            // Control: brazos bien abiertos como si sostuviera ambos orbes.
            float q = smooth(48.0f, 96.0f, t);
            rx = lerp(q, -1.02f, -1.34f);
            ry = lerp(q, -0.48f, -0.72f);
            rz = lerp(q, -0.42f, -0.28f);
            lx = lerp(q, -0.92f, -1.34f);
            ly = lerp(q, 0.48f, 0.72f);
            lz = lerp(q, 0.42f, 0.28f);
        } else if (t < 162.0f) {
            // Fusion: las manos van hacia el centro poco a poco.
            float q = smooth(108.0f, 154.0f, t);
            rx = lerp(q, -1.34f, -1.72f);
            ry = lerp(q, -0.72f, -0.12f);
            rz = lerp(q, -0.28f, -0.06f);
            lx = lerp(q, -1.34f, -1.72f);
            ly = lerp(q, 0.72f, 0.12f);
            lz = lerp(q, 0.28f, 0.06f);
        } else {
            // Lanzamiento: derecha hacia el objetivo, izquierda baja.
            float q = smooth(162.0f, 198.0f, t);
            rx = lerp(q, -1.72f, -1.48f);
            ry = lerp(q, -0.12f, 0.0f);
            rz = lerp(q, -0.06f, 0.0f);
            lx = lerp(q, -1.72f, -0.42f);
            ly = lerp(q, 0.12f, 0.20f);
            lz = lerp(q, 0.06f, 0.18f);
        }

        model.rightArm.xRot = rx + breathe;
        model.rightArm.yRot = ry;
        model.rightArm.zRot = rz;
        model.leftArm.xRot = lx - breathe;
        model.leftArm.yRot = ly;
        model.leftArm.zRot = lz;

        // Copiamos las partes exteriores de la skin para que mangas/pantalones sigan el cuerpo.
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
