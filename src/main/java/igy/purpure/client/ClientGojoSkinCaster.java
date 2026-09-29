package igy.purpure.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import igy.purpure.PurpureMod;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
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
import org.joml.Matrix3f;
import org.joml.Matrix4f;

/**
 * PURPURE-3D V15 — renderer de skin portado del GOJO-3D funcional.
 *
 * No usa PlayerModel vanilla. Dibuja manualmente las cajas con UV 64x64,
 * igual que GOJO-3D, porque ese renderer sí conserva la skin empaquetada.
 * La animación de brazos sigue la línea de tiempo aprobada de PURPURE V15.
 */
@Mod.EventBusSubscriber(modid = PurpureMod.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ClientGojoSkinCaster {
    // Ambas texturas viven DENTRO del .jar bajo assets/purpure/.
    // Así todos los jugadores ven la misma skin aunque no tengan ningún pack local.
    private static final ResourceLocation GOJO_SKIN =
            new ResourceLocation(PurpureMod.MODID, "textures/entity/gojo_skin.png");
    private static final ResourceLocation WHITE =
            new ResourceLocation(PurpureMod.MODID, "textures/entity/white.png");

    private static final float MODEL_SCALE = 0.48f;
    private static final float FLOOR_OFFSET = 3.28f;
    private static final float TIMELINE_TICKS = 236.0f;

    // face order: RIGHT, LEFT, TOP, BOTTOM, FRONT, BACK
    private static final int[][] HEAD = {
            {0,8,8,8}, {16,8,8,8}, {8,0,8,8}, {16,0,8,8}, {8,8,8,8}, {24,8,8,8}
    };
    private static final int[][] HAT = {
            {32,8,8,8}, {48,8,8,8}, {40,0,8,8}, {48,0,8,8}, {40,8,8,8}, {56,8,8,8}
    };
    private static final int[][] BODY = {
            {16,20,4,12}, {28,20,4,12}, {20,16,8,4}, {28,16,8,4}, {20,20,8,12}, {32,20,8,12}
    };
    private static final int[][] BODY2 = {
            {16,36,4,12}, {28,36,4,12}, {20,32,8,4}, {28,32,8,4}, {20,36,8,12}, {32,36,8,12}
    };
    private static final int[][] RARM = {
            {40,20,4,12}, {48,20,4,12}, {44,16,4,4}, {48,16,4,4}, {44,20,4,12}, {52,20,4,12}
    };
    private static final int[][] RARM2 = {
            {40,36,4,12}, {48,36,4,12}, {44,32,4,4}, {48,32,4,4}, {44,36,4,12}, {52,36,4,12}
    };
    private static final int[][] LARM = {
            {32,52,4,12}, {40,52,4,12}, {36,48,4,4}, {40,48,4,4}, {36,52,4,12}, {44,52,4,12}
    };
    private static final int[][] LARM2 = {
            {48,52,4,12}, {56,52,4,12}, {52,48,4,4}, {56,48,4,4}, {52,52,4,12}, {60,52,4,12}
    };
    private static final int[][] RLEG = {
            {0,20,4,12}, {8,20,4,12}, {4,16,4,4}, {8,16,4,4}, {4,20,4,12}, {12,20,4,12}
    };
    private static final int[][] LLEG = {
            {16,52,4,12}, {24,52,4,12}, {20,48,4,4}, {24,48,4,4}, {20,52,4,12}, {28,52,4,12}
    };

    private ClientGojoSkinCaster() {}

    @SubscribeEvent
    public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;

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
        float alpha = smoothRange(0.0f, 12.0f, t);
        if (alpha <= 0.01f) return;

        double ox = ClientPurpureEffects.effectX(target.getUUID());
        double oy = ClientPurpureEffects.effectY(target.getUUID());
        double oz = ClientPurpureEffects.effectZ(target.getUUID());
        if (Double.isNaN(ox) || Double.isNaN(oy) || Double.isNaN(oz)) return;

        double gx = ox + 4.0;
        double gy = oy + 0.03;
        double gz = oz;

        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        RenderType skinType = RenderType.entityTranslucent(GOJO_SKIN);
        RenderType whiteType = RenderType.entityTranslucent(WHITE);
        VertexConsumer skin = buffers.getBuffer(skinType);
        VertexConsumer white = buffers.getBuffer(whiteType);

        pose.pushPose();
        pose.translate(
                gx - camera.getPosition().x,
                gy - camera.getPosition().y,
                gz - camera.getPosition().z
        );

        // Misma orientación del renderer funcional de GOJO-3D.
        pose.mulPose(Axis.YP.rotationDegrees(-90.0f));
        pose.scale(MODEL_SCALE, MODEL_SCALE, MODEL_SCALE);
        pose.translate(0.0f, FLOOR_OFFSET + (float)Math.sin(t * 0.07f) * 0.02f, 0.0f);

        drawBody(pose, skin, white, alpha, t);

        pose.popPose();
        buffers.endBatch(skinType);
        buffers.endBatch(whiteType);
    }

    private static void drawBody(PoseStack pose, VertexConsumer skin, VertexConsumer white,
                                 float alpha, float t) {
        drawSkinBoxAt(pose, skin, 1.95f, 2.55f, 0.92f, 0.0f, 0.05f, 0.0f, BODY, alpha);
        drawSkinBoxAt(pose, skin, 2.015f, 2.615f, 0.985f, 0.0f, 0.05f, 0.0f, BODY2, alpha);

        drawSkinBoxAt(pose, skin, 0.80f, 2.40f, 0.84f, -0.42f, -2.08f, 0.0f, RLEG, alpha);
        drawSkinBoxAt(pose, skin, 0.80f, 2.40f, 0.84f,  0.42f, -2.08f, 0.0f, LLEG, alpha);

        pose.pushPose();
        pose.translate(0.0f, 2.13f, 0.0f);
        pose.mulPose(Axis.YP.rotationDegrees((float)Math.toDegrees(Math.sin(t * 0.04f) * 0.03f)));
        drawSkinBox(pose, skin, 1.64f, 1.64f, 1.64f, HEAD, alpha);
        drawSkinBox(pose, skin, 1.71f, 1.71f, 1.71f, HAT, alpha);
        pose.popPose();

        drawColorBoxAt(pose, white, 1.48f, 0.54f, 0.97f, 0.0f, 1.34f, 0.0f,
                0x10, 0x0C, 0x16, alpha);

        float p = Mth.clamp(t / TIMELINE_TICKS, 0.0f, 1.0f);
        float leftRaise = smoothRange(0.08f, 0.19f, p)
                * (1.0f - smoothRange(0.42f, 0.52f, p));
        float rightRaise = smoothRange(0.28f, 0.39f, p)
                * (1.0f - smoothRange(0.46f, 0.56f, p));
        float bothGather = smoothRange(0.45f, 0.68f, p);
        float release = smoothRange(0.77f, 0.87f, p);

        float lX = Mth.lerp(leftRaise, 0.0f, -Mth.PI / 2.05f);
        float lY = Mth.lerp(leftRaise, 0.0f, -1.18f);
        float lZ = Mth.lerp(leftRaise, 0.0f, 0.08f);

        float rX = Mth.lerp(rightRaise, 0.0f, -Mth.PI / 2.05f);
        float rY = Mth.lerp(rightRaise, 0.0f, 1.18f);
        float rZ = Mth.lerp(rightRaise, 0.0f, -0.08f);

        if (bothGather > 0.0f) {
            lX = Mth.lerp(bothGather, -Mth.PI / 2.05f, -1.08f);
            lY = Mth.lerp(bothGather, -1.18f, -0.39f);
            lZ = Mth.lerp(bothGather, 0.08f, -0.17f);
            rX = Mth.lerp(bothGather, -Mth.PI / 2.05f, -1.08f);
            rY = Mth.lerp(bothGather, 1.18f, 0.39f);
            rZ = Mth.lerp(bothGather, -0.08f, 0.17f);
        }

        if (release > 0.0f) {
            lX = Mth.lerp(release, -1.08f, -0.68f);
            lY = Mth.lerp(release, -0.39f, -0.28f);
            lZ = Mth.lerp(release, -0.17f, 0.14f);
            rX = Mth.lerp(release, -1.08f, -1.50f);
            rY = Mth.lerp(release, 0.39f, 0.14f);
            rZ = Mth.lerp(release, 0.17f, -0.08f);
        }

        pose.pushPose();
        pose.translate(-1.31f, 1.20f, 0.0f);
        pose.mulPose(Axis.ZP.rotation(rZ));
        pose.mulPose(Axis.YP.rotation(rY));
        pose.mulPose(Axis.XP.rotation(rX));
        drawSkinBoxAt(pose, skin, 0.66f, 2.32f, 0.80f, 0.0f, -1.16f, 0.0f, RARM, alpha);
        drawSkinBoxAt(pose, skin, 0.725f, 2.385f, 0.865f, 0.0f, -1.16f, 0.0f, RARM2, alpha);
        pose.popPose();

        pose.pushPose();
        pose.translate(1.31f, 1.20f, 0.0f);
        pose.mulPose(Axis.ZP.rotation(lZ));
        pose.mulPose(Axis.YP.rotation(lY));
        pose.mulPose(Axis.XP.rotation(lX));
        drawSkinBoxAt(pose, skin, 0.66f, 2.32f, 0.80f, 0.0f, -1.16f, 0.0f, LARM, alpha);
        drawSkinBoxAt(pose, skin, 0.725f, 2.385f, 0.865f, 0.0f, -1.16f, 0.0f, LARM2, alpha);
        pose.popPose();
    }

    private static void drawSkinBoxAt(PoseStack pose, VertexConsumer consumer,
                                      float sx, float sy, float sz,
                                      float x, float y, float z,
                                      int[][] uv, float alpha) {
        pose.pushPose();
        pose.translate(x, y, z);
        drawSkinBox(pose, consumer, sx, sy, sz, uv, alpha);
        pose.popPose();
    }

    private static void drawSkinBox(PoseStack pose, VertexConsumer consumer,
                                    float sx, float sy, float sz,
                                    int[][] uv, float alpha) {
        float x = sx * 0.5f, y = sy * 0.5f, z = sz * 0.5f;
        Matrix4f m = pose.last().pose();
        Matrix3f n = pose.last().normal();

        face(consumer,m,n, x,-y, z, x,-y,-z, x, y,-z, x, y, z, uv[0], 1,0,0, 255,255,255,alpha);
        face(consumer,m,n,-x,-y,-z,-x,-y, z,-x, y, z,-x, y,-z, uv[1],-1,0,0, 255,255,255,alpha);
        face(consumer,m,n,-x, y, z, x, y, z, x, y,-z,-x, y,-z, uv[2], 0,1,0, 255,255,255,alpha);
        face(consumer,m,n,-x,-y,-z, x,-y,-z, x,-y, z,-x,-y, z, uv[3], 0,-1,0, 255,255,255,alpha);
        face(consumer,m,n,-x,-y, z, x,-y, z, x, y, z,-x, y, z, uv[4], 0,0,1, 255,255,255,alpha);
        face(consumer,m,n, x,-y,-z,-x,-y,-z,-x, y,-z, x, y,-z, uv[5], 0,0,-1, 255,255,255,alpha);
    }

    private static void drawColorBoxAt(PoseStack pose, VertexConsumer consumer,
                                       float sx, float sy, float sz,
                                       float px, float py, float pz,
                                       int cr, int cg, int cb, float alpha) {
        pose.pushPose();
        pose.translate(px, py, pz);
        float x = sx * 0.5f, y = sy * 0.5f, z = sz * 0.5f;
        Matrix4f m = pose.last().pose();
        Matrix3f n = pose.last().normal();
        int[] full = {0,0,1,1};
        face(consumer,m,n, x,-y, z, x,-y,-z, x, y,-z, x, y, z, full, 1,0,0, cr,cg,cb,alpha);
        face(consumer,m,n,-x,-y,-z,-x,-y, z,-x, y, z,-x, y,-z, full,-1,0,0, cr,cg,cb,alpha);
        face(consumer,m,n,-x, y, z, x, y, z, x, y,-z,-x, y,-z, full, 0,1,0, cr,cg,cb,alpha);
        face(consumer,m,n,-x,-y,-z, x,-y,-z, x,-y, z,-x,-y, z, full, 0,-1,0, cr,cg,cb,alpha);
        face(consumer,m,n,-x,-y, z, x,-y, z, x, y, z,-x, y, z, full, 0,0,1, cr,cg,cb,alpha);
        face(consumer,m,n, x,-y,-z,-x,-y,-z,-x, y,-z, x, y,-z, full, 0,0,-1, cr,cg,cb,alpha);
        pose.popPose();
    }

    private static void face(VertexConsumer c, Matrix4f m, Matrix3f n,
                             float x0,float y0,float z0, float x1,float y1,float z1,
                             float x2,float y2,float z2, float x3,float y3,float z3,
                             int[] uv, float nx,float ny,float nz,
                             int cr,int cg,int cb,float alpha) {
        float u0 = uv[0] / 64.0f;
        float v0 = uv[1] / 64.0f;
        float u1 = (uv[0] + uv[2]) / 64.0f;
        float v1 = (uv[1] + uv[3]) / 64.0f;
        int a = clamp255(alpha * 255.0f);

        vertex(c,m,n,x0,y0,z0,u0,v1,nx,ny,nz,cr,cg,cb,a);
        vertex(c,m,n,x1,y1,z1,u1,v1,nx,ny,nz,cr,cg,cb,a);
        vertex(c,m,n,x2,y2,z2,u1,v0,nx,ny,nz,cr,cg,cb,a);
        vertex(c,m,n,x3,y3,z3,u0,v0,nx,ny,nz,cr,cg,cb,a);
    }

    private static void vertex(VertexConsumer c, Matrix4f m, Matrix3f n,
                               float x,float y,float z, float u,float v,
                               float nx,float ny,float nz,
                               int r,int g,int b,int a) {
        c.vertex(m,x,y,z)
                .color(r,g,b,a)
                .uv(u,v)
                .overlayCoords(OverlayTexture.NO_OVERLAY)
                .uv2(LightTexture.FULL_BRIGHT)
                .normal(n,nx,ny,nz)
                .endVertex();
    }

    private static int clamp255(float v) {
        return Math.max(0, Math.min(255, (int)v));
    }

    private static float smoothRange(float start, float end, float value) {
        float x = Math.max(0.0f, Math.min(1.0f, (value - start) / (end - start)));
        return x * x * (3.0f - 2.0f * x);
    }

    private static float smooth01(float x) {
        x = Math.max(0.0f, Math.min(1.0f, x));
        return x * x * (3.0f - 2.0f * x);
    }
}
