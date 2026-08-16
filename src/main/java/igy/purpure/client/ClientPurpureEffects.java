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
    private ClientPurpureEffects(){}

    public static void accept(PurpureEffectPacket p){
        Minecraft m=Minecraft.getInstance(); if(m.level==null||!m.level.dimension().location().toString().equals(p.dimension()))return;
        if(p.mode()==PurpureEffectPacket.START)FX.put(p.target(),new FX(p));
        else if(p.mode()==PurpureEffectPacket.EXTEND){FX f=FX.get(p.target());if(f!=null)f.hits=Math.min(2000,f.hits+p.hits());}
        else FX.remove(p.target());
    }

    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent e){if(e.phase==TickEvent.Phase.END&&Minecraft.getInstance().level!=null&&!Minecraft.getInstance().isPaused())FX.values().forEach(f->f.t++);}

    @SubscribeEvent public static void shake(ViewportEvent.ComputeCameraAngles e){
        Minecraft m=Minecraft.getInstance();if(m.player==null)return;
        for(FX f:FX.values()){float t=f.t+(float)e.getPartialTick();double d=m.player.distanceToSqr(f.x,f.y,f.z);if(d<1600&&t>125){float k=(float)Math.max(0,1-Math.sqrt(d)/40.0)*(t>175?1f:.25f);e.setYaw(e.getYaw()+Mth.sin(t*1.7f)*.55f*k);e.setPitch(e.getPitch()+Mth.cos(t*2.1f)*.45f*k);e.setRoll(e.getRoll()+Mth.sin(t*1.3f)*.8f*k);}}
    }

    @SubscribeEvent public static void render(RenderLevelStageEvent e){
        if(e.getStage()!=RenderLevelStageEvent.Stage.AFTER_PARTICLES||FX.isEmpty())return;
        Camera c=e.getCamera();PoseStack p=e.getPoseStack();float pt=e.getPartialTick();
        RenderSystem.enableBlend();RenderSystem.blendFuncSeparate(com.mojang.blaze3d.platform.GlStateManager.SourceFactor.SRC_ALPHA,com.mojang.blaze3d.platform.GlStateManager.DestFactor.ONE,com.mojang.blaze3d.platform.GlStateManager.SourceFactor.ONE,com.mojang.blaze3d.platform.GlStateManager.DestFactor.ONE);
        RenderSystem.enableDepthTest();RenderSystem.depthMask(false);RenderSystem.disableCull();RenderSystem.setShader(GameRenderer::getPositionColorShader);
        for(FX f:FX.values()){p.pushPose();p.translate(f.x-c.getPosition().x,f.y-c.getPosition().y,f.z-c.getPosition().z);drawFX(p,f,f.t+pt);p.popPose();}
        RenderSystem.enableCull();RenderSystem.depthMask(true);RenderSystem.defaultBlendFunc();RenderSystem.disableBlend();
    }

    private static void drawFX(PoseStack p,FX f,float t){
        if(t<150){float conv=smooth(90,145,t),r=Mth.lerp(conv,5.2f,.18f),a=t*.065f,y=Mth.lerp(smooth(0,55,t),-1f,4f);orb(p,Mth.cos(a)*r,y,Mth.sin(a)*r,1.8f,0.08f,.35f,1f,t,-1);orb(p,Mth.cos(a+Mth.PI)*r,y,Mth.sin(a+Mth.PI)*r,1.8f,1f,.08f,.05f,t,1);}
        if(t>=122){float q=smooth(122,175,t),imp=smooth(170,195,t),y=Mth.lerp(imp,4f,1.25f),R=t<180?Mth.lerp(q,.3f,4.2f):4.9f*(1+.07f*Mth.sin(t*.5f));p.pushPose();p.translate(0,y,0);sphere(p,R*.34f,.95f,.9f,1f,.5f);sphere(p,R*.62f,.9f,.16f,1f,.28f);sphere(p,R,.2f,.03f,.7f,.15f);rings(p,R*1.25f,t,.75f,.2f,1f);if(t>178)column(p,R,t);p.popPose();if(t>180){p.pushPose();p.translate(0,.1,0);for(int i=0;i<4;i++){float z=(t-180-i*8)/32f;if(z>=0&&z<=1)ring(p,Mth.lerp(z,2f,16f),.25f+z*.6f,.55f,.1f,1f,(1-z)*.28f);}p.popPose();}}
    }

    private static void orb(PoseStack p,float x,float y,float z,float R,float r,float g,float b,float t,int dir){p.pushPose();p.translate(x,y,z);float pulse=R*(1+.08f*Mth.sin(t*.35f));sphere(p,pulse*.38f,.95f,.98f,1f,.45f);sphere(p,pulse*.7f,r,g,b,.28f);sphere(p,pulse,r*.7f,g*.7f,b,.13f);rings(p,pulse*1.55f,t*dir,r,g,b);p.popPose();}
    private static void rings(PoseStack p,float R,float t,float r,float g,float b){for(int i=0;i<5;i++){p.pushPose();p.mulPose(Axis.XP.rotationDegrees(25+i*27));p.mulPose(Axis.ZP.rotationDegrees(t*(.9f+i*.14f)+i*43));ring(p,R*(.78f+i*.09f),R*.035f+.04f,r,g,b,.22f-i*.025f);p.popPose();}}
    private static void ring(PoseStack p,float R,float w,float r,float g,float b,float a){Matrix4f m=p.last().pose();BufferBuilder v=Tesselator.getInstance().getBuilder();v.begin(VertexFormat.Mode.QUADS,DefaultVertexFormat.POSITION_COLOR);int n=80;for(int i=0;i<n;i++){float a0=i*PI2/n,a1=(i+1)*PI2/n,ri=R-w,ro=R+w;V(v,m,Mth.cos(a0)*ri,0,Mth.sin(a0)*ri,r,g,b,a);V(v,m,Mth.cos(a0)*ro,0,Mth.sin(a0)*ro,r,g,b,a);V(v,m,Mth.cos(a1)*ro,0,Mth.sin(a1)*ro,r,g,b,a);V(v,m,Mth.cos(a1)*ri,0,Mth.sin(a1)*ri,r,g,b,a);}BufferUploader.drawWithShader(v.end());}
    private static void sphere(PoseStack p,float R,float r,float g,float b,float a){Matrix4f m=p.last().pose();BufferBuilder v=Tesselator.getInstance().getBuilder();v.begin(VertexFormat.Mode.QUADS,DefaultVertexFormat.POSITION_COLOR);int lon=30,lat=16;for(int iy=0;iy<lat;iy++){float p0=((float)iy/lat-.5f)*Mth.PI,p1=((float)(iy+1)/lat-.5f)*Mth.PI;for(int ix=0;ix<lon;ix++){float t0=ix*PI2/lon,t1=(ix+1)*PI2/lon;SV(v,m,R,p0,t0,r,g,b,a);SV(v,m,R,p0,t1,r,g,b,a);SV(v,m,R,p1,t1,r,g,b,a);SV(v,m,R,p1,t0,r,g,b,a);}}BufferUploader.drawWithShader(v.end());}
    private static void SV(BufferBuilder v,Matrix4f m,float R,float p,float t,float r,float g,float b,float a){V(v,m,R*Mth.cos(p)*Mth.cos(t),R*Mth.sin(p),R*Mth.cos(p)*Mth.sin(t),r,g,b,a);}
    private static void column(PoseStack p,float R,float t){for(int i=0;i<7;i++){p.pushPose();p.mulPose(Axis.YP.rotationDegrees(i*51.4f+t*(i%2==0?.55f:-.45f)));Matrix4f m=p.last().pose();BufferBuilder v=Tesselator.getInstance().getBuilder();v.begin(VertexFormat.Mode.QUADS,DefaultVertexFormat.POSITION_COLOR);float w=Math.max(1.8f,R*.4f),h=42;V(v,m,-w,-h/2,0,.5f,.12f,1,.03f);V(v,m,w,-h/2,0,.5f,.12f,1,.03f);V(v,m,w,h/2,0,.7f,.3f,1,.11f);V(v,m,-w,h/2,0,.7f,.3f,1,.11f);BufferUploader.drawWithShader(v.end());p.popPose();}}
    private static void V(BufferBuilder v,Matrix4f m,float x,float y,float z,float r,float g,float b,float a){v.vertex(m,x,y,z).color(C(r),C(g),C(b),C(a)).endVertex();}
    private static int C(float x){return Mth.clamp((int)(x*255),0,255);}private static float smooth(float a,float b,float x){float q=Mth.clamp((x-a)/(b-a),0,1);return q*q*(3-2*q);}
    private static final class FX{final double x,y,z;final long seed;int hits,t;FX(PurpureEffectPacket p){x=p.x();y=p.y();z=p.z();seed=p.seed();hits=p.hits();}int end(){return 180+hits*2+40;}}
}
