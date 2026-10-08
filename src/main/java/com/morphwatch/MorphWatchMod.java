package com.morphwatch;

import com.morphwatch.network.ModNetwork;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

@Mod(MorphWatchMod.MODID)
public class MorphWatchMod {
    public static final String MODID = "morphwatch";

    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, MODID);

    public static final RegistryObject<Item> MORPH_WATCH = ITEMS.register("morph_watch",
            () -> new MorphWatchItem(1, new Item.Properties().stacksTo(1).rarity(Rarity.RARE)));
    public static final RegistryObject<Item> MORPH_WATCH_DIAMOND = ITEMS.register("morph_watch_diamond",
            () -> new MorphWatchItem(2, new Item.Properties().stacksTo(1).rarity(Rarity.EPIC)));
    public static final RegistryObject<Item> MORPH_WATCH_NETHERITE = ITEMS.register("morph_watch_netherite",
            () -> new MorphWatchItem(3, new Item.Properties().stacksTo(1).rarity(Rarity.EPIC).fireResistant()));

    public MorphWatchMod() {
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
        ITEMS.register(modBus);
        modBus.addListener(this::commonSetup);
        modBus.addListener(this::addToCreativeTab);
    }

    public static Item watchForTier(int tier) {
        return switch (tier) {
            case 2 -> MORPH_WATCH_DIAMOND.get();
            case 3 -> MORPH_WATCH_NETHERITE.get();
            default -> MORPH_WATCH.get();
        };
    }

    public static String tierName(int tier) {
        return switch (tier) { case 2 -> "Diamond"; case 3 -> "Netherite"; default -> "Gold"; };
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(ModNetwork::register);
    }

    private void addToCreativeTab(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.TOOLS_AND_UTILITIES) {
            event.accept(MORPH_WATCH);
            event.accept(MORPH_WATCH_DIAMOND);
            event.accept(MORPH_WATCH_NETHERITE);
        }
    }
}
