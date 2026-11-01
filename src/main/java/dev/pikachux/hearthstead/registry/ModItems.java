package dev.pikachux.hearthstead.registry;

import dev.pikachux.hearthstead.Hearthstead;
import dev.pikachux.hearthstead.colony.BuildingType;
import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import dev.pikachux.hearthstead.item.BuildToolItem;
import dev.pikachux.hearthstead.item.ClipboardItem;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

public final class ModItems {
    public static final ResourceKey<Item> BUILD_TOOL_KEY = ResourceKey.create(Registries.ITEM, Hearthstead.id("build_tool"));
    public static final Item BUILD_TOOL = Registry.register(BuiltInRegistries.ITEM, BUILD_TOOL_KEY,
            new BuildToolItem(new Item.Properties().setId(BUILD_TOOL_KEY).stacksTo(1)));
    public static final ResourceKey<Item> CLIPBOARD_KEY = ResourceKey.create(Registries.ITEM, Hearthstead.id("clipboard"));
    public static final Item CLIPBOARD = Registry.register(BuiltInRegistries.ITEM, CLIPBOARD_KEY,
            new ClipboardItem(new Item.Properties().setId(CLIPBOARD_KEY).stacksTo(1)));

    private ModItems() {
    }

    public static void register() {
        CreativeModeTab tab = FabricItemGroup.builder()
                .title(Component.translatable("itemGroup.hearthstead"))
                .icon(() -> new ItemStack(ModBlocks.HUTS.get(BuildingType.TOWN_HALL)))
                .displayItems((params, output) -> {
                    output.accept(BUILD_TOOL);
                    output.accept(CLIPBOARD);
                    output.accept(ModBlocks.ROPE_FENCE);
                    output.accept(ModBlocks.SCARECROW);
                    ModBlocks.HUTS.values().forEach(output::accept);
                })
                .build();
        Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, Hearthstead.id("general"), tab);
    }
}
