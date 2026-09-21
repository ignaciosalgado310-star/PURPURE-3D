package com.igy.zoro3d.server;

import com.igy.zoro3d.network.ZoroNetwork;
import com.igy.zoro3d.network.ZoroSyncPacket;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayDeque;
import java.util.UUID;

public final class ZoroAttack {
    public static final int STAGE_INTRO = 0;
    public static final int STAGE_RING = 1;
    public static final int STAGE_SLASH = 2;

    public static final int INTRO_END = 50;
    public static final int RING_END = 105;
    private static final int TOTEMS_PER_TICK = 2;
    private static final int CRATER_RADIUS = 12;
    private static final int CRATER_DEPTH = 6;
    private static final int CRATER_BUDGET = 900;

    private final UUID attackId = UUID.randomUUID();
    private final ServerPlayer target;
    private final ArrayDeque<BlockPos> craterQueue = new ArrayDeque<>();

    private int requestedTotems;
    private int usedTotems;
    private int age;
    private int finishedTotemTick = -1;
    private boolean depleted;
    private boolean finished;
    private boolean craterQueued;

    public ZoroAttack(ServerPlayer target, int requestedTotems) {
        this.target = target;
        this.requestedTotems = Math.max(1, requestedTotems);
    }

    public void begin() {
        ServerLevel level = target.serverLevel();
        level.playSound(null, target.blockPosition(), SoundEvents.BEACON_AMBIENT,
                SoundSource.MASTER, 2.5F, 0.45F);
        level.playSound(null, target.blockPosition(), SoundEvents.TRIDENT_RIPTIDE_3,
                SoundSource.MASTER, 2.2F, 0.60F);
        sync(true);
    }

    public void addTotems(int amount) {
        if (finished || amount <= 0) return;
        long next = (long) requestedTotems + amount;
        requestedTotems = (int) Math.min(Integer.MAX_VALUE, next);
        if (!depleted && usedTotems < requestedTotems) finishedTotemTick = -1;
        sync(true);
    }

    public boolean tick() {
        if (finished) return false;
        if (!target.isAlive() || target.isRemoved() || target.connection == null) {
            finish(false);
            return false;
        }

        ServerLevel level = target.serverLevel();

        if (age == 28) {
            level.playSound(null, target.blockPosition(), SoundEvents.ENDER_DRAGON_GROWL,
                    SoundSource.MASTER, 1.8F, 0.72F);
        }
        if (age == INTRO_END) {
            level.playSound(null, target.blockPosition(), SoundEvents.CONDUIT_ACTIVATE,
                    SoundSource.MASTER, 2.8F, 0.55F);
            level.playSound(null, target.blockPosition(), SoundEvents.WARDEN_SONIC_BOOM,
                    SoundSource.MASTER, 2.4F, 1.35F);
        }
        if (age == RING_END) {
            level.playSound(null, target.blockPosition(), SoundEvents.GENERIC_EXPLODE,
                    SoundSource.MASTER, 3.5F, 0.62F);
            level.playSound(null, target.blockPosition(), SoundEvents.WARDEN_SONIC_BOOM,
                    SoundSource.MASTER, 3.5F, 0.88F);
            queueCrater();
        }

        if (age >= RING_END) tickSlash();
        processCraterQueue();

        if (age % 2 == 0 || age == INTRO_END || age == RING_END) sync(false);
        age++;
        return !finished;
    }

    private void tickSlash() {
        ServerLevel level = target.serverLevel();
        int slashAge = age - RING_END;

        if (slashAge % 12 == 0) {
            level.playSound(null, target.blockPosition(), SoundEvents.PLAYER_ATTACK_SWEEP,
                    SoundSource.MASTER, 2.5F, 0.55F + (slashAge % 48) * 0.006F);
        }
        if (slashAge % 36 == 0) {
            level.playSound(null, target.blockPosition(), SoundEvents.TRIDENT_THUNDER,
                    SoundSource.MASTER, 1.8F, 1.45F);
        }

        if (!depleted && usedTotems < requestedTotems && slashAge >= 8) {
            for (int i = 0; i < TOTEMS_PER_TICK && usedTotems < requestedTotems; i++) {
                if (consumeTotem(target)) {
                    usedTotems++;
                    level.broadcastEntityEvent(target, (byte) 35);
                } else {
                    depleted = true;
                    target.hurt(target.damageSources().magic(), 1000.0F);
                    finishedTotemTick = age;
                    break;
                }
            }
            if (usedTotems > 0 && usedTotems % 20 == 0) {
                level.playSound(null, target.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME,
                        SoundSource.MASTER, 2.0F, 0.55F + (usedTotems % 80) * 0.004F);
            }
            if (usedTotems >= requestedTotems) finishedTotemTick = age;
        }

        if (!target.isAlive()) {
            finish(false);
            return;
        }

        if (finishedTotemTick >= 0 && age - finishedTotemTick >= 55 && craterQueue.isEmpty()) {
            finish(false);
        }
    }

