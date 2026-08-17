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

    private static final int IMPACT = 190;
    private static final int INTERVAL = 2;

    // Cabeza custom de Gojo. Solo se usa como textura del PLAYER_HEAD del NPC.
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
        stack.getOrCreateTagElement("display").putInt("color", 0x080A12);
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

            gojo = new ArmorStand(level, x + 3.45, y, z);
            gojo.setNoGravity(true);
            gojo.setInvulnerable(true);
            gojo.setSilent(true);
            gojo.setShowArms(true);
            gojo.setNoBasePlate(true);
            gojo.setCustomName(Component.literal("§f§lGojo Satoru"));
            gojo.setCustomNameVisible(false);
            gojo.setYRot(90.0f);

            gojo.setItemSlot(EquipmentSlot.HEAD, createGojoHead());
            gojo.setItemSlot(EquipmentSlot.CHEST, blackLeather(Items.LEATHER_CHESTPLATE));
            gojo.setItemSlot(EquipmentSlot.LEGS, blackLeather(Items.LEATHER_LEGGINGS));
            gojo.setItemSlot(EquipmentSlot.FEET, blackLeather(Items.LEATHER_BOOTS));

            gojo.setHeadPose(new Rotations(-4.0f, 0.0f, 0.0f));
            gojo.setRightArmPose(new Rotations(3.0f, 0.0f, 8.0f));
            gojo.setLeftArmPose(new Rotations(-3.0f, 0.0f, -8.0f));
            level.addFreshEntity(gojo);
        }

        int end() {
            return Math.max(330, IMPACT + hits * INTERVAL + 90);
        }

        void tick(ServerPlayer player) {
            t++;

            if (gojo == null || !gojo.isAlive()) {
                spawnGojo();
            }
            animateGojo();

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

        void animateGojo() {
            if (gojo == null || !gojo.isAlive()) return;

            // Lo mantenemos siempre en el mismo sitio para que sea una aparicion cinematografica.
            gojo.teleportTo(x + 3.45, y, z);
            gojo.setYRot(90.0f + (float)Math.sin(t * 0.045) * 2.0f);

            float rightX;
            float rightY;
            float rightZ;
            float leftX;
            float leftY;
            float leftZ;

            if (t < 55) {
                float q = smooth(5.0f, 55.0f, t);
                rightX = lerp(q, 3.0f, -52.0f);
                rightY = lerp(q, 0.0f, -18.0f);
                rightZ = lerp(q, 8.0f, -28.0f);
                leftX = lerp(q, -3.0f, -48.0f);
                leftY = lerp(q, 0.0f, 18.0f);
                leftZ = lerp(q, -8.0f, 28.0f);
            } else if (t < 125) {
                float q = smooth(55.0f, 115.0f, t);
                rightX = lerp(q, -52.0f, -78.0f);
                rightY = lerp(q, -18.0f, -34.0f);
                rightZ = lerp(q, -28.0f, -17.0f);
                leftX = lerp(q, -48.0f, -78.0f);
                leftY = lerp(q, 18.0f, 34.0f);
                leftZ = lerp(q, 28.0f, 17.0f);
            } else if (t < 185) {
                float q = smooth(125.0f, 175.0f, t);
                rightX = lerp(q, -78.0f, -96.0f);
                rightY = lerp(q, -34.0f, -5.0f);
                rightZ = lerp(q, -17.0f, -4.0f);
                leftX = lerp(q, -78.0f, -96.0f);
                leftY = lerp(q, 34.0f, 5.0f);
                leftZ = lerp(q, 17.0f, 4.0f);
            } else {
                float q = smooth(185.0f, 235.0f, t);
                // Lanzamiento: brazo derecho al frente y el izquierdo baja.
                rightX = lerp(q, -96.0f, -88.0f);
                rightY = lerp(q, -5.0f, 0.0f);
                rightZ = lerp(q, -4.0f, 0.0f);
                leftX = lerp(q, -96.0f, -25.0f);
                leftY = lerp(q, 5.0f, 10.0f);
                leftZ = lerp(q, 4.0f, 12.0f);
            }

            float breathe = (float)Math.sin(t * 0.12) * 2.0f;
            gojo.setRightArmPose(new Rotations(rightX + breathe, rightY, rightZ));
            gojo.setLeftArmPose(new Rotations(leftX - breathe, leftY, leftZ));
            gojo.setHeadPose(new Rotations(-4.0f + (float)Math.sin(t * 0.055) * 2.0f, 0.0f, 0.0f));
        }

        void cleanup() {
            if (gojo != null) {
                gojo.discard();
                gojo = null;
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
