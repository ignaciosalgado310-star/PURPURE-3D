package igy.purpure.server;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import igy.purpure.PurpureMod;
import igy.purpure.network.ModNetwork;
import igy.purpure.network.PurpureEffectPacket;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Rotations;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
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

    private static final int IMPACT = 340;
    private static final int INTERVAL = 2;
    private static final double GOJO_OFFSET_X = 4.0;

    // Cabeza de Gojo para que el NPC siempre sea visible sin depender de un renderer cliente.
    private static final String GOJO_HEAD_TEXTURE =
            "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNDcxY2JkNjZjNzBhYmEyMDU0NzI3ZTc0YmJjODg4NzcxYmFhNzgwZDdmMmJmMTE0MzNlYzY4YjZiZjUxNmZkMiJ9fX0=";

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
        ritual.spawnGojo();
        send(ritual, PurpureEffectPacket.START, hits);

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

    private static ItemStack createGojoHead() {
        ItemStack head = new ItemStack(Items.PLAYER_HEAD);
        CompoundTag owner = new CompoundTag();
        CompoundTag properties = new CompoundTag();
        ListTag textures = new ListTag();
        CompoundTag texture = new CompoundTag();
        texture.putString("Value", GOJO_HEAD_TEXTURE);
        textures.add(texture);
        properties.put("textures", textures);
        owner.put("Properties", properties);
        head.getOrCreateTag().put("SkullOwner", owner);
        return head;
    }

    private static ItemStack blackLeather(Item item) {
        ItemStack stack = new ItemStack(item);
        stack.getOrCreateTagElement("display").putInt("color", 0x050711);
        return stack;
    }

    private static float smooth(float start, float end, float value) {
        float x = Math.max(0.0f, Math.min(1.0f, (value - start) / (end - start)));
        return x * x * (3.0f - 2.0f * x);
    }

    private static float lerp(float q, float a, float b) {
        return a + (b - a) * q;
    }

    private static final class Ritual {
        final UUID id;
        final ServerLevel level;
        final double x;
        final double y;
        final double z;
        final long seed = new Random().nextLong();
        final ArrayDeque<BlockPos> crater = new ArrayDeque<>();

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
            this.hits = hits;
        }

        void spawnGojo() {
            if (gojo != null && gojo.isAlive()) return;

            gojo = new ArmorStand(level, x + GOJO_OFFSET_X, y, z);
            gojo.setNoGravity(true);
            gojo.setInvulnerable(true);
            gojo.setSilent(true);
            gojo.setInvisible(false);
            gojo.setShowArms(true);
            gojo.setNoBasePlate(true);
            gojo.setCustomName(Component.literal("§f§lGojo Satoru"));
            gojo.setCustomNameVisible(false);

            // +X desde el jugador; yaw 90 mira hacia -X, o sea hacia el objetivo.
            gojo.setYRot(90.0f);
            gojo.setYHeadRot(90.0f);

            gojo.setItemSlot(EquipmentSlot.HEAD, createGojoHead());
            gojo.setItemSlot(EquipmentSlot.CHEST, blackLeather(Items.LEATHER_CHESTPLATE));
            gojo.setItemSlot(EquipmentSlot.LEGS, blackLeather(Items.LEATHER_LEGGINGS));
            gojo.setItemSlot(EquipmentSlot.FEET, blackLeather(Items.LEATHER_BOOTS));

            gojo.setHeadPose(new Rotations(-5.0f, 0.0f, 0.0f));
            gojo.setBodyPose(new Rotations(0.0f, 0.0f, 0.0f));
            gojo.setRightArmPose(new Rotations(5.0f, 0.0f, 8.0f));
            gojo.setLeftArmPose(new Rotations(-4.0f, 0.0f, -8.0f));
            gojo.setRightLegPose(new Rotations(1.5f, 0.0f, 1.5f));
            gojo.setLeftLegPose(new Rotations(-1.5f, 0.0f, -1.5f));

            level.addFreshEntity(gojo);
        }

        int end() {
            return Math.max(390, IMPACT + hits * INTERVAL + 25);
        }

        void tick(ServerPlayer player) {
            t++;

            if (gojo == null || !gojo.isAlive()) spawnGojo();
            animateGojo();

            if (t < end()) {
                // El jugador queda quieto mirando DIRECTAMENTE a Gojo.
                double gx = x + GOJO_OFFSET_X;
                double gy = y + 1.55;
                double gz = z;
                double eyeY = y + 1.62;
                double dx = gx - x;
                double dz = gz - z;
                double dy = gy - eyeY;
                double horizontal = Math.sqrt(dx * dx + dz * dz);
                float yaw = (float)Math.toDegrees(Math.atan2(-dx, dz));
                float pitch = (float)-Math.toDegrees(Math.atan2(dy, horizontal));

                player.teleportTo(level, x, y, z, yaw, pitch);
                player.setYHeadRot(yaw);
                player.setYBodyRot(yaw);
                player.setDeltaMovement(0.0, 0.0, 0.0);
                player.fallDistance = 0.0f;
                player.hurtMarked = true;
            }

            if (t == IMPACT) queueCrater(player.blockPosition());

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

        void animateGojo() {
            if (gojo == null || !gojo.isAlive()) return;

            gojo.teleportTo(x + GOJO_OFFSET_X, y, z);
            float sway = (float)Math.sin(t * 0.045) * 2.2f;
            gojo.setYRot(90.0f + sway);
            gojo.setYHeadRot(90.0f + sway * 0.55f);

            float rightX, rightY, rightZ;
            float leftX, leftY, leftZ;

            if (t < 82) {
                float q = smooth(8.0f, 76.0f, t);
                rightX = lerp(q, 5.0f, -48.0f);
                rightY = lerp(q, 0.0f, -20.0f);
                rightZ = lerp(q, 8.0f, -25.0f);
                leftX = lerp(q, -4.0f, -44.0f);
                leftY = lerp(q, 0.0f, 20.0f);
                leftZ = lerp(q, -8.0f, 25.0f);
            } else if (t < 220) {
                float q = smooth(82.0f, 205.0f, t);
                rightX = lerp(q, -48.0f, -74.0f);
                rightY = lerp(q, -20.0f, -36.0f);
                rightZ = lerp(q, -25.0f, -18.0f);
                leftX = lerp(q, -44.0f, -74.0f);
                leftY = lerp(q, 20.0f, 36.0f);
                leftZ = lerp(q, 25.0f, 18.0f);
            } else if (t < 275) {
                // Fusion: manos juntas poco a poco.
                float q = smooth(220.0f, 265.0f, t);
                rightX = lerp(q, -74.0f, -98.0f);
                rightY = lerp(q, -36.0f, -6.0f);
                rightZ = lerp(q, -18.0f, -4.0f);
                leftX = lerp(q, -74.0f, -98.0f);
                leftY = lerp(q, 36.0f, 6.0f);
                leftZ = lerp(q, 18.0f, 4.0f);
            } else {
                // Lanzamiento: brazo derecho apunta al jugador y el izquierdo baja.
                float q = smooth(275.0f, 330.0f, t);
                rightX = lerp(q, -98.0f, -88.0f);
                rightY = lerp(q, -6.0f, 0.0f);
                rightZ = lerp(q, -4.0f, 0.0f);
                leftX = lerp(q, -98.0f, -24.0f);
                leftY = lerp(q, 6.0f, 10.0f);
                leftZ = lerp(q, 4.0f, 12.0f);
            }

            float breathe = (float)Math.sin(t * 0.11) * 2.0f;
            float body = (float)Math.sin(t * 0.052) * 2.2f;
            gojo.setRightArmPose(new Rotations(rightX + breathe, rightY, rightZ));
            gojo.setLeftArmPose(new Rotations(leftX - breathe, leftY, leftZ));
            gojo.setHeadPose(new Rotations(-5.0f + (float)Math.sin(t * 0.055) * 2.2f, -body * 0.4f, 0.0f));
            gojo.setBodyPose(new Rotations(-1.5f + body * 0.18f, body, 0.0f));
            gojo.setRightLegPose(new Rotations(2.0f + breathe * 0.20f, 0.0f, 1.8f));
            gojo.setLeftLegPose(new Rotations(-2.0f - breathe * 0.18f, 0.0f, -1.8f));
        }

        void cleanup() {
            if (gojo != null) {
                gojo.discard();
                gojo = null;
            }
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
