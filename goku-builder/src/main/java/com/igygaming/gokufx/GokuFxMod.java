package com.igygaming.gokufx;

import com.igygaming.gokufx.network.GokuNetwork;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

@Mod(GokuFxMod.MODID)
public final class GokuFxMod {
    public static final String MODID = "gokufx";

    public GokuFxMod() {
        ModItems.ITEMS.register(FMLJavaModLoadingContext.get().getModEventBus());
        ModSounds.SOUNDS.register(FMLJavaModLoadingContext.get().getModEventBus());
        GokuNetwork.register();
        MinecraftForge.EVENT_BUS.register(this);
    }

    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        GokuCommands.register(event.getDispatcher());
    }

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            AttackManager.tick();
            GenkiManager.tick();
            // Se ejecuta al final para anular cualquier empuje o arrastre de los ataques.
            MovementLockManager.tick();
        }
    }
}
