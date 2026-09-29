package com.igygaming.gokufx;

import com.igygaming.gokufx.network.GokuEffectPacket;
import com.igygaming.gokufx.network.GokuNetwork;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

public final class AttackManager {
    public static final int DEFAULT_KAME_TOTEMS = 25;
    public static final int DEFAULT_GENKI_TOTEMS = 100;

    private static final List<Attack> ACTIVE = new ArrayList<>();

    private AttackManager() {}

    public static void startKamehameha(ServerPlayer player, int totems) {
        int hits = Math.max(1, Math.min(1000, totems));
        ACTIVE.add(new KamehamehaAttack(player, hits));
        GokuNetwork.CHANNEL.send(
                PacketDistributor.DIMENSION.with(() -> player.serverLevel().dimension()),
                new GokuEffectPacket(GokuEffectPacket.KAMEHAMEHA, player.getUUID(), hits)
        );
    }

    public static void startGenkiDama(ServerPlayer player, int totems) {
        int hits = Math.max(1, Math.min(1000, totems));
        ACTIVE.add(new GenkiDamaAttack(player, hits));
        GokuNetwork.CHANNEL.send(
                PacketDistributor.DIMENSION.with(() -> player.serverLevel().dimension()),
                new GokuEffectPacket(GokuEffectPacket.GENKI_DAMA, player.getUUID(), hits)
        );
    }

    public static void tick() {
        Iterator<Attack> iterator = ACTIVE.iterator();
        while (iterator.hasNext()) {
            Attack attack = iterator.next();
            try {
                if (attack.tick()) {
                    attack.cleanup();
                    iterator.remove();
                }
            } catch (Throwable t) {
                attack.cleanup();
                iterator.remove();
                t.printStackTrace();
            }
        }
    }

    private interface Attack {
        boolean tick();
        void cleanup();
    }

    private abstract static class PlayerAttack implements Attack {
        protected final ServerLevel level;
        protected final UUID targetId;
        protected final int requestedHits;
        protected int completedHits = 0;
        protected int age = 0;

        protected PlayerAttack(ServerPlayer target, int requestedHits) {
            this.level = target.serverLevel();
            this.targetId = target.getUUID();
            this.requestedHits = requestedHits;
        }

        protected ServerPlayer target() {
            return level.getServer().getPlayerList().getPlayer(targetId);
        }

        protected void consumeOneTotemOrKill(ServerPlayer player) {
            if (player == null || !player.isAlive()) return;

            if (consumeTotem(player)) {
                // Mantiene visible la animación vanilla del tótem.
                level.broadcastEntityEvent(player, (byte) 35);
            } else {
                player.invulnerableTime = 0;
                player.hurt(level.damageSources().magic(), 1000.0F);
            }
        }

