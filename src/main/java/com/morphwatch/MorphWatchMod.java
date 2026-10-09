package com.morphwatch;

import com.morphwatch.network.ModNetwork;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Rarity;
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

    public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MODID);

    public static final RegistryObject<Item> MORPH_WATCH = ITEMS.register("morph_watch",
            () -> new MorphWatchItem(new Item.Properties().stacksTo(1).rarity(Rarity.RARE)));

    /** Creative mode: the Morph Watch tab, with everything to craft and upgrade the watch. */
    public static final RegistryObject<CreativeModeTab> TAB = TABS.register("morph_watch", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.morphwatch"))
            .icon(() -> new ItemStack(MORPH_WATCH.get()))
            .displayItems((params, out) -> {
                out.accept(MORPH_WATCH.get());
                out.accept(MorphWatchItem.withUpgrades(MorphData.ALL_UPGRADES));
                // To craft it
                out.accept(Items.CRAFTING_TABLE);
                out.accept(Items.GOLD_BLOCK);
                out.accept(Items.RED_WOOL);
                out.accept(Items.ENDER_PEARL);
                // To upgrade it (hold one and left-click while wearing the watch)
                out.accept(Items.GOLD_INGOT);
                out.accept(Items.DIAMOND);
                out.accept(Items.EMERALD);
            })
            .build());

    public MorphWatchMod() {
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
        ITEMS.register(modBus);
        TABS.register(modBus);
        modBus.addListener(this::commonSetup);
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(ModNetwork::register);
    }
}
