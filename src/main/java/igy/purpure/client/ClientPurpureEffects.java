package igy.purpure.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import com.mojang.math.Axis;
import igy.purpure.PurpureMod;
import igy.purpure.network.PurpureEffectPacket;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;
import java.util.*;

@Mod.EventBusSubscriber(modid=PurpureMod.MODID,value=Dist.CLIENT,bus=Mod.EventBusSubscriber.Bus.FORGE)
public final class ClientPurpureEffects {
    private static final Map<UUID,FX> FX=new LinkedHashMap<>();
    private static final float PI2=(float)(Math.PI*2);
    private static final int FUSION_START=180;
    private static final int IMPACT=260;
    private ClientPurpureEffects(){}

    public static void accept(PurpureEffectPacket p){
        Minecraft m=Minecraft.getInstance(); if(m.level==null||!m.level.dimension().location().toString().equals(p.dimension()))return;
        if(p.mode()==PurpureEffectPacket.START)FX.put(p.target(),new FX(p));
        else if(p.mode()==PurpureEffectPacket.EXTEND){FX f=FX.get(p.target());if(f!=null)f.hits=Math.min(2000,f.hits+p.hits());}
        else FX.remove(p.target());
    }

    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent e){
        if(e.phase==TickEvent.Phase.END&&Minecraft.getInstance().level!=null&&!Minecraft.getInstance().isPaused())FX.values().forEach(f->f.t++);
    }

    @SubscribeEvent public static void shake(ViewportEvent.ComputeCameraAngles e){
        Minecraft m=Minecraft.getInstance();if(m.player==null)return;
        for(FX f:FX.values()){
            float t=f.t+(float)e.getPartialTick();
            double d=m.player.distanceToSqr(f.x,f.y,f.z);
            if(d<3600&&t>245){
                float k=(float)Math.max(0,1-Math.sqrt(d)/60.0)*(t>IMPACT?1f:.30f);
                e.setYaw(e.getYaw()+Mth.sin(t*1.55f)*.85f*k);
                e.setPitch(e.getPitch()+Mth.cos(t*1.95f)*.68f*k);
                e.setRoll(e.getRoll()+Mth.sin(t*1.2f)*1.15f*k);
            }
        }
    }

    @SubscribeEvent public static void render(RenderLevelStageEvent e){
        if(e.getStage()!=RenderLevelStageEvent.Stage.AFTER_PARTICLES||FX.isEmpty())return;
        Camera c=e.getCamera();PoseStack p=e.getPoseStack();float pt=e.getPartialTick();
        RenderSystem.enableBlend();
        RenderSystem.blendFuncSeparate(com.mojang.blaze3d.platform.GlStateManager.SourceFactor.SRC_ALPHA,com.mojang.blaze3d.platform.GlStateManager.DestFactor.ONE,com.mojang.blaze3d.platform.GlStateManager.SourceFactor.ONE,com.mojang.blaze3d.platform.GlStateManager.DestFactor.ONE);
        RenderSystem.enableDepthTest();RenderSystem.depthMask(false);RenderSystem.disableCull();RenderSystem.setShader(GameRenderer::getPositionColorShader);
        for(FX f:FX.values()){
            p.pushPose();p.translate(f.x-c.getPosition().x,f.y-c.getPosition().y,f.z-c.getPosition().z);drawFX(p,f,f.t+pt);p.popPose();
        }
        RenderSystem.enableCull();RenderSystem.depthMask(true);RenderSystem.defaultBlendFunc();RenderSystem.disableBlend();
    }

    private static void drawFX(PoseStack p,FX f,float t){
        // Azul + Rojo: grandes, separados y con movimiento lento para que el ritual dure más.
        if(t<235){
            float conv=smooth(125,228,t);
            float r=Mth.lerp(conv,9.2f,.25f);
            float a=t*.040f;
            float y=Mth.lerp(smooth(0,85,t),-1f,6.0f);
            float size=Mth.lerp(smooth(0,55,t),1.2f,3.25f);
            orb(p,Mth.cos(a)*r,y,Mth.sin(a)*r,size,0.05f,.30f,1f,t,-1);
            orb(p,Mth.cos(a+Mth.PI)*r,y,Mth.sin(a+Mth.PI)*r,size,1f,.045f,.03f,t,1);
        }

        // Fusión: aparece antes del impacto y crece progresivamente.
        if(t>=FUSION_START){
            float q=smooth(FUSION_START,IMPACT-15,t);
            float imp=smooth(IMPACT-18,IMPACT+15,t);
            float y=Mth.lerp(imp,6.0f,2.2f);
            float base=t<IMPACT?Mth.lerp(q,.35f,8.4f):10.8f*(1+.055f*Mth.sin(t*.32f));

            p.pushPose();p.translate(0,y,0);
            sphere(p,base*.28f,.98f,.96f,1f,.60f);
            sphere(p,base*.50f,.95f,.58f,1f,.38f);
            sphere(p,base*.72f,.70f,.12f,1f,.26f);
            sphere(p,base,.20f,.02f,.75f,.16f);
            rings(p,base*1.30f,t,.78f,.18f,1f);
            if(t>IMPACT-8)column(p,base,t);
            p.popPose();

            if(t>IMPACT){
                p.pushPose();p.translate(0,.15,0);
                for(int i=0;i<7;i++){
                    float z=(t-IMPACT-i*12)/54f;
                    if(z>=0&&z<=1)ring(p,Mth.lerp(z,3.5f,31f),.38f+z*.95f,.60f,.12f,1f,(1-z)*.34f);
                }
                p.popPose();
            }
        }
    }

    private static void orb(PoseStack p,float x,float y,float z,float R,float r,float g,float b,float t,int dir){
        p.pushPose();p.translate(x,y,z);
        float pulse=R*(1+.07f*Mth.sin(t*.24f));
        sphere(p,pulse*.34f,.98f,.99f,1f,.52f);
        sphere(p,pulse*.62f,r,g,b,.36f);
        sphere(p,pulse*.84f,r*.82f,g*.82f,b,.22f);
        sphere(p,pulse,r*.58f,g*.58f,b,.12f);
        rings(p,pulse*1.72f,t*dir,r,g,b);
        p.popPose();
    }

    private static void rings(PoseStack p,float R,float t,float r,float g,float b){
        for(int i=0;i<7;i++){
            p.pushPose();
            p.mulPose(Axis.XP.rotationDegrees(18+i*22));
            p.mulPose(Axis.ZP.rotationDegrees(t*(.55f+i*.09f)+i*37));
            ring(p,R*(.72f+i*.075f),R*.032f+.05f,r,g,b,.26f-i*.022f);
            p.popPose();
        }
    }

    private static void ring(PoseStack p,float R,float w,float r,float g,float b,float a){
        Matrix4f m=p.last().pose();BufferBuilder v=Tesselator.getInstance().getBuilder();v.begin(VertexFormat.Mode.QUADS,DefaultVertexFormat.POSITION_COLOR);
        int n=96;
        for(int i=0;i<n;i++){
            float a0=i*PI2/n,a1=(i+1)*PI2/n,ri=R-w,ro=R+w;
            V(v,m,Mth.cos(a0)*ri,0,Mth.sin(a0)*ri,r,g,b,a);
            V(v,m,Mth.cos(a0)*ro,0,Mth.sin(a0)*ro,r,g,b,a);
            V(v,m,Mth.cos(a1)*ro,0,Mth.sin(a1)*ro,r,g,b,a);
            V(v,m,Mth.cos(a1)*ri,0,Mth.sin(a1)*ri,r,g,b,a);
        }
        BufferUploader.drawWithShader(v.end());
    }

    private static void sphere(PoseStack p,float R,float r,float g,float b,float a){
        Matrix4f m=p.last().pose();BufferBuilder v=Tesselator.getInstance().getBuilder();v.begin(VertexFormat.Mode.QUADS,DefaultVertexFormat.POSITION_COLOR);
        int lon=40,lat=22;
        for(int iy=0;iy<lat;iy++){
            float p0=((float)iy/lat-.5f)*Mth.PI,p1=((float)(iy+1)/lat-.5f)*Mth.PI;
            for(int ix=0;ix<lon;ix++){
                float t0=ix*PI2/lon,t1=(ix+1)*PI2/lon;
                SV(v,m,R,p0,t0,r,g,b,a);SV(v,m,R,p0,t1,r,g,b,a);SV(v,m,R,p1,t1,r,g,b,a);SV(v,m,R,p1,t0,r,g,b,a);
            }
        }
        BufferUploader.drawWithShader(v.end());
    }

    private static void SV(BufferBuilder v,Matrix4f m,float R,float p,float t,float r,float g,float b,float a){
        V(v,m,R*Mth.cos(p)*Mth.cos(t),R*Mth.sin(p),R*Mth.cos(p)*Mth.sin(t),r,g,b,a);
    }

    private static void column(PoseStack p,float R,float t){
        for(int i=0;i<9;i++){
            p.pushPose();p.mulPose(Axis.YP.rotationDegrees(i*40f+t*(i%2==0?.36f:-.31f)));
            Matrix4f m=p.last().pose();BufferBuilder v=Tesselator.getInstance().getBuilder();v.begin(VertexFormat.Mode.QUADS,DefaultVertexFormat.POSITION_COLOR);
            float w=Math.max(3.0f,R*.45f),h=72;
            V(v,m,-w,-h/2,0,.45f,.08f,1,.035f);
            V(v,m,w,-h/2,0,.45f,.08f,1,.035f);
            V(v,m,w,h/2,0,.78f,.35f,1,.13f);
            V(v,m,-w,h/2,0,.78f,.35f,1,.13f);
            BufferUploader.drawWithShader(v.end());p.popPose();
        }
    }

    private static void V(BufferBuilder v,Matrix4f m,float x,float y,float z,float r,float g,float b,float a){v.vertex(m,x,y,z).color(C(r),C(g),C(b),C(a)).endVertex();}
    private static int C(float x){return Mth.clamp((int)(x*255),0,255);}
    private static float smooth(float a,float b,float x){float q=Mth.clamp((x-a)/(b-a),0,1);return q*q*(3-2*q);}

    private static final class FX{
        final double x,y,z;final long seed;int hits,t;
        FX(PurpureEffectPacket p){x=p.x();y=p.y();z=p.z();seed=p.seed();hits=p.hits();}
    }
}
