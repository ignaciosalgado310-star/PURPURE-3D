package com.igy.purpure3d.v2;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * PURPURE 3D V2
 * Visual principal: mallas 3D texturizadas (UV spheres, torus y cilindro), SIN particulas.
 * Pensado para Forge 1.20.1 y para activarse desde consola/Stream To Earn con /purpure.
 */
@Mod.EventBusSubscriber(modid = PurpureV2.MODID)
public final class PurpureV2 {
    public static final String MODID = "purpure3d";

    // 28 segundos base. Si llegan mas eventos, se acumulan golpes y puede prolongarse la fase final.
    private static final int BLUE_RED_END = 160;   // 0 - 8 s
    private static final int MERGE_END = 300;      // 8 - 15 s
    private static final int BLAST_START = 380;    // 19 s
    private static final int BASE_TOTAL = 560;     // 28 s
    private static final int HIT_INTERVAL = 4;     // un golpe cada 0.20 s durante la fase Purple
    private static final int DEFAULT_HITS = 25;
    private static final int MAX_TOTAL = 1200;      // limite: 60 s si se acumulan muchisimos eventos

    private static final String PROTOCOL = "2";
    private static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(MODID, "purpure_v2"),
            () -> PROTOCOL,
            PROTOCOL::equals,
            PROTOCOL::equals
    );

    private static final Map<UUID, ActiveSequence> ACTIVE = new LinkedHashMap<>();

    static {
        CHANNEL.registerMessage(0, EffectPacket.class, EffectPacket::encode, EffectPacket::decode, EffectPacket::handle);
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientVisuals.init());
    }

    private PurpureV2() {}

    /**
     * LOWEST hace que este registro entre al final y sustituya la ejecucion anterior de /purpure
     * sin tener que borrar la version funcional del proyecto.
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void registerCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(
                Commands.literal("purpure")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(ctx -> start(
                                        EntityArgument.getPlayer(ctx, "player"),
                                        DEFAULT_HITS,
                                        ctx.getSource()
                                ))
                                .then(Commands.argument("golpes", IntegerArgumentType.integer(1, 1000))
                                        .executes(ctx -> start(
                                                EntityArgument.getPlayer(ctx, "player"),
                                                IntegerArgumentType.getInteger(ctx, "golpes"),
                                                ctx.getSource()
                                        )))
                        )
        );
    }

    private static int start(ServerPlayer player, int hits, net.minecraft.commands.CommandSourceStack source) {
        ServerLevel level = player.serverLevel();
        long now = level.getGameTime();
        ActiveSequence sequence = ACTIVE.get(player.getUUID());

        if (sequence == null) {
            sequence = new ActiveSequence(
                    player,
                    now,
                    now + BASE_TOTAL,
                    player.getX(),
                    player.getY(),
                    player.getZ(),
                    hits
            );
            ensureEnoughBlastTime(sequence, now);
            ACTIVE.put(player.getUUID(), sequence);

            level.playSound(null, player.blockPosition(), SoundEvents.BEACON_ACTIVATE, SoundSource.MASTER, 4.0F, 0.55F);
            level.playSound(null, player.blockPosition(), SoundEvents.WITHER_SPAWN, SoundSource.MASTER, 2.0F, 0.45F);
        } else {
            sequence.pendingHits += hits;
            ensureEnoughBlastTime(sequence, now);
            level.playSound(null, player.blockPosition(), SoundEvents.BEACON_POWER_SELECT, SoundSource.MASTER, 2.0F, 0.75F);
        }

        sync(sequence, now);
        int finalHits = sequence.pendingHits;
        source.sendSuccess(() -> Component.literal("§5§lPURPURE 3D §dV2 iniciado para §f" + player.getGameProfile().getName()
                + " §7(" + finalHits + " golpes acumulados)"), true);
        return 1;
    }

    private static void ensureEnoughBlastTime(ActiveSequence sequence, long now) {
        long blastAbsolute = sequence.startTick + BLAST_START;
        long requiredEnd = blastAbsolute + (long) sequence.pendingHits * HIT_INTERVAL + 60L;
        long wanted = Math.max(sequence.endTick, requiredEnd);
        sequence.endTick = Math.min(sequence.startTick + MAX_TOTAL, wanted);
        if (sequence.endTick < now + 40) {
            sequence.endTick = Math.min(sequence.startTick + MAX_TOTAL, now + 40);
        }
    }

    private static void sync(ActiveSequence sequence, long now) {
        int elapsed = (int) Math.max(0, now - sequence.startTick);
        int total = (int) Math.max(BASE_TOTAL, sequence.endTick - sequence.startTick);
        EffectPacket packet = new EffectPacket(
                sequence.player.getUUID(),
                sequence.lockX,
                sequence.lockY,
                sequence.lockZ,
                elapsed,
                total
        );
        CHANNEL.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> sequence.player), packet);
    }

    @SubscribeEvent
    public static void serverTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || ACTIVE.isEmpty()) return;

        Iterator<Map.Entry<UUID, ActiveSequence>> iterator = ACTIVE.entrySet().iterator();
        while (iterator.hasNext()) {
            ActiveSequence sequence = iterator.next().getValue();
            ServerPlayer player = sequence.player;

            if (player.isRemoved() || !player.isAlive()) {
                iterator.remove();
                continue;
            }

            ServerLevel level = player.serverLevel();
            long now = level.getGameTime();
            int age = (int) (now - sequence.startTick);

            if (now >= sequence.endTick) {
                iterator.remove();
                continue;
            }

            // Inmovilizacion fuerte durante toda la cinematica.
            player.setDeltaMovement(Vec3.ZERO);
            player.fallDistance = 0.0F;
            if ((age & 1) == 0) {
                player.teleportTo(sequence.lockX, sequence.lockY, sequence.lockZ);
            }

            if (age == BLUE_RED_END) {
                level.playSound(null, player.blockPosition(), SoundEvents.RESPAWN_ANCHOR_CHARGE, SoundSource.MASTER, 5.0F, 0.55F);
            }
            if (age == MERGE_END) {
                level.playSound(null, player.blockPosition(), SoundEvents.END_PORTAL_SPAWN, SoundSource.MASTER, 4.0F, 0.65F);
                level.playSound(null, player.blockPosition(), SoundEvents.ENDER_DRAGON_GROWL, SoundSource.MASTER, 3.0F, 0.55F);
            }
            if (age == BLAST_START) {
                level.playSound(null, player.blockPosition(), SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.MASTER, 8.0F, 0.45F);
                level.playSound(null, player.blockPosition(), SoundEvents.GENERIC_EXPLODE, SoundSource.MASTER, 4.0F, 0.55F);
                carveCrater(level, BlockPos.containing(sequence.lockX, sequence.lockY, sequence.lockZ), 9);
            }

            if (age >= BLAST_START && ((age - BLAST_START) % HIT_INTERVAL == 0) && sequence.pendingHits > 0) {
                sequence.pendingHits--;
                player.invulnerableTime = 0;
                player.hurt(player.damageSources().magic(), 1000.0F);
                player.invulnerableTime = 0;
            }
        }
    }

    /** Crater manual: destruye bloques sin invocar una explosion con nube de particulas. */
    private static void carveCrater(ServerLevel level, BlockPos center, int radius) {
        int r2 = radius * radius;
        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                for (int y = -radius / 2; y <= 1; y++) {
                    double shaped = x * x + z * z + (y * y * 2.2);
                    if (shaped > r2) continue;
                    BlockPos pos = center.offset(x, y, z);
                    if (!level.getBlockState(pos).isAir() && !level.getBlockState(pos).is(Blocks.BEDROCK)) {
                        level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
                    }
                }
            }
        }
    }

    private static final class ActiveSequence {
        final ServerPlayer player;
        final long startTick;
        long endTick;
        final double lockX;
        final double lockY;
        final double lockZ;
        int pendingHits;

        ActiveSequence(ServerPlayer player, long startTick, long endTick,
                       double lockX, double lockY, double lockZ, int pendingHits) {
            this.player = player;
            this.startTick = startTick;
            this.endTick = endTick;
            this.lockX = lockX;
            this.lockY = lockY;
            this.lockZ = lockZ;
            this.pendingHits = pendingHits;
        }
    }

    private record EffectPacket(UUID target, double x, double y, double z, int elapsed, int total) {
        static void encode(EffectPacket msg, FriendlyByteBuf buf) {
            buf.writeUUID(msg.target);
            buf.writeDouble(msg.x);
            buf.writeDouble(msg.y);
            buf.writeDouble(msg.z);
            buf.writeVarInt(msg.elapsed);
            buf.writeVarInt(msg.total);
        }

        static EffectPacket decode(FriendlyByteBuf buf) {
            return new EffectPacket(
                    buf.readUUID(),
                    buf.readDouble(),
                    buf.readDouble(),
                    buf.readDouble(),
                    buf.readVarInt(),
                    buf.readVarInt()
            );
        }

        static void handle(EffectPacket msg, Supplier<NetworkEvent.Context> contextSupplier) {
            NetworkEvent.Context context = contextSupplier.get();
            context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientVisuals.accept(msg)));
            context.setPacketHandled(true);
        }
    }

    /**
     * Todo lo que hay aqui es CLIENTE. No se crean ParticleTypes.
     * Las superficies se dibujan como geometria 3D real con UV y textura procedural 256x256.
     */
    private static final class ClientVisuals {
        private static final Map<UUID, ClientEffect> EFFECTS = new LinkedHashMap<>();
        private static boolean initialized;
        private static boolean texturesReady;

        private static ResourceLocation BLUE_TEXTURE;
        private static ResourceLocation RED_TEXTURE;
        private static ResourceLocation PURPLE_TEXTURE;
        private static ResourceLocation RING_TEXTURE;
        private static ResourceLocation BEAM_TEXTURE;

        // Mallas precalculadas: modelos 3D suaves, no sprites ni particulas.
        private static Mesh SPHERE;
        private static Mesh TORUS;
        private static Mesh CYLINDER;

        static void init() {
            if (initialized) return;
            initialized = true;
            MinecraftForge.EVENT_BUS.addListener(ClientVisuals::renderLevel);
        }

        static void accept(EffectPacket msg) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.level == null) return;
            long now = mc.level.getGameTime();
            EFFECTS.put(msg.target, new ClientEffect(
                    msg.target,
                    msg.x,
                    msg.y,
                    msg.z,
                    now - msg.elapsed,
                    msg.total
            ));
        }

        private static void ensureAssets() {
            if (texturesReady) return;
            texturesReady = true;

            BLUE_TEXTURE = makeEnergyTexture("v2_blue", 40, 125, 255, 11L, false);
            RED_TEXTURE = makeEnergyTexture("v2_red", 255, 40, 55, 29L, false);
            PURPLE_TEXTURE = makeEnergyTexture("v2_purple", 185, 35, 255, 71L, false);
            RING_TEXTURE = makeEnergyTexture("v2_ring", 220, 105, 255, 101L, true);
            BEAM_TEXTURE = makeBeamTexture("v2_beam", 202, 70, 255, 131L);

            SPHERE = Mesh.sphere(28, 56);
            TORUS = Mesh.torus(72, 12, 0.065F);
            CYLINDER = Mesh.cylinder(64);
        }

        private static ResourceLocation makeEnergyTexture(String name, int baseR, int baseG, int baseB,
                                                          long seed, boolean ringStyle) {
            final int size = 256;
            NativeImage image = new NativeImage(size, size, true);
            Random random = new Random(seed);
            double[][] noise = new double[size][size];
            for (int y = 0; y < size; y++) {
                for (int x = 0; x < size; x++) {
                    noise[x][y] = random.nextDouble();
                }
            }

            for (int y = 0; y < size; y++) {
                for (int x = 0; x < size; x++) {
                    double nx = (x + 0.5 - size / 2.0) / (size / 2.0);
                    double ny = (y + 0.5 - size / 2.0) / (size / 2.0);
                    double radius = Math.sqrt(nx * nx + ny * ny);
                    double angle = Math.atan2(ny, nx);

                    double edge = Math.max(0.0, 1.0 - radius);
                    double swirl = 0.5 + 0.5 * Math.sin(angle * (ringStyle ? 12.0 : 7.0) - radius * 31.0);
                    double bands = 0.5 + 0.5 * Math.sin(radius * 72.0 + angle * 4.0);
                    double n = noise[x][y];
                    double intensity = edge * (0.62 + 0.22 * swirl + 0.12 * bands + 0.12 * n);
                    if (ringStyle) intensity = Math.min(1.0, intensity * 1.20);

                    int alpha = clamp255((int) (255.0 * Math.pow(edge, 0.72)));
                    if (radius > 1.0) alpha = 0;
                    double whiteCore = Math.pow(Math.max(0.0, 1.0 - radius * 1.35), 2.8);

                    int r = clamp255((int) Mth.lerp((float) whiteCore, baseR, 255));
                    int g = clamp255((int) Mth.lerp((float) whiteCore, baseG, 255));
                    int b = clamp255((int) Mth.lerp((float) whiteCore, baseB, 255));
                    r = clamp255((int) (r * (0.72 + 0.40 * intensity)));
                    g = clamp255((int) (g * (0.72 + 0.40 * intensity)));
                    b = clamp255((int) (b * (0.72 + 0.40 * intensity)));

                    // NativeImage usa ABGR internamente.
                    int abgr = (alpha << 24) | (b << 16) | (g << 8) | r;
                    image.setPixelRGBA(x, y, abgr);
                }
            }

            ResourceLocation id = new ResourceLocation(MODID, "dynamic/" + name);
            Minecraft.getInstance().getTextureManager().register(id, new DynamicTexture(image));
            return id;
        }

        private static ResourceLocation makeBeamTexture(String name, int baseR, int baseG, int baseB, long seed) {
            final int size = 256;
            NativeImage image = new NativeImage(size, size, true);
            Random random = new Random(seed);
            double[] streak = new double[size];
            for (int x = 0; x < size; x++) streak[x] = random.nextDouble();

            for (int y = 0; y < size; y++) {
                double v = y / 255.0;
                for (int x = 0; x < size; x++) {
                    double u = x / 255.0;
                    double pulse = 0.62 + 0.38 * Math.sin(v * 45.0 + u * 9.0);
                    double line = 0.65 + 0.35 * Math.pow(streak[x], 2.0);
                    double hot = 0.35 + 0.65 * Math.pow(Math.abs(Math.sin(u * Math.PI * 8.0)), 5.0);
                    int a = clamp255((int) (235 * pulse * line));
                    int r = clamp255((int) Mth.lerp((float) hot, baseR, 255));
                    int g = clamp255((int) Mth.lerp((float) hot, baseG, 255));
                    int b = clamp255((int) Mth.lerp((float) hot, baseB, 255));
                    image.setPixelRGBA(x, y, (a << 24) | (b << 16) | (g << 8) | r);
                }
            }

            ResourceLocation id = new ResourceLocation(MODID, "dynamic/" + name);
            Minecraft.getInstance().getTextureManager().register(id, new DynamicTexture(image));
            return id;
        }

        private static int clamp255(int value) {
            return Math.max(0, Math.min(255, value));
        }

        private static void renderLevel(RenderLevelStageEvent event) {
            if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
            Minecraft mc = Minecraft.getInstance();
            if (mc.level == null || EFFECTS.isEmpty()) return;

            ensureAssets();

            long gameTime = mc.level.getGameTime();
            float partial = event.getPartialTick();
            Vec3 camera = event.getCamera().getPosition();
            PoseStack poseStack = event.getPoseStack();
            MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();

            Iterator<Map.Entry<UUID, ClientEffect>> iterator = EFFECTS.entrySet().iterator();
            while (iterator.hasNext()) {
                ClientEffect effect = iterator.next().getValue();
                float age = (float) (gameTime - effect.localStartTick) + partial;
                if (age > effect.totalTicks + 10.0F) {
                    iterator.remove();
                    continue;
                }
                renderEffect(effect, age, camera, poseStack, buffers);
            }

            buffers.endBatch();
        }

        private static void renderEffect(ClientEffect effect, float age, Vec3 camera,
                                         PoseStack poseStack, MultiBufferSource.BufferSource buffers) {
            double cx = effect.x - camera.x;
            double cy = effect.y + 8.0 - camera.y;
            double cz = effect.z - camera.z;

            poseStack.pushPose();
            poseStack.translate(cx, cy, cz);

            // FASE 1: Azul y Rojo gigantes, flotando y girando durante 8 segundos.
            if (age < MERGE_END) {
                float build = smooth01(age / 85.0F);
                float merge = smooth01((age - BLUE_RED_END) / (float) (MERGE_END - BLUE_RED_END));
                float distance = Mth.lerp(merge, 10.5F, 0.35F);
                float orbit = age * 1.25F;
                float ox = Mth.cos((float) Math.toRadians(orbit)) * distance;
                float oz = Mth.sin((float) Math.toRadians(orbit)) * distance * 0.42F;
                float sphereRadius = (3.2F + build * 1.45F) * (1.0F + 0.035F * Mth.sin(age * 0.15F));

                renderEnergyOrb(poseStack, buffers, -ox, 0.40F + Mth.sin(age * 0.045F) * 0.55F, -oz,
                        sphereRadius, BLUE_TEXTURE, 70.0F + age * 1.4F, 0);
                renderEnergyOrb(poseStack, buffers, ox, -0.15F + Mth.cos(age * 0.043F) * 0.55F, oz,
                        sphereRadius, RED_TEXTURE, -55.0F - age * 1.55F, 1);

                // Orbitas enormes 3D alrededor del ritual completo.
                poseStack.pushPose();
                poseStack.mulPose(Axis.XP.rotationDegrees(66.0F));
                poseStack.mulPose(Axis.ZP.rotationDegrees(age * 0.55F));
                renderMesh(TORUS, poseStack, buffers, RING_TEXTURE, 15.5F, 2.1F, 15.5F, 155);
                poseStack.popPose();

                poseStack.pushPose();
                poseStack.mulPose(Axis.ZP.rotationDegrees(58.0F));
                poseStack.mulPose(Axis.YP.rotationDegrees(-age * 0.72F));
                renderMesh(TORUS, poseStack, buffers, RING_TEXTURE, 19.0F, 2.8F, 19.0F, 95);
                poseStack.popPose();
            }

            // FASE 2: fusion. La esfera morada nace pequena y llega a ser enorme.
            if (age >= MERGE_END) {
                float fusion = smooth01((age - MERGE_END) / (float) (BLAST_START - MERGE_END));
                float pulse = 1.0F + 0.055F * Mth.sin(age * 0.22F);
                float core = Mth.lerp(fusion, 1.0F, 10.8F) * pulse;

                renderMesh(SPHERE, poseStack, buffers, PURPLE_TEXTURE, core, core, core, 245);
                renderMesh(SPHERE, poseStack, buffers, PURPLE_TEXTURE, core * 1.18F, core * 1.18F, core * 1.18F, 105);
                renderMesh(SPHERE, poseStack, buffers, RING_TEXTURE, core * 1.42F, core * 1.42F, core * 1.42F, 42);

                for (int i = 0; i < 4; i++) {
                    poseStack.pushPose();
                    poseStack.mulPose(Axis.XP.rotationDegrees(24.0F + i * 37.0F + age * (0.30F + i * 0.08F)));
                    poseStack.mulPose(Axis.ZP.rotationDegrees(17.0F + i * 49.0F - age * (0.45F + i * 0.05F)));
                    float ringR = core * (1.35F + i * 0.12F);
                    renderMesh(TORUS, poseStack, buffers, RING_TEXTURE, ringR, ringR * 0.12F, ringR, 155 - i * 22);
                    poseStack.popPose();
                }
            }

            // FASE 3: HOLLOW PURPLE final. Dura 9 s base y se prolonga si se acumulan eventos.
            if (age >= BLAST_START) {
                float blastAge = age - BLAST_START;
                float entry = smooth01(blastAge / 24.0F);
                float pulse = 1.0F + 0.065F * Mth.sin(blastAge * 0.28F);

                // Purple de 32 bloques de diametro en su halo exterior.
                float coreR = 11.8F * entry * pulse;
                float haloR = 16.0F * entry * (1.0F + 0.025F * Mth.sin(blastAge * 0.17F));
                renderMesh(SPHERE, poseStack, buffers, PURPLE_TEXTURE, coreR, coreR, coreR, 255);
                renderMesh(SPHERE, poseStack, buffers, PURPLE_TEXTURE, coreR * 1.16F, coreR * 1.16F, coreR * 1.16F, 120);
                renderMesh(SPHERE, poseStack, buffers, RING_TEXTURE, haloR, haloR, haloR, 45);

                // Columna 3D de mas de 100 bloques de alto.
                poseStack.pushPose();
                poseStack.translate(0.0D, 47.0D, 0.0D);
                poseStack.mulPose(Axis.YP.rotationDegrees(blastAge * 1.9F));
                renderMesh(CYLINDER, poseStack, buffers, BEAM_TEXTURE, 7.6F * entry, 110.0F, 7.6F * entry, 180);
                poseStack.popPose();

                poseStack.pushPose();
                poseStack.translate(0.0D, 47.0D, 0.0D);
                poseStack.mulPose(Axis.YP.rotationDegrees(-blastAge * 2.7F));
                renderMesh(CYLINDER, poseStack, buffers, PURPLE_TEXTURE, 4.4F * entry, 110.0F, 4.4F * entry, 210);
                poseStack.popPose();

                // Ondas de choque: torus 3D reales, hasta ~120 bloques de diametro.
                float wave1 = 10.0F + (blastAge % 95.0F) / 95.0F * 50.0F;
                float wave2 = 10.0F + ((blastAge + 47.5F) % 95.0F) / 95.0F * 50.0F;
                renderGroundWave(poseStack, buffers, wave1, 155);
                renderGroundWave(poseStack, buffers, wave2, 105);
            }

            poseStack.popPose();
        }

        private static void renderEnergyOrb(PoseStack poseStack, MultiBufferSource.BufferSource buffers,
                                            float x, float y, float z, float radius,
                                            ResourceLocation texture, float rotation, int variant) {
            poseStack.pushPose();
            poseStack.translate(x, y, z);

            renderMesh(SPHERE, poseStack, buffers, texture, radius, radius, radius, 250);
            renderMesh(SPHERE, poseStack, buffers, texture, radius * 1.22F, radius * 1.22F, radius * 1.22F, 80);
            renderMesh(SPHERE, poseStack, buffers, RING_TEXTURE, radius * 1.43F, radius * 1.43F, radius * 1.43F, 30);

            for (int i = 0; i < 3; i++) {
                poseStack.pushPose();
                poseStack.mulPose(Axis.XP.rotationDegrees(rotation + i * 58.0F));
                poseStack.mulPose(Axis.ZP.rotationDegrees((variant == 0 ? 1 : -1) * rotation * 0.72F + i * 41.0F));
                float rr = radius * (1.48F + i * 0.18F);
                renderMesh(TORUS, poseStack, buffers, texture, rr, rr * 0.16F, rr, 165 - i * 30);
                poseStack.popPose();
            }

            poseStack.popPose();
        }

        private static void renderGroundWave(PoseStack poseStack, MultiBufferSource.BufferSource buffers,
                                             float radius, int alpha) {
            poseStack.pushPose();
            poseStack.translate(0.0D, -7.65D, 0.0D);
            renderMesh(TORUS, poseStack, buffers, RING_TEXTURE, radius, 0.65F, radius, alpha);
            poseStack.popPose();
        }

        private static void renderMesh(Mesh mesh, PoseStack poseStack, MultiBufferSource.BufferSource buffers,
                                       ResourceLocation texture, float sx, float sy, float sz, int alpha) {
            if (mesh == null || texture == null || sx <= 0.001F || sy <= 0.001F || sz <= 0.001F) return;

            poseStack.pushPose();
            poseStack.scale(sx, sy, sz);

            VertexConsumer consumer = buffers.getBuffer(RenderType.entityTranslucent(texture));
            PoseStack.Pose pose = poseStack.last();
            Matrix4f matrix = pose.pose();
            Matrix3f normal = pose.normal();

            for (MeshVertex v : mesh.vertices) {
                consumer.vertex(matrix, v.x, v.y, v.z)
                        .color(255, 255, 255, alpha)
                        .uv(v.u, v.v)
                        .overlayCoords(OverlayTexture.NO_OVERLAY)
                        .uv2(15728880)
                        .normal(normal, v.nx, v.ny, v.nz)
                        .endVertex();
            }

            poseStack.popPose();
        }

        private static float smooth01(float t) {
            t = Mth.clamp(t, 0.0F, 1.0F);
            return t * t * (3.0F - 2.0F * t);
        }

        private record ClientEffect(UUID target, double x, double y, double z, long localStartTick, int totalTicks) {}

        private record MeshVertex(float x, float y, float z, float u, float v, float nx, float ny, float nz) {}

        /** Malla triangulada con coordenadas UV. */
        private static final class Mesh {
            final List<MeshVertex> vertices = new ArrayList<>();

            static Mesh sphere(int latSegments, int lonSegments) {
                Mesh mesh = new Mesh();
                for (int lat = 0; lat < latSegments; lat++) {
                    float v0 = lat / (float) latSegments;
                    float v1 = (lat + 1) / (float) latSegments;
                    double p0 = -Math.PI / 2.0 + Math.PI * v0;
                    double p1 = -Math.PI / 2.0 + Math.PI * v1;

                    for (int lon = 0; lon < lonSegments; lon++) {
                        float u0 = lon / (float) lonSegments;
                        float u1 = (lon + 1) / (float) lonSegments;
                        double t0 = Math.PI * 2.0 * u0;
                        double t1 = Math.PI * 2.0 * u1;

                        MeshVertex a = sphereVertex(p0, t0, u0, 1.0F - v0);
                        MeshVertex b = sphereVertex(p1, t0, u0, 1.0F - v1);
                        MeshVertex c = sphereVertex(p1, t1, u1, 1.0F - v1);
                        MeshVertex d = sphereVertex(p0, t1, u1, 1.0F - v0);
                        quad(mesh, a, b, c, d);
                    }
                }
                return mesh;
            }

            private static MeshVertex sphereVertex(double phi, double theta, float u, float v) {
                float cp = (float) Math.cos(phi);
                float x = cp * (float) Math.cos(theta);
                float y = (float) Math.sin(phi);
                float z = cp * (float) Math.sin(theta);
                return new MeshVertex(x, y, z, u, v, x, y, z);
            }

            static Mesh torus(int majorSegments, int tubeSegments, float tubeRadius) {
                Mesh mesh = new Mesh();
                for (int i = 0; i < majorSegments; i++) {
                    float u0 = i / (float) majorSegments;
                    float u1 = (i + 1) / (float) majorSegments;
                    double a0 = u0 * Math.PI * 2.0;
                    double a1 = u1 * Math.PI * 2.0;

                    for (int j = 0; j < tubeSegments; j++) {
                        float v0 = j / (float) tubeSegments;
                        float v1 = (j + 1) / (float) tubeSegments;
                        double b0 = v0 * Math.PI * 2.0;
                        double b1 = v1 * Math.PI * 2.0;

                        MeshVertex p0 = torusVertex(a0, b0, tubeRadius, u0, v0);
                        MeshVertex p1 = torusVertex(a1, b0, tubeRadius, u1, v0);
                        MeshVertex p2 = torusVertex(a1, b1, tubeRadius, u1, v1);
                        MeshVertex p3 = torusVertex(a0, b1, tubeRadius, u0, v1);
                        quad(mesh, p0, p1, p2, p3);
                    }
                }
                return mesh;
            }

            private static MeshVertex torusVertex(double a, double b, float tube, float u, float v) {
                float ca = (float) Math.cos(a);
                float sa = (float) Math.sin(a);
                float cb = (float) Math.cos(b);
                float sb = (float) Math.sin(b);
                float radial = 1.0F + tube * cb;
                float x = radial * ca;
                float y = tube * sb;
                float z = radial * sa;
                float nx = cb * ca;
                float ny = sb;
                float nz = cb * sa;
                return new MeshVertex(x, y, z, u, v, nx, ny, nz);
            }

            static Mesh cylinder(int segments) {
                Mesh mesh = new Mesh();
                for (int i = 0; i < segments; i++) {
                    float u0 = i / (float) segments;
                    float u1 = (i + 1) / (float) segments;
                    double a0 = u0 * Math.PI * 2.0;
                    double a1 = u1 * Math.PI * 2.0;

                    float x0 = (float) Math.cos(a0);
                    float z0 = (float) Math.sin(a0);
                    float x1 = (float) Math.cos(a1);
                    float z1 = (float) Math.sin(a1);

                    MeshVertex a = new MeshVertex(x0, -0.5F, z0, u0, 1.0F, x0, 0, z0);
                    MeshVertex b = new MeshVertex(x0, 0.5F, z0, u0, 0.0F, x0, 0, z0);
                    MeshVertex c = new MeshVertex(x1, 0.5F, z1, u1, 0.0F, x1, 0, z1);
                    MeshVertex d = new MeshVertex(x1, -0.5F, z1, u1, 1.0F, x1, 0, z1);
                    quad(mesh, a, b, c, d);
                }
                return mesh;
            }

            private static void quad(Mesh mesh, MeshVertex a, MeshVertex b, MeshVertex c, MeshVertex d) {
                mesh.vertices.add(a);
                mesh.vertices.add(b);
                mesh.vertices.add(c);
                mesh.vertices.add(a);
                mesh.vertices.add(c);
                mesh.vertices.add(d);
            }
        }
    }
}