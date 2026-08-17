package igy.purpure.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;

/**
 * V7 GOJO CASTER.
 * Modelo cinematografico procedural estilo Minecraft: pelo blanco, antifaz negro
 * y uniforme oscuro. No usa entidades ni texturas externas, asi que es estable
 * y solo existe visualmente durante el ritual.
 */
public final class ClientGojoCaster {
    public static final float X = 4.0f;
    public static final float Y = 0.0f;
    public static final float Z = 0.0f;
    public static final float FUSION_X = 2.65f;
    public static final float FUSION_Y = 2.02f;

    private ClientGojoCaster() {}

    public static void render(PoseStack pose, float t) {
        if (t < 0.0f || t > 224.0f) return;

        float appear = smooth(0.0f, 18.0f, t);
        float vanish = 1.0f - smooth(202.0f, 224.0f, t);
        float scale = (0.72f + 0.28f * appear) * Math.max(0.02f, vanish);
        float rise = Mth.lerp(appear, -0.85f, 0.0f);

        pose.pushPose();
        pose.translate(X, Y + rise, Z);
        // El frente del modelo es -Z; -90 grados hace que mire hacia el jugador en el origen.
        pose.mulPose(Axis.YP.rotationDegrees(-90.0f));
        pose.scale(scale, scale, scale);

        // Piernas.
        part(pose, -0.17f, 0.43f, 0.0f, 0.26f, 0.86f, 0.28f, 0.035f, 0.040f, 0.055f);
        part(pose,  0.17f, 0.43f, 0.0f, 0.26f, 0.86f, 0.28f, 0.035f, 0.040f, 0.055f);

        // Torso y cuello alto.
        part(pose, 0.0f, 1.24f, 0.0f, 0.74f, 0.78f, 0.40f, 0.028f, 0.032f, 0.050f);
        part(pose, 0.0f, 1.66f, -0.01f, 0.43f, 0.22f, 0.34f, 0.025f, 0.030f, 0.045f);

        // Brazos animados.
        float rightX;
        float rightY;
        float rightZ;
        float leftX;
        float leftY;
        float leftZ;

        if (t < 38.0f) {
            float q = smooth(10.0f, 38.0f, t);
            rightX = Mth.lerp(q, 6.0f, -58.0f);
            rightY = Mth.lerp(q, 0.0f, -24.0f);
            rightZ = Mth.lerp(q, 4.0f, -25.0f);
            leftX = Mth.lerp(q, -2.0f, -58.0f);
            leftY = Mth.lerp(q, 0.0f, 24.0f);
            leftZ = Mth.lerp(q, -4.0f, 25.0f);
        } else if (t < 92.0f) {
            float q = smooth(38.0f, 78.0f, t);
            rightX = Mth.lerp(q, -58.0f, -82.0f);
            rightY = Mth.lerp(q, -24.0f, -38.0f);
            rightZ = Mth.lerp(q, -25.0f, -18.0f);
            leftX = Mth.lerp(q, -58.0f, -82.0f);
            leftY = Mth.lerp(q, 24.0f, 38.0f);
            leftZ = Mth.lerp(q, 25.0f, 18.0f);
        } else if (t < 136.0f) {
            float q = smooth(92.0f, 128.0f, t);
            rightX = Mth.lerp(q, -82.0f, -108.0f);
            rightY = Mth.lerp(q, -38.0f, -8.0f);
            rightZ = Mth.lerp(q, -18.0f, -4.0f);
            leftX = Mth.lerp(q, -82.0f, -108.0f);
            leftY = Mth.lerp(q, 38.0f, 8.0f);
            leftZ = Mth.lerp(q, 18.0f, 4.0f);
        } else {
            float q = smooth(136.0f, 164.0f, t);
            // Remate: brazo derecho proyectado hacia el objetivo y el izquierdo baja.
            rightX = Mth.lerp(q, -108.0f, -91.0f);
            rightY = Mth.lerp(q, -8.0f, 0.0f);
            rightZ = Mth.lerp(q, -4.0f, 0.0f);
            leftX = Mth.lerp(q, -108.0f, -28.0f);
            leftY = Mth.lerp(q, 8.0f, 8.0f);
            leftZ = Mth.lerp(q, 4.0f, 8.0f);
        }

        arm(pose, -0.50f, 1.48f, rightX, rightY, rightZ);
        arm(pose,  0.50f, 1.48f, leftX, leftY, leftZ);

        // Cabeza.
        float headTilt = -3.0f - smooth(110.0f, 150.0f, t) * 6.0f;
        pose.pushPose();
        pose.translate(0.0f, 1.97f, 0.0f);
        pose.mulPose(Axis.XP.rotationDegrees(headTilt));
        box(pose, 0.54f, 0.54f, 0.54f, 0.94f, 0.79f, 0.70f, 1.0f);

        // Antifaz.
        pose.pushPose();
        pose.translate(0.0f, 0.035f, -0.292f);
        box(pose, 0.57f, 0.16f, 0.045f, 0.012f, 0.014f, 0.020f, 1.0f);
        pose.popPose();

        // Pelo blanco en volumen, con mechones simples.
        hairSpike(pose, -0.20f, 0.34f,  0.03f, -22.0f, -18.0f);
        hairSpike(pose, -0.06f, 0.39f, -0.02f,  -8.0f,  -7.0f);
        hairSpike(pose,  0.10f, 0.40f,  0.00f,   7.0f,   8.0f);
        hairSpike(pose,  0.23f, 0.34f,  0.04f,  22.0f,  18.0f);
        hairSpike(pose, -0.24f, 0.25f,  0.14f, -32.0f, -12.0f);
        hairSpike(pose,  0.25f, 0.26f,  0.13f,  31.0f,  12.0f);
        pose.popPose();

        // Pequeño brillo azul en la mano de lanzamiento al final, sin particulas.
        if (t >= 145.0f && t <= 194.0f) {
            float q = smooth(145.0f, 160.0f, t) * (1.0f - smooth(185.0f, 198.0f, t));
            pose.pushPose();
            pose.translate(-0.51f, 1.54f, -0.72f);
            box(pose, 0.12f + q * 0.10f, 0.12f + q * 0.10f, 0.12f + q * 0.10f,
                    0.30f, 0.74f, 1.0f, 0.55f + q * 0.35f);
            pose.popPose();
        }

        pose.popPose();
    }