        protected boolean consumeTotem(ServerPlayer player) {
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

        protected void applyRequestedHits(ServerPlayer player, int startTick, int endTick) {
            if (age < startTick || age > endTick || completedHits >= requestedHits) return;

            int totalTicks = Math.max(1, endTick - startTick + 1);
            int elapsed = age - startTick + 1;
            int due = Math.min(requestedHits,
                    (int) Math.ceil((elapsed / (double) totalTicks) * requestedHits));

            while (completedHits < due && player.isAlive()) {
                consumeOneTotemOrKill(player);
                completedHits++;
            }
        }

        protected void damageNearby(Vec3 center, double radius, float amount, ServerPlayer mainTarget) {
            AABB box = new AABB(center, center).inflate(radius);
            for (LivingEntity living : level.getEntitiesOfClass(LivingEntity.class, box, LivingEntity::isAlive)) {
                if (living == mainTarget) continue;
                if (living.position().distanceToSqr(center) <= radius * radius) {
                    living.invulnerableTime = 0;
                    living.hurt(level.damageSources().magic(), amount);
                }
            }
        }

        protected void launchAlong(ServerPlayer player, Vec3 direction, double speed, double yBoost) {
            if (player == null || !player.isAlive()) return;
            Vec3 dir = direction.lengthSqr() < 0.0001D ? new Vec3(1.0D, 0.0D, 0.0D) : direction.normalize();
            player.setDeltaMovement(dir.scale(speed).add(0.0D, yBoost, 0.0D));
            player.hurtMarked = true;
            player.fallDistance = 0.0F;
        }

        protected void breakBeamPath(Vec3 start, Vec3 direction, double length, double radius) {
            Vec3 dir = direction.normalize();
            for (double t = 0.0D; t <= length; t += 0.90D) {
                breakSphere(start.add(dir.scale(t)), radius);
            }
        }

        protected void breakSphere(Vec3 center, double radius) {
            breakSphereBudgeted(center, radius, Integer.MAX_VALUE);
        }

        protected int breakSphereBudgeted(Vec3 center, double radius, int budget) {
            if (budget <= 0) return 0;

            int r = (int) Math.ceil(radius);
            int cx = (int) Math.floor(center.x);
            int cy = (int) Math.floor(center.y);
            int cz = (int) Math.floor(center.z);
            double r2 = radius * radius;

            for (int x = -r; x <= r && budget > 0; x++) {
                for (int y = -r; y <= r && budget > 0; y++) {
                    for (int z = -r; z <= r && budget > 0; z++) {
                        if ((x * x) + (y * y) + (z * z) > r2) continue;
                        BlockPos pos = new BlockPos(cx + x, cy + y, cz + z);
                        if (destroyIfAllowed(pos)) budget--;
                    }
                }
            }
            return budget;
        }

        protected void createGenkiHole(Vec3 center, double craterRadius, double shaftRadius) {
            breakSphere(center, craterRadius);
            breakSphere(center.add(0.0D, -2.0D, 0.0D), craterRadius - 1.5D);
            breakSphere(center.add(0.0D, -5.0D, 0.0D), craterRadius - 3.0D);

            int minY = level.getMinBuildHeight() + 1;
            for (int y = (int) Math.floor(center.y); y >= minY; y--) {
                double shrink = Math.max(2.6D, shaftRadius - ((center.y - y) * 0.015D));
                breakSphere(new Vec3(center.x, y, center.z), shrink);
            }
        }

        private boolean destroyIfAllowed(BlockPos pos) {
            if (pos.getY() <= level.getMinBuildHeight()) return false;
            if (!level.hasChunkAt(pos)) return false;

            BlockState state = level.getBlockState(pos);
            if (state.isAir()) return false;
            if (state.is(Blocks.BEDROCK)) return false;
            if (state.getDestroySpeed(level, pos) < 0.0F) return false;

            return level.destroyBlock(pos, false);
        }

        @Override
        public void cleanup() {
            // Los visuales ahora se dibujan del lado cliente como mallas 3D reales.
        }
    }

    private static final class KamehamehaAttack extends PlayerAttack {
        private static final double DRAG_SPEED = 0.82D;
        private static final int BLOCK_BUDGET_PER_TICK = 240;

        private final Vec3 beamDirection;
        private Vec3 dragOrigin;
        private double dragProgress;

        private KamehamehaAttack(ServerPlayer target, int hits) {
            super(target, hits);
            this.beamDirection = new Vec3(1.0D, -0.04D, 0.0D).normalize();
        }

        @Override
        public boolean tick() {
            ServerPlayer player = target();
            if (player == null || !player.isAlive()) return true;
            age++;

            Vec3 playerCenter = player.position().add(0.0D, 1.15D, 0.0D);
            Vec3 source = playerCenter.add(beamDirection.scale(-14.0D));
            int beamEnd = KameTiming.totalTicks(requestedHits);

            // El audio personalizado lleva el protagonismo; estos sonidos vanilla quedan como capa sutil.
            if (age == 1) {
                level.playSound(null, source.x, source.y, source.z,
                        SoundEvents.WARDEN_SONIC_CHARGE, SoundSource.HOSTILE, 0.65F, 1.45F);
            }

            if (age <= KameTiming.CHARGE_TICKS && age % 32 == 0) {
                float p = age / (float) KameTiming.CHARGE_TICKS;
                level.playSound(null, source.x, source.y, source.z,
                        SoundEvents.RESPAWN_ANCHOR_CHARGE, SoundSource.HOSTILE, 0.45F,
                        0.72F + p * 0.45F);
            }

            if (age == KameTiming.BEAM_START_TICK) {
                dragOrigin = player.position();
                dragProgress = 0.0D;
                level.playSound(null, player.getX(), player.getY() + 1.0D, player.getZ(),
                        SoundEvents.WARDEN_SONIC_BOOM, SoundSource.HOSTILE, 2.5F, 0.58F);
            }

            if (age >= KameTiming.FRONT_CONTACT_TICK && age <= beamEnd) {
                if (dragOrigin == null) dragOrigin = player.position();

                // La punta/frente visible es la que abre el corredor y arrastra al jugador.
                Vec3 impact = player.position().add(0.0D, 1.0D, 0.0D);
                clearKameTunnel(impact, 6.4D, 3.25D);
                applyControlledDrag(player);

                // Los tótems se reparten por TODA la duración real del rayo.
                applyRequestedHits(player, KameTiming.hitStartTick(), KameTiming.hitEndTick(requestedHits));

                if (age <= KameTiming.hitEndTick(requestedHits) && age % 4 == 0) {
                    damageNearby(impact.add(beamDirection.scale(1.5D)), 4.2D, 24.0F, player);
                }

                if (age % 18 == 0) {
                    level.playSound(null, impact.x, impact.y, impact.z,
                            SoundEvents.BEACON_AMBIENT, SoundSource.HOSTILE, 0.35F, 1.75F);
                }
            }

            return age > beamEnd + 3;
        }

