package com.igygaming.gokufx;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModSounds {
    public static final DeferredRegister<SoundEvent> SOUNDS =
            DeferredRegister.create(ForgeRegistries.SOUND_EVENTS, GokuFxMod.MODID);

    public static final RegistryObject<SoundEvent> KAME_INTRO = register("kame_intro");
    public static final RegistryObject<SoundEvent> KAME_SUSTAIN = register("kame_sustain");
    public static final RegistryObject<SoundEvent> KAME_OUTRO = register("kame_outro");
    public static final RegistryObject<SoundEvent> GENKI_CHARGE = register("genki_charge");

    private ModSounds() {}

    private static RegistryObject<SoundEvent> register(String name) {
        ResourceLocation id = new ResourceLocation(GokuFxMod.MODID, name);
        return SOUNDS.register(name, () -> SoundEvent.createVariableRangeEvent(id));
    }
}