    private static void arm(PoseStack pose, float shoulderX, float shoulderY,
                            float xDeg, float yDeg, float zDeg) {
        pose.pushPose();
        pose.translate(shoulderX, shoulderY, 0.0f);
        pose.mulPose(Axis.ZP.rotationDegrees(zDeg));
        pose.mulPose(Axis.YP.rotationDegrees(yDeg));
        pose.mulPose(Axis.XP.rotationDegrees(xDeg));
        pose.translate(0.0f, -0.34f, 0.0f);
        box(pose, 0.23f, 0.72f, 0.25f, 0.030f, 0.034f, 0.052f, 1.0f);
        // Mano visible.
        pose.translate(0.0f, -0.43f, 0.0f);
        box(pose, 0.22f, 0.20f, 0.23f, 0.94f, 0.79f, 0.70f, 1.0f);
        pose.popPose();
    }

    private static void hairSpike(PoseStack pose, float x, float y, float z, float zRot, float xRot) {
        pose.pushPose();
        pose.translate(x, y, z);
        pose.mulPose(Axis.ZP.rotationDegrees(zRot));
        pose.mulPose(Axis.XP.rotationDegrees(xRot));
        box(pose, 0.16f, 0.34f, 0.18f, 0.94f, 0.96f, 1.0f, 1.0f);
        pose.popPose();
    }

    private static void part(PoseStack pose, float x, float y, float z,
                             float sx, float sy, float sz,
                             float r, float g, float b) {
        pose.pushPose();
        pose.translate(x, y, z);
        box(pose, sx, sy, sz, r, g, b, 1.0f);
        pose.popPose();
    }

    private static void box(PoseStack pose, float sx, float sy, float sz,
                            float r, float g, float b, float a) {
        RenderSystem.enableDepthTest();
        RenderSystem.disableCull();
        if (a < 0.999f) {
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
        } else {
            RenderSystem.disableBlend();
        }
        RenderSystem.depthMask(true);
        RenderSystem.setShader(GameRenderer::getPositionColorShader);

        float x = sx * 0.5f;
        float y = sy * 0.5f;
        float z = sz * 0.5f;
        Matrix4f m = pose.last().pose();
        BufferBuilder bb = Tesselator.getInstance().getBuilder();
        bb.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);

        // Frente / atras.
        quad(bb, m, -x,-y,-z,  x,-y,-z,  x, y,-z, -x, y,-z, r,g,b,a);
        quad(bb, m,  x,-y, z, -x,-y, z, -x, y, z,  x, y, z, r,g,b,a);
        // Izquierda / derecha.
        quad(bb, m, -x,-y, z, -x,-y,-z, -x, y,-z, -x, y, z, r,g,b,a);
        quad(bb, m,  x,-y,-z,  x,-y, z,  x, y, z,  x, y,-z, r,g,b,a);
        // Abajo / arriba.
        quad(bb, m, -x,-y, z,  x,-y, z,  x,-y,-z, -x,-y,-z, r,g,b,a);
        quad(bb, m, -x, y,-z,  x, y,-z,  x, y, z, -x, y, z, r,g,b,a);

        BufferUploader.drawWithShader(bb.end());
    }

    private static void quad(BufferBuilder bb, Matrix4f m,
                             float x0,float y0,float z0, float x1,float y1,float z1,
                             float x2,float y2,float z2, float x3,float y3,float z3,
                             float r,float g,float b,float a) {
        vertex(bb,m,x0,y0,z0,r,g,b,a);
        vertex(bb,m,x1,y1,z1,r,g,b,a);
        vertex(bb,m,x2,y2,z2,r,g,b,a);
        vertex(bb,m,x3,y3,z3,r,g,b,a);
    }

    private static void vertex(BufferBuilder bb, Matrix4f m,
                               float x,float y,float z,float r,float g,float b,float a) {
        bb.vertex(m, x, y, z)
                .color(toColor(r), toColor(g), toColor(b), toColor(a))
                .endVertex();
    }

    private static int toColor(float v) {
        return Mth.clamp((int)(v * 255.0f), 0, 255);
    }

    private static float smooth(float start, float end, float value) {
        float x = Mth.clamp((value - start) / (end - start), 0.0f, 1.0f);
        return x * x * (3.0f - 2.0f * x);
    }
}