        private void applyControlledDrag(ServerPlayer player) {
            dragProgress += DRAG_SPEED;
            Vec3 desired = dragOrigin.add(beamDirection.scale(dragProgress));
            Vec3 error = desired.subtract(player.position());

            Vec3 correction = error.scale(0.30D);
            if (correction.lengthSqr() > 0.36D) {
                correction = correction.normalize().scale(0.60D);
            }

            Vec3 velocity = beamDirection.scale(0.76D).add(correction);
            if (velocity.lengthSqr() > 1.5625D) {
                velocity = velocity.normalize().scale(1.25D);
            }

            // Mantiene un empuje continuo en vez de un único lanzamiento de física vanilla.
            player.setDeltaMovement(player.getDeltaMovement().lerp(velocity, 0.78D));
            player.hurtMarked = true;
            player.fallDistance = 0.0F;
        }

        private void clearKameTunnel(Vec3 center, double ahead, double baseRadius) {
            int budget = BLOCK_BUDGET_PER_TICK;

            // Variación leve del radio: túnel energético irregular, no un /fill air perfecto.
            for (double d = 0.0D; d <= ahead && budget > 0; d += 1.55D) {
                double wave = 0.93D + 0.09D * Math.sin(age * 0.31D + d * 1.73D);
                Vec3 cutCenter = center.add(beamDirection.scale(d));
                budget = breakSphereBudgeted(cutCenter, baseRadius * wave, budget);
            }
        }

        @Override
        public void cleanup() {
            GokuNetwork.CHANNEL.send(
                    PacketDistributor.DIMENSION.with(level::dimension),
                    new GokuEffectPacket(GokuEffectPacket.KAMEHAMEHA_STOP, targetId, 0)
            );
        }
    }

    private static final class GenkiDamaAttack extends PlayerAttack {
        private Vec3 center;

        private GenkiDamaAttack(ServerPlayer target, int hits) {
            super(target, hits);
            this.center = target.position().add(0.0D, 15.0D, 0.0D);
        }

        @Override
        public boolean tick() {
            ServerPlayer player = target();
            if (player == null || !player.isAlive()) return true;
            age++;

            if (age == 1) {
                center = player.position().add(0.0D, 15.0D, 0.0D);
                level.playSound(null, center.x, center.y, center.z,
                        SoundEvents.BEACON_ACTIVATE, SoundSource.HOSTILE, 3.4F, 0.60F);
            }

            if (age <= 120) {
                center = player.position().add(0.0D, 15.0D, 0.0D);
                if (age % 14 == 0) {
                    float p = age / 120.0F;
                    level.playSound(null, center.x, center.y, center.z,
                            SoundEvents.RESPAWN_ANCHOR_CHARGE, SoundSource.HOSTILE, 2.0F,
                            0.48F + p * 0.75F);
                }
            }

            if (age == 121) {
                level.playSound(null, center.x, center.y, center.z,
                        SoundEvents.WITHER_SPAWN, SoundSource.HOSTILE, 4.0F, 0.50F);
            }

            if (age >= 121 && age <= 160) {
                float p = (age - 121) / 39.0F;
                float fall = p * p;
                Vec3 start = player.position().add(0.0D, 15.0D, 0.0D);
                Vec3 end = player.position().add(0.0D, 1.2D, 0.0D);
                center = start.lerp(end, fall);
            }

            if (age == 161) {
                Vec3 impact = player.position().add(0.0D, 1.0D, 0.0D);
                level.playSound(null, impact.x, impact.y, impact.z,
                        SoundEvents.GENERIC_EXPLODE, SoundSource.HOSTILE, 4.0F, 0.45F);
                level.playSound(null, impact.x, impact.y, impact.z,
                        SoundEvents.WARDEN_SONIC_BOOM, SoundSource.HOSTILE, 4.0F, 0.35F);

                // Genki Dama: impacto mucho más destructivo que antes.
                createGenkiHole(impact, 14.5D, 6.6D);
            }

            if (age >= 161 && age <= 216) {
                Vec3 impact = player.position().add(0.0D, 1.0D, 0.0D);

                // Consume EXACTAMENTE la cantidad indicada en el comando.
                applyRequestedHits(player, 161, 206);

                if (age <= 206 && age % 2 == 0) {
                    damageNearby(impact, 11.0D, 48.0F, player);
                }
            }

            return age > 222;
        }
    }
}
