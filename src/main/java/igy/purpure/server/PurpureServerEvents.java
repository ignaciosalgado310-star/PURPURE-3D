package igy.purpure.server;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import igy.purpure.PurpureMod;
import igy.purpure.network.ModNetwork;
import igy.purpure.network.PurpureEffectPacket;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

@Mod.EventBusSubscriber(modid = PurpureMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class PurpureServerEvents {
    private static final Map<UUID, Ritual> ACTIVE = new HashMap<>();

    // V15: el punto fuerte del audio de referencia cae alrededor de 10-11 s.
    private static final int IMPACT = 220;
    private static final int INTERVAL = 2;
    private static final double GOJO_OFFSET_X = 4.0;
    private static final double TRAVEL_SPEED = 0.38;
    private static final int BLOCK_BUDGET_PER_TICK = 520;

    private PurpureServerEvents() {}

    @SubscribeEvent
    public static void commands(RegisterCommandsEvent event) {
        event.getDispatcher().register(
                Commands.literal("purpure")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.argument("target", EntityArgument.player())
                                .executes(context -> start(EntityArgument.getPlayer(context, "target"), 25))
                                .then(Commands.argument("hits", IntegerArgumentType.integer(1))
                                        .executes(context -> start(
                                                EntityArgument.getPlayer(context, "target"),
                                                IntegerArgumentType.getInteger(context, "hits")
                                        ))))
        );
    }

    private static int start(ServerPlayer player, int hits) {
        Ritual existing = ACTIVE.get(player.getUUID());
        if (existing != null) {
            long expanded = (long)existing.hits + hits;
            existing.hits = (int)Math.min(Integer.MAX_VALUE / 4L, expanded);
            send(existing, PurpureEffectPacket.EXTEND, hits);
            player.sendSystemMessage(Component.literal("§5§lHOLLOW PURPLE §7• §d+" + hits + " tótems"));
            return 1;
        }

        Ritual ritual = new Ritual(player, hits);
        ACTIVE.put(player.getUUID(), ritual);
        ritual.spawnGojoAnchor();
        send(ritual, PurpureEffectPacket.START, hits);

        // El OGG de este evento es ahora el audio extraído del video de referencia.
        ritual.level.playSound(
                null,
                player.blockPosition(),
                PurpureMod.HOLLOW_PURPLE_VIDEO.get(),
                SoundSource.MASTER,
                4.0f,
                1.0f
        );

        player.sendSystemMessage(Component.literal("§5§lHOLLOW PURPLE §7• §dGojo ha aparecido"));
        return 1;
    }

    private static void send(Ritual ritual, byte mode, int hits) {
        ModNetwork.CHANNEL.send(
                PacketDistributor.DIMENSION.with(() -> ritual.level.dimension()),
                new PurpureEffectPacket(
                        mode,
                        ritual.id,
                        ritual.level.dimension().location().toString(),
                        ritual.x,
                        ritual.y,
                        ritual.z,
                        hits,
                        ritual.seed
                )
        );
    }

    @SubscribeEvent
    public static void tick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || ACTIVE.isEmpty()) return;

        Iterator<Ritual> iterator = ACTIVE.values().iterator();
        while (iterator.hasNext()) {
            Ritual ritual = iterator.next();
            ServerPlayer player = ritual.level.getServer().getPlayerList().getPlayer(ritual.id);

            if (player == null || !player.isAlive() || player.serverLevel() != ritual.level) {
                ritual.cleanup();
                send(ritual, PurpureEffectPacket.STOP, 0);
                iterator.remove();
                continue;
            }

            ritual.tick(player);
            if (ritual.done()) {
                ritual.cleanup();
                send(ritual, PurpureEffectPacket.STOP, 0);
                iterator.remove();
            }
        }
    }

    private static final class Ritual {
        final UUID id;
        final ServerLevel level;
        final double x;
        final double y;
        final double z;
        final long seed = new Random().nextLong();
        final ArrayDeque<BlockPos> blocksToErase = new ArrayDeque<>();
        final Set<BlockPos> queuedBlocks = new HashSet<>();

        ArmorStand gojo;
        int t;
        int hits;
        int completedHits;

        Ritual(ServerPlayer player, int hits) {
            this.id = player.getUUID();
            this.level = player.serverLevel();
            this.x = player.getX();
            this.y = player.getY();
            this.z = player.getZ();
            this.hits = Math.max(1, hits);
        }

        void spawnGojoAnchor() {
            if (gojo != null && gojo.isAlive()) return;

            gojo = new ArmorStand(level, x + GOJO_OFFSET_X, y, z);
            gojo.setNoGravity(true);
            gojo.setInvulnerable(true);
            gojo.setSilent(true);
            gojo.setInvisible(true);
            gojo.setNoBasePlate(true);
            gojo.setYRot(90.0f);
            gojo.setYHeadRot(90.0f);
            level.addFreshEntity(gojo);
        }

        long endTick() {
            return Math.max(260L, (long)IMPACT + (long)hits * INTERVAL + 30L);
        }

        void tick(ServerPlayer player) {
            t++;
            if (gojo == null || !gojo.isAlive()) spawnGojoAnchor();

            if (t < IMPACT) {
                holdForCinematic(player);
            } else {
                dragWithPurple(player);
            }

            if (t >= IMPACT && completedHits < hits) {
                int due = Math.min(hits, ((t - IMPACT) / INTERVAL) + 1);
                while (completedHits < due) {
                    hit(player);
                    completedHits++;
                }
            }

            eraseQueuedBlocks();
        }

        void holdForCinematic(ServerPlayer player) {
            double gx = x + GOJO_OFFSET_X;
            double gy = y + 1.55;
            double eyeY = y + 1.62;
            double dx = gx - x;
            double dz = z - z;
            double dy = gy - eyeY;
            double horizontal = Math.sqrt(dx * dx + dz * dz);
            float yaw = (float)Math.toDegrees(Math.atan2(-dx, dz));
            float pitch = (float)-Math.toDegrees(Math.atan2(dy, horizontal));

            player.setNoGravity(false);
            player.teleportTo(level, x, y, z, yaw, pitch);
            player.setYHeadRot(yaw);
            player.setYBodyRot(yaw);
            player.setDeltaMovement(0.0, 0.0, 0.0);
            player.fallDistance = 0.0f;
            player.hurtMarked = true;
        }

        void dragWithPurple(ServerPlayer player) {
            int travelTick = Math.max(0, t - IMPACT);
            double desiredX = x - travelTick * TRAVEL_SPEED;
            double desiredY = y + 0.18;
            double desiredZ = z;

            // Abrimos primero el volumen inmediatamente delante del ataque.
            queueTunnel(desiredX - 1.8, desiredY + 1.3, desiredZ);
            eraseQueuedBlocks();

            player.setNoGravity(true);
            player.fallDistance = 0.0f;
            player.setYRot(90.0f);
            player.setYHeadRot(90.0f);
            player.setYBodyRot(90.0f);

            double errX = desiredX - player.getX();
            double errY = desiredY - player.getY();
            double errZ = desiredZ - player.getZ();
            double errorSq = errX * errX + errY * errY + errZ * errZ;

            if (errorSq > 2.25) {
                // Corrección excepcional, no teletransporte cada tick.
                player.teleportTo(level, desiredX, desiredY, desiredZ, 90.0f, 0.0f);
            } else {
                double vy = Math.max(-0.22, Math.min(0.22, errY * 0.45));
                double vz = Math.max(-0.12, Math.min(0.12, errZ * 0.35));
                player.setDeltaMovement(-TRAVEL_SPEED, vy, vz);
            }

            player.hurtMarked = true;
        }

        void queueTunnel(double centerX, double centerY, double centerZ) {
            int rx = 5;
            int ry = 4;
            int rz = 5;
            BlockPos center = BlockPos.containing(centerX, centerY, centerZ);

            for (int dx = -rx; dx <= rx; dx++) {
                for (int dy = -ry; dy <= ry; dy++) {
                    for (int dz = -rz; dz <= rz; dz++) {
                        double n = (dx * dx) / 25.0 + (dy * dy) / 16.0 + (dz * dz) / 25.0;
                        if (n > 1.0) continue;

                        BlockPos pos = center.offset(dx, dy, dz);
                        if (!level.hasChunkAt(pos)) continue;
                        if (queuedBlocks.add(pos)) blocksToErase.add(pos);
                    }
                }
            }
        }

        void eraseQueuedBlocks() {
            int budget = BLOCK_BUDGET_PER_TICK;
            while (budget-- > 0 && !blocksToErase.isEmpty()) {
                BlockPos pos = blocksToErase.poll();
                queuedBlocks.remove(pos);
                if (!level.hasChunkAt(pos)) continue;

                var state = level.getBlockState(pos);
                if (state.isAir() || state.is(Blocks.BEDROCK) || level.getBlockEntity(pos) != null) continue;

                // Hollow Purple borra materia: sin miles de FallingBlock ni drops.
                level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
            }
        }

        void cleanup() {
            if (gojo != null) {
                gojo.discard();
                gojo = null;
            }
            ServerPlayer player = level.getServer().getPlayerList().getPlayer(id);
            if (player != null) {
                player.setNoGravity(false);
                player.setDeltaMovement(0.0, player.getDeltaMovement().y, 0.0);
                player.hurtMarked = true;
            }
            blocksToErase.clear();
            queuedBlocks.clear();
        }

        void hit(ServerPlayer player) {
            if (consumeTotem(player)) {
                level.broadcastEntityEvent(player, (byte)35);
            } else {
                player.hurt(player.damageSources().magic(), 1000.0f);
            }
        }

        boolean consumeTotem(ServerPlayer player) {
            ItemStack offhand = player.getOffhandItem();
            if (offhand.is(Items.TOTEM_OF_UNDYING)) {
                offhand.shrink(1);
                return true;
            }

            Inventory inventory = player.getInventory();
            for (int i = 0; i < inventory.getContainerSize(); i++) {
                ItemStack stack = inventory.getItem(i);
                if (stack.is(Items.TOTEM_OF_UNDYING)) {
                    stack.shrink(1);
                    return true;
                }
            }
            return false;
        }

        boolean done() {
            return completedHits >= hits && t > endTick() && blocksToErase.isEmpty();
        }
    }
}
