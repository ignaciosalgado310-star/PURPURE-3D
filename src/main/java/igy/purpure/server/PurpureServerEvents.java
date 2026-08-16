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
import net.minecraft.sounds.SoundEvents;
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
import java.util.*;

@Mod.EventBusSubscriber(modid=PurpureMod.MODID, bus=Mod.EventBusSubscriber.Bus.FORGE)
public final class PurpureServerEvents {
    private static final Map<UUID,Ritual> ACTIVE=new HashMap<>();
    private static final int IMPACT=180, INTERVAL=2;
    private PurpureServerEvents() {}

    @SubscribeEvent
    public static void commands(RegisterCommandsEvent e) {
        e.getDispatcher().register(Commands.literal("purpure").requires(s->s.hasPermission(2))
                .then(Commands.argument("target",EntityArgument.player())
                        .executes(c->start(EntityArgument.getPlayer(c,"target"),25))
                        .then(Commands.argument("hits",IntegerArgumentType.integer(1,300))
                                .executes(c->start(EntityArgument.getPlayer(c,"target"),IntegerArgumentType.getInteger(c,"hits"))))));
    }

    private static int start(ServerPlayer p,int hits) {
        Ritual r=ACTIVE.get(p.getUUID());
        if(r!=null){ r.hits=Math.min(2000,r.hits+hits); send(r,PurpureEffectPacket.EXTEND,hits); return 1; }
        r=new Ritual(p,hits); ACTIVE.put(p.getUUID(),r); send(r,PurpureEffectPacket.START,hits);
        r.level.playSound(null,p.blockPosition(),SoundEvents.BEACON_ACTIVATE,SoundSource.MASTER,2.5f,0.55f);
        p.sendSystemMessage(Component.literal("§5§lHOLLOW PURPLE §7• §dRitual iniciado")); return 1;
    }

    private static void send(Ritual r,byte mode,int hits){
        ModNetwork.CHANNEL.send(PacketDistributor.DIMENSION.with(() -> r.level.dimension()),
                new PurpureEffectPacket(mode,r.id,r.level.dimension().location().toString(),r.x,r.y,r.z,hits,r.seed));
    }

    @SubscribeEvent
    public static void tick(TickEvent.ServerTickEvent e){
        if(e.phase!=TickEvent.Phase.END||ACTIVE.isEmpty())return;
        Iterator<Ritual> it=ACTIVE.values().iterator();
        while(it.hasNext()){
            Ritual r=it.next(); ServerPlayer p=r.level.getServer().getPlayerList().getPlayer(r.id);
            if(p==null||!p.isAlive()||p.serverLevel()!=r.level){send(r,PurpureEffectPacket.STOP,0);it.remove();continue;}
            r.tick(p); if(r.done()){send(r,PurpureEffectPacket.STOP,0);it.remove();}
        }
    }

    private static final class Ritual {
        final UUID id; final ServerLevel level; final double x,y,z; final long seed=new Random().nextLong();
        final ArrayDeque<BlockPos> crater=new ArrayDeque<>(); int t=0,hits,done=0;
        Ritual(ServerPlayer p,int hits){id=p.getUUID();level=p.serverLevel();x=p.getX();y=p.getY();z=p.getZ();this.hits=hits;}
        int end(){return IMPACT+hits*INTERVAL+40;}
        void tick(ServerPlayer p){
            t++; if(t<end()){p.teleportTo(level,x,y,z,p.getYRot(),p.getXRot());p.setDeltaMovement(0,0,0);p.fallDistance=0;}
            if(t==80) level.playSound(null,p.blockPosition(),SoundEvents.RESPAWN_ANCHOR_CHARGE,SoundSource.MASTER,3.0f,0.65f);
            if(t==130) level.playSound(null,p.blockPosition(),SoundEvents.END_PORTAL_SPAWN,SoundSource.MASTER,3.5f,1.45f);
            if(t==IMPACT){level.playSound(null,p.blockPosition(),SoundEvents.GENERIC_EXPLODE,SoundSource.MASTER,5f,0.45f);queue(p.blockPosition());}
            if(t>=IMPACT&&done<hits){int due=Math.min(hits,((t-IMPACT)/INTERVAL)+1);while(done<due){hit(p);done++;}}
            for(int i=0;i<80&&!crater.isEmpty();i++){BlockPos b=crater.poll(); if(!level.getBlockState(b).isAir()&&!level.getBlockState(b).is(Blocks.BEDROCK)&&level.getBlockEntity(b)==null)level.destroyBlock(b,false);}
        }
        void hit(ServerPlayer p){if(totem(p))level.broadcastEntityEvent(p,(byte)35);else p.hurt(p.damageSources().magic(),1000f);}
        boolean totem(ServerPlayer p){ItemStack o=p.getOffhandItem();if(o.is(Items.TOTEM_OF_UNDYING)){o.shrink(1);return true;} Inventory inv=p.getInventory();for(int i=0;i<inv.getContainerSize();i++){ItemStack s=inv.getItem(i);if(s.is(Items.TOTEM_OF_UNDYING)){s.shrink(1);return true;}}return false;}
        void queue(BlockPos c){int R=7;for(int dx=-R;dx<=R;dx++)for(int dy=-4;dy<=4;dy++)for(int dz=-R;dz<=R;dz++){double q=dx*dx/49.0+dy*dy/16.0+dz*dz/49.0;if(q<=1)crater.add(c.offset(dx,dy,dz));}}
        boolean done(){return t>end()&&crater.isEmpty();}
    }
}
