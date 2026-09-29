package com.igygaming.gokufx.client;

import com.igygaming.gokufx.GokuFxMod;
import com.igygaming.gokufx.network.GokuEffectPacket;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;

/**
 * Render 3D real para GOKU FX.
 * Inspirado en la malla esférica de PURPURE 3D: las esferas principales usan 88x48 segmentos.
 * No usa partículas ni ItemDisplay para formar el Kamehameha o la Genki Dama.
 */
@Mod.EventBusSubscriber(modid = GokuFxMod.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ClientGokuRenderer {
    private static final float TAU = (float) (Math.PI * 2.0D);

    private ClientGokuRenderer() {}

    @SubscribeEvent
    public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;

        Camera camera = event.getCamera();
        PoseStack pose = event.getPoseStack();
        float partial = event.getPartialTick();

        for (AbstractClientPlayer target : mc.level.players()) {
            float kame = ClientGokuEffects.effectTick(target.getUUID(), GokuEffectPacket.KAMEHAMEHA);
            float genki = ClientGokuEffects.effectTick(target.getUUID(), GokuEffectPacket.GENKI_DAMA);
            if (kame < 0.0F && genki < 0.0F) continue;

            pose.pushPose();
            pose.translate(
                    target.getX() - camera.getPosition().x,
                    target.getY() - camera.getPosition().y,
                    target.getZ() - camera.getPosition().z
            );

            // Kamehameha V12 se renderiza exclusivamente en ClientKameV12Renderer.
            if (genki >= 0.0F) drawGenkiDama(pose, genki + partial);

            pose.popPose();
        }

        RenderSystem.depthMask(true);
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();
        RenderSystem.enableCull();
        RenderSystem.enableDepthTest();
    }

    private static void drawKamehameha(PoseStack pose, float t) {
        final float sourceX = -14.0F;
        // La pendiente del haz es -0.04. Con Y=1.71 pasa por Y~1.15 justo en el centro del jugador.
        final float sourceY = 1.71F;

        if (t <= 60.0F) {
            float p = smooth(1.0F, 60.0F, t);
            float pulse = 1.0F + Mth.sin(t * 0.32F) * 0.045F;
            float radius = (0.45F + 2.45F * p) * pulse;

            pose.pushPose();
            pose.translate(sourceX, sourceY, 0.0F);
            blueEnergySphere(pose, radius);
            // Rallitos azules que salen de la esfera, estilo anime. No modifican la esfera original.
            drawKameChargeLightning(pose, t, radius);
            pose.popPose();
            return;
        }

        if (t <= 158.0F) {
            float launch = smooth(61.0F, 77.0F, t);
            float fade = 1.0F - smooth(148.0F, 158.0F, t);
            float beamLength = 5.0F + 50.0F * launch;
            float pulse = 1.0F + Mth.sin(t * 0.48F) * 0.075F;

            Vec3 start = new Vec3(sourceX, sourceY, 0.0D);
            Vec3 end = new Vec3(sourceX + beamLength, sourceY - beamLength * 0.04F, 0.0D);

            pose.pushPose();
            pose.translate(sourceX, sourceY, 0.0F);
            float sourceRadius = 2.90F * pulse * fade;
            blueEnergySphere(pose, sourceRadius);
            drawKameChargeLightning(pose, t, sourceRadius);
            pose.popPose();

            // Haz 3D circular, totalmente azul: sin nucleo blanco.
            solidCylinder(pose, start, end, 2.72F * pulse, 0.015F, 0.10F, 0.82F, fade);
            solidCylinder(pose, start, end, 2.05F * pulse, 0.020F, 0.32F, 1.00F, fade);
            solidCylinder(pose, start, end, 1.22F * pulse, 0.10F, 0.58F, 1.00F, fade);
            glowCylinder(pose, start, end, 2.96F * pulse, 0.02F, 0.20F, 1.00F, 0.20F * fade);
            glowCylinder(pose, start, end, 3.25F * pulse, 0.00F, 0.08F, 1.00F, 0.10F * fade);

            // Los mismos rallitos anime ahora recorren también TODO el Kamehameha mientras empuja al jugador.
            drawKameBeamLightning(pose, start, end, t, 3.05F * pulse, fade);

            // Zona donde atraviesa al jugador: bola de energia 3D centrada.
            pose.pushPose();
            pose.translate(0.0F, 1.15F, 0.0F);
            float impactPulse = 2.0F + Mth.sin(t * 0.55F) * 0.28F;
            blueEnergySphere(pose, impactPulse * fade);
            // También salen rallitos alrededor del jugador mientras lo lleva el ataque.
            drawKameChargeLightning(pose, t + 21.0F, impactPulse * fade);
            pose.popPose();
        }
    }

    private static void drawGenkiDama(PoseStack pose, float t) {
        if (t <= 120.0F) {
            float p = Mth.clamp(t / 120.0F, 0.0F, 1.0F);
            float eased = 1.0F - (1.0F - p) * (1.0F - p);
            float radius = 0.55F + 6.80F * eased;
            radius *= 1.0F + Mth.sin(t * 0.18F) * 0.025F;

            pose.pushPose();
            pose.translate(0.0F, 15.0F, 0.0F);
            celesteEnergySphere(pose, radius);
            // Brillitos y corrientes de energía que llegan a la esfera durante la carga.
            drawGenkiChargeEnergy(pose, t, radius);
            pose.popPose();

            // Pequenas motas 3D que suben hacia la Genki; son mallas, no particulas.
            for (int i = 0; i < 12; i++) {
                double phase = t * 0.055D + i * (Math.PI * 2.0D / 12.0D);
                double cycle = ((t + i * 7.0D) % 84.0D) / 84.0D;
                double ring = 4.5D + Math.sin(phase * 1.8D) * 1.3D;
                float x = (float) (Math.cos(phase) * ring);
                float z = (float) (Math.sin(phase) * ring);
                float y = (float) (0.8D + cycle * 14.0D);
                float size = 0.10F + (float) cycle * 0.16F;

                pose.pushPose();
                pose.translate(x, y, z);
                smallCelesteOrb(pose, size);
                pose.popPose();
            }
            return;
        }

        if (t <= 160.0F) {
            float p = Mth.clamp((t - 121.0F) / 39.0F, 0.0F, 1.0F);
            float fall = p * p;
            float y = Mth.lerp(fall, 15.0F, 1.2F);
            float radius = 7.35F * (1.0F + Mth.sin(t * 0.34F) * 0.025F);

            pose.pushPose();
            pose.translate(0.0F, y, 0.0F);
            celesteEnergySphere(pose, radius);
            pose.popPose();
            return;
        }

        if (t <= 224.0F) {
            float age = t - 161.0F;
            float fade = 1.0F - smooth(210.0F, 224.0F, t);
            float core = Math.max(0.30F, 7.35F - age * 0.105F);
            float shock = 4.0F + age * 0.48F;

            pose.pushPose();
            pose.translate(0.0F, 1.0F, 0.0F);

            if (core > 0.35F) {
                celesteEnergySphere(pose, core * fade);
            }

            // Ondas esfericas de impacto, todas celestes y brillantes.
            glowSphere(pose, shock, 0.00F, 0.62F, 1.00F, 0.13F * fade, 64, 32);
            glowSphere(pose, shock * 1.10F, 0.04F, 0.84F, 1.00F, 0.085F * fade, 64, 32);
            glowSphere(pose, shock * 1.20F, 0.12F, 0.94F, 1.00F, 0.050F * fade, 64, 32);
            pose.popPose();
        }
    }

    /**
     * Rayos cortos y quebrados que nacen en la superficie de la esfera azul.
     * Son geometría 3D aditiva, no partículas, y no alteran el modelo/base existente.
     */
    private static void drawKameChargeLightning(PoseStack pose, float t, float radius) {
        if (radius <= 0.08F) return;
        float strength = smooth(5.0F, 24.0F, t);
        if (strength <= 0.01F) return;

        final int rays = 8;
        for (int i = 0; i < rays; i++) {
            double angle = i * (Math.PI * 2.0D / rays) + t * 0.075D;
            double vertical = Math.sin(t * 0.17D + i * 1.71D) * 0.62D;
            double horizontal = Math.sqrt(Math.max(0.08D, 1.0D - vertical * vertical));
            Vec3 dir = new Vec3(
                    Math.cos(angle) * horizontal,
                    vertical,
                    Math.sin(angle) * horizontal
            ).normalize();

            double flicker = 0.82D + 0.22D * Math.sin(t * 0.61D + i * 2.13D);
            Vec3 start = dir.scale(radius * 0.76D);
            Vec3 end = dir.scale(radius * (1.18D + 0.38D * flicker));
            float width = 0.025F + radius * 0.010F;

            drawJaggedBolt(
                    pose,
                    start,
                    end,
                    t,
                    i * 17 + 3,
                    width,
                    0.02F, 0.46F, 1.00F,
                    (0.50F + 0.20F * (float) flicker) * strength
            );
        }
    }

    /**
     * Rayos anime pegados a la superficie del haz completo.
     * Van girando alrededor del Kamehameha y se ven también en la zona que arrastra al jugador.
     */
    private static void drawKameBeamLightning(PoseStack pose, Vec3 start, Vec3 end,
                                               float t, float beamRadius, float fade) {
        if (fade <= 0.01F || beamRadius <= 0.08F) return;

        Vec3 axis = end.subtract(start);
        double length = axis.length();
        if (length < 0.1D) return;
        Vec3 dir = axis.normalize();
        Vec3 helper = Math.abs(dir.y) < 0.90D ? new Vec3(0.0D, 1.0D, 0.0D) : new Vec3(0.0D, 0.0D, 1.0D);
        Vec3 u = dir.cross(helper).normalize();
        Vec3 v = dir.cross(u).normalize();

        // Varias cadenas de rayos repartidas por todo el largo del ataque.
        final int chains = 7;
        final int sections = 7;
        for (int chain = 0; chain < chains; chain++) {
            double baseAngle = chain * (Math.PI * 2.0D / chains) + t * (0.105D + chain * 0.003D);
            for (int section = 0; section < sections; section++) {
                double q0 = section / (double) sections;
                double q1 = (section + 0.72D) / sections;
                q1 = Math.min(1.0D, q1);

                double wave0 = baseAngle + q0 * Math.PI * 3.2D + Math.sin(t * 0.19D + section) * 0.22D;
                double wave1 = baseAngle + q1 * Math.PI * 3.2D + Math.sin(t * 0.19D + section + 1) * 0.22D;
                double shell0 = beamRadius * (1.02D + 0.12D * Math.sin(t * 0.31D + section * 1.7D + chain));
                double shell1 = beamRadius * (1.04D + 0.15D * Math.cos(t * 0.27D + section * 1.3D + chain));

                Vec3 center0 = start.add(dir.scale(length * q0));
                Vec3 center1 = start.add(dir.scale(length * q1));
                Vec3 p0 = center0
                        .add(u.scale(Math.cos(wave0) * shell0))
                        .add(v.scale(Math.sin(wave0) * shell0));
                Vec3 p1 = center1
                        .add(u.scale(Math.cos(wave1) * shell1))
                        .add(v.scale(Math.sin(wave1) * shell1));

                float flicker = 0.55F + 0.25F * Mth.sin(t * 0.63F + chain * 2.2F + section);
                drawJaggedBolt(
                        pose, p0, p1, t,
                        100 + chain * 31 + section * 7,
                        0.045F + beamRadius * 0.005F,
                        0.02F, 0.50F, 1.00F,
                        Math.max(0.18F, flicker) * fade
                );
            }
        }

        // Rayos cortos que saltan hacia afuera desde el haz para darle el look de anime.
        final int bursts = 12;
        for (int i = 0; i < bursts; i++) {
            double q = ((t * 0.020D + i * 0.083D) % 1.0D);
            double angle = i * (Math.PI * 2.0D / bursts) - t * 0.135D;
            Vec3 radial = u.scale(Math.cos(angle)).add(v.scale(Math.sin(angle))).normalize();
            Vec3 center = start.add(dir.scale(length * q));
            Vec3 p0 = center.add(radial.scale(beamRadius * 0.88D));
            Vec3 p1 = center.add(radial.scale(beamRadius * (1.35D + 0.30D * Math.sin(t * 0.41D + i))));
            drawJaggedBolt(pose, p0, p1, t, 500 + i * 19,
                    0.035F + beamRadius * 0.004F,
                    0.05F, 0.62F, 1.00F,
                    0.58F * fade);
        }
    }

    /**
     * Energía celeste que viaja desde alrededor hacia la Genki Dama.
     * La esfera original sigue creciendo exactamente con la misma fórmula; esto solo añade la sensación de absorción.
     */
    private static void drawGenkiChargeEnergy(PoseStack pose, float t, float radius) {
        float strength = smooth(4.0F, 32.0F, t);
        if (strength <= 0.01F) return;

        // Aura exterior que hace que el crecimiento se perciba más brillante sin tocar el núcleo original.
        float auraPulse = 1.0F + Mth.sin(t * 0.21F) * 0.018F;
        glowSphere(pose, radius * 1.12F * auraPulse, 0.08F, 0.88F, 1.00F,
                0.055F * strength, 64, 32);

        // Brillitos que nacen lejos y convergen sobre la superficie de la esfera.
        final int sparkles = 20;
        for (int i = 0; i < sparkles; i++) {
            float cycle = ((t * 1.30F + i * 6.7F) % 86.0F) / 86.0F;
            float angle = i * TAU / sparkles + t * 0.020F;
            float outer = 10.0F + (i % 4) * 1.55F;
            float surface = Math.max(radius * 0.90F, 0.45F);
            float radial = Mth.lerp(cycle, outer, surface);
            float verticalStart = -7.5F + (i % 6) * 3.0F;
            float y = Mth.lerp(cycle, verticalStart, 0.0F)
                    + Mth.sin(angle * 2.0F + i) * (1.0F - cycle) * 1.15F;
            float x = Mth.cos(angle) * radial;
            float z = Mth.sin(angle) * radial;
            float twinkle = 0.10F + 0.12F * (0.5F + 0.5F * Mth.sin(t * 0.52F + i));
            float size = twinkle * (0.65F + cycle * 0.75F) * strength;

            pose.pushPose();
            pose.translate(x, y, z);
            smallCelesteOrb(pose, size);
            pose.popPose();

            // Las primeras corrientes dejan una estela corta y brillante camino a la esfera.
            if (i < 9) {
                float previous = Math.max(0.0F, cycle - 0.075F);
                float oldRadial = Mth.lerp(previous, outer, surface);
                float oldY = Mth.lerp(previous, verticalStart, 0.0F)
                        + Mth.sin(angle * 2.0F + i) * (1.0F - previous) * 1.15F;
                Vec3 from = new Vec3(Mth.cos(angle) * oldRadial, oldY, Mth.sin(angle) * oldRadial);
                Vec3 to = new Vec3(x, y, z);
                glowRibbon(pose, from, to,
                        0.06F, 0.88F, 1.00F,
                        0.28F * strength * (0.45F + cycle * 0.55F),
                        0.045F + 0.025F * cycle);
            }
        }

        // NUEVO: energía que llega literalmente desde TODAS las direcciones alrededor de la Genki.
        // Distribución casi esférica usando el ángulo áureo: arriba, abajo, lados y diagonales.
        final int omniMotes = 38;
        final double goldenAngle = Math.PI * (3.0D - Math.sqrt(5.0D));
        for (int i = 0; i < omniMotes; i++) {
            double yDir = 1.0D - 2.0D * ((i + 0.5D) / omniMotes);
            double horizontal = Math.sqrt(Math.max(0.0D, 1.0D - yDir * yDir));
            double phi = i * goldenAngle + t * 0.018D;
            Vec3 dir = new Vec3(Math.cos(phi) * horizontal, yDir, Math.sin(phi) * horizontal).normalize();

            float cycle = ((t * 1.55F + i * 4.25F) % 74.0F) / 74.0F;
            double outer = 14.0D + (i % 6) * 1.75D;
            double surface = Math.max(0.55D, radius * (0.96D + 0.025D * Math.sin(i * 1.9D)));
            double distance = Mth.lerp(cycle, (float) outer, (float) surface);

            // Pequeña curva lateral para que no parezca que todas viajan en líneas perfectamente rectas.
            Vec3 helper = Math.abs(dir.y) < 0.88D ? new Vec3(0.0D, 1.0D, 0.0D) : new Vec3(1.0D, 0.0D, 0.0D);
            Vec3 tangent = dir.cross(helper).normalize();
            double curve = Math.sin(t * 0.14D + i * 1.31D + cycle * Math.PI) * (1.0D - cycle) * 1.15D;
            Vec3 pos = dir.scale(distance).add(tangent.scale(curve));

            float blink = 0.11F + 0.10F * (0.5F + 0.5F * Mth.sin(t * 0.67F + i * 2.37F));
            float size = blink * (0.65F + cycle * 0.95F) * strength;
            pose.pushPose();
            pose.translate(pos.x, pos.y, pos.z);
            smallCelesteOrb(pose, size);
            pose.popPose();

            // Estela de la bolita acercándose a la esfera.
            float previousCycle = Math.max(0.0F, cycle - 0.070F);
            double previousDistance = Mth.lerp(previousCycle, (float) outer, (float) surface);
            double previousCurve = Math.sin(t * 0.14D + i * 1.31D + previousCycle * Math.PI)
                    * (1.0D - previousCycle) * 1.15D;
            Vec3 previous = dir.scale(previousDistance).add(tangent.scale(previousCurve));
            glowRibbon(pose, previous, pos,
                    0.05F, 0.90F, 1.00F,
                    0.23F * strength * (0.35F + cycle * 0.65F),
                    0.035F + cycle * 0.025F);
        }

        // Destellos pegados a la superficie para que parezca que la energía se integra a la esfera.
        final int surfaceSparks = 10;
        for (int i = 0; i < surfaceSparks; i++) {
            float angle = i * TAU / surfaceSparks + t * 0.037F;
            float latitude = Mth.sin(t * 0.041F + i * 1.37F) * 0.72F;
            float horizontal = Mth.sqrt(Math.max(0.05F, 1.0F - latitude * latitude));
            float shell = radius * 1.025F;
            float x = Mth.cos(angle) * horizontal * shell;
            float y = latitude * shell;
            float z = Mth.sin(angle) * horizontal * shell;
            float blink = 0.07F + 0.09F * (0.5F + 0.5F * Mth.sin(t * 0.70F + i * 2.1F));

            pose.pushPose();
            pose.translate(x, y, z);
            smallCelesteOrb(pose, blink * strength);
            pose.popPose();
        }
    }

    private static void drawJaggedBolt(PoseStack pose, Vec3 start, Vec3 end, float t, int seed,
                                       float width, float r, float g, float b, float a) {
        Vec3 axis = end.subtract(start);
        if (axis.lengthSqr() < 0.0001D) return;

        Vec3 dir = axis.normalize();
        Vec3 helper = Math.abs(dir.y) < 0.90D ? new Vec3(0.0D, 1.0D, 0.0D) : new Vec3(1.0D, 0.0D, 0.0D);
        Vec3 u = dir.cross(helper).normalize();
        Vec3 v = dir.cross(u).normalize();

        Vec3 previous = start;
        final int segments = 5;
        double frame = Math.floor(t * 0.72D);
        for (int s = 1; s <= segments; s++) {
            double q = s / (double) segments;
            Vec3 current;
            if (s == segments) {
                current = end;
            } else {
                double envelope = Math.sin(Math.PI * q);
                double jitterScale = Math.max(0.05D, width * 5.8D) * envelope;
                double j1 = Math.sin(frame * 1.91D + seed * 0.73D + s * 2.37D) * jitterScale;
                double j2 = Math.cos(frame * 1.47D + seed * 1.11D + s * 1.83D) * jitterScale;
                current = start.add(axis.scale(q)).add(u.scale(j1)).add(v.scale(j2));
            }

            glowRibbon(pose, previous, current, r, g, b, a, width);
            previous = current;
        }
    }

    private static void glowRibbon(PoseStack pose, Vec3 start, Vec3 end,
                                   float r, float g, float b, float a, float width) {
        if (a <= 0.005F || width <= 0.002F) return;
        setGlow();
        Matrix4f matrix = pose.last().pose();
        BufferBuilder builder = Tesselator.getInstance().getBuilder();
        builder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);

        float x0 = (float) start.x;
        float y0 = (float) start.y;
        float z0 = (float) start.z;
        float x1 = (float) end.x;
        float y1 = (float) end.y;
        float z1 = (float) end.z;
        float w = width;

        // Dos planos cruzados para que el rayo se vea desde casi cualquier ángulo.
        vertex(builder, matrix, x0, y0 - w, z0, r, g, b, a);
        vertex(builder, matrix, x0, y0 + w, z0, r, g, b, a);
        vertex(builder, matrix, x1, y1 + w, z1, r, g, b, a);
        vertex(builder, matrix, x1, y1 - w, z1, r, g, b, a);

        vertex(builder, matrix, x0 - w, y0, z0, r, g, b, a * 0.78F);
        vertex(builder, matrix, x0 + w, y0, z0, r, g, b, a * 0.78F);
        vertex(builder, matrix, x1 + w, y1, z1, r, g, b, a * 0.78F);
        vertex(builder, matrix, x1 - w, y1, z1, r, g, b, a * 0.78F);

        BufferUploader.drawWithShader(builder.end());
    }

    private static void blueEnergySphere(PoseStack pose, float radius) {
        if (radius <= 0.03F) return;
        solidSphere(pose, radius, 0.015F, 0.08F, 0.72F, 88, 48);
        solidSphere(pose, radius * 0.76F, 0.020F, 0.30F, 1.00F, 88, 48);
        solidSphere(pose, radius * 0.30F, 0.09F, 0.58F, 1.00F, 72, 40);
        glowSphere(pose, radius * 1.045F, 0.02F, 0.22F, 1.00F, 0.18F, 88, 48);
        glowSphere(pose, radius * 1.085F, 0.00F, 0.08F, 1.00F, 0.085F, 72, 40);
    }

    private static void celesteEnergySphere(PoseStack pose, float radius) {
        if (radius <= 0.03F) return;
        solidSphere(pose, radius, 0.00F, 0.48F, 0.96F, 88, 48);
        solidSphere(pose, radius * 0.78F, 0.02F, 0.76F, 1.00F, 88, 48);
        solidSphere(pose, radius * 0.34F, 0.16F, 0.94F, 1.00F, 72, 40);
        glowSphere(pose, radius * 1.045F, 0.00F, 0.78F, 1.00F, 0.19F, 88, 48);
        glowSphere(pose, radius * 1.085F, 0.06F, 0.92F, 1.00F, 0.10F, 72, 40);
    }

    private static void smallCelesteOrb(PoseStack pose, float radius) {
        solidSphere(pose, radius, 0.02F, 0.72F, 1.00F, 20, 12);
        glowSphere(pose, radius * 1.35F, 0.08F, 0.90F, 1.00F, 0.22F, 20, 12);
    }

    private static void solidSphere(PoseStack pose, float radius, float r, float g, float b, int lon, int lat) {
        if (radius <= 0.02F) return;
        setSolid();
        sphere(pose, radius, r, g, b, 1.0F, lon, lat);
    }

    private static void glowSphere(PoseStack pose, float radius, float r, float g, float b, float a, int lon, int lat) {
        if (radius <= 0.02F || a <= 0.005F) return;
        setGlow();
        sphere(pose, radius, r, g, b, a, lon, lat);
    }

    private static void sphere(PoseStack pose, float radius, float r, float g, float b, float a, int lon, int lat) {
        Matrix4f matrix = pose.last().pose();
        BufferBuilder builder = Tesselator.getInstance().getBuilder();
        builder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);

        for (int iy = 0; iy < lat; iy++) {
            float p0 = ((float) iy / lat - 0.5F) * Mth.PI;
            float p1 = ((float) (iy + 1) / lat - 0.5F) * Mth.PI;
            for (int ix = 0; ix < lon; ix++) {
                float a0 = ix * TAU / lon;
                float a1 = (ix + 1) * TAU / lon;
                sphereVertex(builder, matrix, radius, p0, a0, r, g, b, a);
                sphereVertex(builder, matrix, radius, p0, a1, r, g, b, a);
                sphereVertex(builder, matrix, radius, p1, a1, r, g, b, a);
                sphereVertex(builder, matrix, radius, p1, a0, r, g, b, a);
            }
        }
        BufferUploader.drawWithShader(builder.end());
    }

    private static void sphereVertex(BufferBuilder builder, Matrix4f matrix, float radius, float lat, float lon,
                                     float r, float g, float b, float a) {
        float c = Mth.cos(lat);
        vertex(builder, matrix,
                radius * c * Mth.cos(lon),
                radius * Mth.sin(lat),
                radius * c * Mth.sin(lon),
                r, g, b, a);
    }

    private static void solidCylinder(PoseStack pose, Vec3 start, Vec3 end, float radius,
                                      float r, float g, float b, float a) {
        if (radius <= 0.02F || a <= 0.005F) return;
        setSolid();
        cylinder(pose, start, end, radius, r, g, b, a);
    }

    private static void glowCylinder(PoseStack pose, Vec3 start, Vec3 end, float radius,
                                     float r, float g, float b, float a) {
        if (radius <= 0.02F || a <= 0.005F) return;
        setGlow();
        cylinder(pose, start, end, radius, r, g, b, a);
    }

    private static void cylinder(PoseStack pose, Vec3 start, Vec3 end, float radius,
                                 float r, float g, float b, float a) {
        Vec3 axis = end.subtract(start);
        if (axis.lengthSqr() < 0.0001D) return;
        axis = axis.normalize();

        Vec3 helper = Math.abs(axis.y) < 0.95D ? new Vec3(0.0D, 1.0D, 0.0D) : new Vec3(1.0D, 0.0D, 0.0D);
        Vec3 u = axis.cross(helper).normalize();
        Vec3 v = axis.cross(u).normalize();

        Matrix4f matrix = pose.last().pose();
        BufferBuilder builder = Tesselator.getInstance().getBuilder();
        builder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);

        final int sides = 72;
        for (int i = 0; i < sides; i++) {
            double a0 = i * Math.PI * 2.0D / sides;
            double a1 = (i + 1) * Math.PI * 2.0D / sides;

            Vec3 off0 = u.scale(Math.cos(a0) * radius).add(v.scale(Math.sin(a0) * radius));
            Vec3 off1 = u.scale(Math.cos(a1) * radius).add(v.scale(Math.sin(a1) * radius));

            Vec3 s0 = start.add(off0);
            Vec3 s1 = start.add(off1);
            Vec3 e0 = end.add(off0);
            Vec3 e1 = end.add(off1);

            vertex(builder, matrix, (float) s0.x, (float) s0.y, (float) s0.z, r, g, b, a);
            vertex(builder, matrix, (float) e0.x, (float) e0.y, (float) e0.z, r, g, b, a);
            vertex(builder, matrix, (float) e1.x, (float) e1.y, (float) e1.z, r, g, b, a);
            vertex(builder, matrix, (float) s1.x, (float) s1.y, (float) s1.z, r, g, b, a);
        }
        BufferUploader.drawWithShader(builder.end());
    }

    private static void vertex(BufferBuilder builder, Matrix4f matrix,
                               float x, float y, float z,
                               float r, float g, float b, float a) {
        builder.vertex(matrix, x, y, z)
                .color(toColor(r), toColor(g), toColor(b), toColor(a))
                .endVertex();
    }

    private static void setSolid() {
        RenderSystem.enableDepthTest();
        RenderSystem.disableBlend();
        RenderSystem.depthMask(true);
        RenderSystem.disableCull();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
    }

    private static void setGlow() {
        RenderSystem.enableDepthTest();
        RenderSystem.enableBlend();
        RenderSystem.blendFuncSeparate(
                com.mojang.blaze3d.platform.GlStateManager.SourceFactor.SRC_ALPHA,
                com.mojang.blaze3d.platform.GlStateManager.DestFactor.ONE,
                com.mojang.blaze3d.platform.GlStateManager.SourceFactor.ONE,
                com.mojang.blaze3d.platform.GlStateManager.DestFactor.ONE
        );
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
    }

    private static int toColor(float value) {
        return Mth.clamp((int) (value * 255.0F), 0, 255);
    }

    private static float smooth(float start, float end, float value) {
        float x = Mth.clamp((value - start) / (end - start), 0.0F, 1.0F);
        return x * x * (3.0F - 2.0F * x);
    }
}
