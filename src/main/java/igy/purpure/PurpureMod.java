package igy.purpure;

import igy.purpure.network.ModNetwork;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

@Mod(PurpureMod.MODID)
public final class PurpureMod {
    public static final String MODID = "purpure";

    public static final DeferredRegister<SoundEvent> SOUND_EVENTS =
            DeferredRegister.create(ForgeRegistries.SOUND_EVENTS, MODID);

    public static final RegistryObject<SoundEvent> HOLLOW_PURPLE_VIDEO =
            SOUND_EVENTS.register(
                    "hollow_purple_video",
                    () -> SoundEvent.createVariableRangeEvent(new ResourceLocation(MODID, "hollow_purple_video"))
            );

    public PurpureMod() {
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
        SOUND_EVENTS.register(modBus);
        ModNetwork.register();
    }
}