    private boolean consumeTotem(ServerPlayer player) {
        ItemStack offhand = player.getOffhandItem();
        if (!offhand.isEmpty() && offhand.is(Items.TOTEM_OF_UNDYING)) {
            offhand.shrink(1);
            return true;
        }

        Inventory inv = player.getInventory();
        for (int slot = 0; slot < inv.getContainerSize(); slot++) {
            ItemStack stack = inv.getItem(slot);
            if (stack.isEmpty() || !stack.is(Items.TOTEM_OF_UNDYING)) continue;
            stack.shrink(1);
            inv.setChanged();
            return true;
        }
        return false;
    }

    private int stage() {
        if (age < INTRO_END) return STAGE_INTRO;
        if (age < RING_END) return STAGE_RING;
        return STAGE_SLASH;
    }

    private Vec3 center() {
        return target.position().add(0.0, 1.0, 0.0);
    }

    private void sync(boolean force) {
        if (finished && !force) return;
        Vec3 c = center();
        ZoroNetwork.sendToAll(new ZoroSyncPacket(
                attackId,
                target.getUUID(),
                target.serverLevel().dimension().location().toString(),
                ZoroSyncPacket.OP_UPDATE,
                stage(),
                age,
                c.x, c.y, c.z,
                usedTotems,
                requestedTotems
        ));
    }

    public void finish(boolean cancelled) {
        if (finished) return;
        finished = true;
        Vec3 c = center();
        ZoroNetwork.sendToAll(new ZoroSyncPacket(
                attackId,
                target.getUUID(),
                target.serverLevel().dimension().location().toString(),
                ZoroSyncPacket.OP_END,
                stage(),
                age,
                c.x, c.y, c.z,
                usedTotems,
                requestedTotems
        ));
        if (!cancelled && target.isAlive()) {
            target.serverLevel().playSound(null, target.blockPosition(), SoundEvents.BEACON_DEACTIVATE,
                    SoundSource.MASTER, 2.3F, 0.62F);
        }
    }

    private void queueCrater() {
        if (craterQueued) return;
        craterQueued = true;
        BlockPos origin = target.blockPosition();
        double radiusSq = CRATER_RADIUS * (double) CRATER_RADIUS;

        for (int dx = -CRATER_RADIUS; dx <= CRATER_RADIUS; dx++) {
            for (int dz = -CRATER_RADIUS; dz <= CRATER_RADIUS; dz++) {
                double hSq = dx * (double) dx + dz * (double) dz;
                if (hSq > radiusSq) continue;
                double n = Math.sqrt(hSq) / CRATER_RADIUS;
                double bowl = Math.pow(Math.max(0.0, 1.0 - n), 0.64);
                int depth = Math.max(1, (int) Math.round(CRATER_DEPTH * bowl));
                int top = 2 + (int) Math.round((1.0 - n) * 2.0);
                for (int dy = top; dy >= -depth; dy--) {
                    craterQueue.add(origin.offset(dx, dy, dz));
                }
            }
        }
    }

    private void processCraterQueue() {
        if (craterQueue.isEmpty()) return;
        ServerLevel level = target.serverLevel();
        for (int i = 0; i < CRATER_BUDGET && !craterQueue.isEmpty(); i++) {
            BlockPos pos = craterQueue.pollFirst();
            if (pos == null) break;
            BlockState state = level.getBlockState(pos);
            if (state.isAir() || state.is(Blocks.BEDROCK)) continue;
            if (level.getBlockEntity(pos) != null) continue;
            level.destroyBlock(pos, false, target);
        }
    }
}
