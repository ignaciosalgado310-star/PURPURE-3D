package com.igygaming.gokufx;

import net.minecraft.world.item.Item;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModItems {
    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, GokuFxMod.MODID);

    public static final RegistryObject<Item> KAME_ORB = item("kame_orb");
    public static final RegistryObject<Item> GENKI_ORB = item("genki_orb");
    public static final RegistryObject<Item> KAME_BEAM = item("kame_beam");
    public static final RegistryObject<Item> KAME_BEAM_CORE = item("kame_beam_core");
    public static final RegistryObject<Item> KAME_RING = item("kame_ring");
    public static final RegistryObject<Item> GENKI_RING = item("genki_ring");

    private ModItems() {}

    private static RegistryObject<Item> item(String name) {
        return ITEMS.register(name, () -> new Item(new Item.Properties().stacksTo(1)));
    }
}
