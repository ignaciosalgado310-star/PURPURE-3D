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
import java.util.Iterator;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

@Mod.EventBusSubscriber(modid = PurpureMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class PurpureServerEvents {
    private static final Map<UUID, Ritual> ACTIVE = new HashMap<>();

    // V5: el impacto llega mucho antes para seguir el ritmo de la nueva animacion.
    private static final int IMPACT = 190;
    private static final int INTERVAL = 2;

    private PurpureServerEvents() {}

    @SubscribeEvent
    public static void commands(RegisterCommandsEvent event) {
        event.getDispatcher().register(
                Commands.literal("purpure")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.argument("target", EntityArgument.player())
                                .executes(context -> start(EntityArgument.getPlayer(context, "target"), 25))
                                .then(Commands.argument("hits", IntegerArgumentType.integer(1, 300))
                                        .executes(context -> start(
                                                EntityArgument.getPlayer(context, "target"),
                                                IntegerArgumentType.getInteger(context, "hits")
                                        ))))
        );
    }

    private static int start(ServerPlayer player, int hits) {
        Ritual existing = ACTIVE.get(player.getUUID());
        if (existing != null) {
            existing.hits = Math.min(2000, existing.hits + hits);
            send(existing, PurpureEffectPacket.EXTEND, hits);
            player.sendSystemMessage(Component.literal("§5§lHOLLOW PURPLE §7• §d+" + hits + " golpes"));
            return 1;
        }

        Ritual ritual = new Ritual(player, hits);
        ACTIVE.put(player.getUUID(), ritual);
        send(ritual, PurpureEffectPacket.START, hits);

        ritual.level.playSound(
                null,
                player.blockPosition(),
                PurpureMod.HOLLOW_PURPLE_VIDEO.get(),
                SoundSource.MASTER,
                4.0f,
                1.0f
        );

        player.sendSystemMessage(Component.literal("§5§lHOLLOW PURPLE §7• §dRitual iniciado"));
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
                send(ritual, PurpureEffectPacket.STOP, 0);
                iterator.remove();
                continue;
            }

            ritual.tick(player);
            if (ritual.done()) {
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
        final ArrayDeque<BlockPos> crater = new ArrayDeque<>();

        int t;
        int hits;
        int completedHits;

        Ritual(ServerPlayer player, int hits) {
            this.id = player.getUUID();
            this.level = player.serverLevel();
            this.x = player.getX();
            this.y = player.getY();
            this.z = player.getZ();
            this.hits = hits;
        }

        int end() {
            // ~16.5 s minimo: suficiente para la parte hablada/activa del audio del video,
            // sin dejar al jugador congelado durante el silencio final del clip.
            return Math.max(330, IMPACT + hits * INTERVAL + 90);
        }

        void tick(ServerPlayer player) {
            t++;

            if (t < end()) {
                player.teleportTo(level, x, y, z, player.getYRot(), player.getXRot());
                player.setDeltaMovement(0.0, 0.0, 0.0);
                player.fallDistance = 0.0f;
                player.hurtMarked = true;
            }

            if (t == IMPACT) {
                queueCrater(player.blockPosition());
            }

            if (t >= IMPACT && completedHits < hits) {
                int due = Math.min(hits, ((t - IMPACT) / INTERVAL) + 1);
                while (completedHits < due) {
                    hit(player);
                    completedHits++;
                }
            }

            for (int i = 0; i < 120 && !crater.isEmpty(); i++) {
                BlockPos pos = crater.poll();
                if (!level.getBlockState(pos).isAir()
                        && !level.getBlockState(pos).is(Blocks.BEDROCK)
                        && level.getBlockEntity(pos) == null) {
                    level.destroyBlock(pos, false);
                }
            }
        }

        void hit(ServerPlayer player) {
            if (consumeTotem(player)) {
                level.broadcastEntityEvent(player, (byte) 35);
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

        void queueCrater(BlockPos center) {
            int radiusX = 10;
            int radiusY = 5;
            int radiusZ = 10;

            for (int dx = -radiusX; dx <= radiusX; dx++) {
                for (int dy = -radiusY; dy <= radiusY; dy++) {
                    for (int dz = -radiusZ; dz <= radiusZ; dz++) {
                        double normalized =
                                (dx * dx) / 100.0 +
                                (dy * dy) / 25.0 +
                                (dz * dz) / 100.0;
                        if (normalized <= 1.0) crater.add(center.offset(dx, dy, dz));
                    }
                }
            }
        }

        boolean done() {
            return t > end() && crater.isEmpty();
        }
    }
}
