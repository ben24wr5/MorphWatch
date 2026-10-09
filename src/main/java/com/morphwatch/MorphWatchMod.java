package com.morphwatch;

import com.morphwatch.network.ModNetwork;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.inventory.MenuType;
import net.minecraftforge.common.extensions.IForgeMenuType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
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
    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, MODID);
    public static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(ForgeRegistries.MENU_TYPES, MODID);
    public static final RegistryObject<MenuType<WatchWorkbenchMenu>> WORKBENCH_MENU = MENUS.register("watch_workbench",
            () -> IForgeMenuType.create((id, inventory, data) -> new WatchWorkbenchMenu(id, inventory)));

    public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MODID);

    public static final RegistryObject<Item> MORPH_WATCH = ITEMS.register("morph_watch",
            () -> new MorphWatchItem(new Item.Properties().stacksTo(1).rarity(Rarity.RARE)));

    public static final RegistryObject<Item> RECHARGE_CRYSTAL = ITEMS.register("recharge_crystal",
            () -> new RechargeCrystalItem(new Item.Properties().stacksTo(16).rarity(Rarity.UNCOMMON)));
    public static final RegistryObject<Block> WATCH_WORKBENCH = BLOCKS.register("watch_workbench",
            () -> new WatchWorkbenchBlock(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_GREEN)
                    .strength(2.5F).sound(SoundType.WOOL)));
    public static final RegistryObject<Item> WATCH_WORKBENCH_ITEM = ITEMS.register("watch_workbench",
            () -> new BlockItem(WATCH_WORKBENCH.get(), new Item.Properties()));

    /** Creative mode: the Morph Watch tab, with everything to craft and upgrade the watch. */
    public static final RegistryObject<CreativeModeTab> TAB = TABS.register("morph_watch", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.morphwatch"))
            .icon(() -> new ItemStack(MORPH_WATCH.get()))
            .displayItems((params, out) -> {
                out.accept(MORPH_WATCH.get());
                out.accept(RECHARGE_CRYSTAL.get());
                out.accept(WATCH_WORKBENCH_ITEM.get());
                // To craft it
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
        BLOCKS.register(modBus);
        MENUS.register(modBus);
        ITEMS.register(modBus);
        TABS.register(modBus);
        modBus.addListener(this::commonSetup);
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(ModNetwork::register);
    }
}
