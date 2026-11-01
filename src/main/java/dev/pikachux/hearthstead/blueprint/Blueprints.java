package dev.pikachux.hearthstead.blueprint;

import dev.pikachux.hearthstead.Hearthstead;
import dev.pikachux.hearthstead.block.HutBlock;
import dev.pikachux.hearthstead.colony.BuildingData;
import dev.pikachux.hearthstead.colony.BuildingType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Loads hut blueprints from vanilla structure templates.
 *
 * <p>Blueprints live at {@code hearthstead:huts/<hut>/level<N>}, i.e.
 * {@code data/hearthstead/structure/huts/<hut>/level<N>.nbt}. They can be replaced
 * with a data pack, or by saving a structure block under that name in a world.
 * A template must contain the matching hut block, which marks where the hut sits.
 *
 * <p>Decorations live at {@code hearthstead:decorations/<name>} (classic) and
 * {@code hearthstead:styles/<style>/decorations/<name>}. They have no hut block: their anchor is
 * the middle of the template one block above its bottom layer, and their front faces north.
 */
public final class Blueprints {
    private static final Map<Identifier, Optional<Blueprint>> CACHE = new HashMap<>();
    private static final String DECORATION_FOLDER = "structure/decorations/";
    @Nullable
    private static List<String> decorationCache;
    @Nullable
    private static Map<String, Item> iconCache;

    private Blueprints() {
    }

    public static void clearCache() {
        CACHE.clear();
        decorationCache = null;
        iconCache = null;
        Styles.clearCache();
    }

    public static Identifier id(BuildingData building, int level) {
        return id(building.type(), building.style(), level);
    }

    public static Identifier id(BuildingType type, String style, int level) {
        String hut = type.blockName().substring("blockhut".length());
        if (Styles.CLASSIC.equals(style)) {
            return Hearthstead.id("huts/" + hut + "/level" + level);
        }
        return Hearthstead.id("styles/" + style + "/" + hut + "/level" + level);
    }

    /** The building's blueprint in its style, falling back to the classic style if missing. */
    @Nullable
    public static Blueprint get(MinecraftServer server, BuildingData building, int level) {
        Blueprint blueprint = load(server, id(building, level));
        if (blueprint == null && !Styles.CLASSIC.equals(building.style())) {
            blueprint = load(server, id(building.type(), Styles.CLASSIC, level));
        }
        return blueprint;
    }

    public static Identifier decorationId(String style, String name) {
        if (Styles.CLASSIC.equals(style)) {
            return Hearthstead.id("decorations/" + name);
        }
        return Hearthstead.id("styles/" + style + "/decorations/" + name);
    }

    /** A decoration's blueprint in the given style, falling back to the classic style if missing. */
    @Nullable
    public static Blueprint decoration(MinecraftServer server, String style, String name) {
        if (!isDecorationName(name)) {
            return null;
        }
        Blueprint blueprint = load(server, decorationId(style, name));
        if (blueprint == null && !Styles.CLASSIC.equals(style)) {
            blueprint = load(server, decorationId(Styles.CLASSIC, name));
        }
        return blueprint;
    }

    private static boolean isDecorationName(String name) {
        return !name.isEmpty() && name.chars().allMatch(c -> c >= 'a' && c <= 'z' || c >= '0' && c <= '9' || c == '_');
    }

    /** Names of every decoration with a classic blueprint in the loaded data packs, sorted. */
    public static List<String> decorations(MinecraftServer server) {
        if (decorationCache == null) {
            decorationCache = server.getResourceManager()
                    .listResources("structure/decorations", id -> id.getPath().endsWith(".nbt"))
                    .keySet().stream()
                    .filter(id -> id.getNamespace().equals(Hearthstead.MOD_ID))
                    .map(id -> id.getPath().substring(DECORATION_FOLDER.length(), id.getPath().length() - ".nbt".length()))
                    .filter(Blueprints::isDecorationName)
                    .sorted()
                    .toList();
        }
        return decorationCache;
    }

    /**
     * The item shown for a decoration in the build tool, from {@code data/hearthstead/decoration_icons.json}
     * (a data pack may add its own), or picked from the blueprint when it is not listed there.
     */
    public static Item decorationIcon(MinecraftServer server, String name, Blueprint blueprint) {
        if (iconCache == null) {
            Map<String, Item> icons = new HashMap<>();
            server.getResourceManager().getResource(Hearthstead.id("decoration_icons.json")).ifPresent(resource -> {
                try (var reader = resource.openAsReader()) {
                    com.google.gson.JsonObject json = com.google.gson.JsonParser.parseReader(reader).getAsJsonObject();
                    for (var entry : json.entrySet()) {
                        Identifier id = Identifier.tryParse(entry.getValue().getAsString());
                        if (id != null) {
                            net.minecraft.core.registries.BuiltInRegistries.ITEM.getOptional(id)
                                    .ifPresent(item -> icons.put(entry.getKey(), item));
                        }
                    }
                } catch (Exception e) {
                    Hearthstead.LOGGER.warn("Could not read decoration_icons.json", e);
                }
            });
            iconCache = icons;
        }
        Item icon = iconCache.get(name);
        return icon != null && icon != Items.AIR ? icon : decorationIcon(blueprint);
    }

    /**
     * Picks a decoration's icon from its blueprint: its rarest block that has an item,
     * the highest one when several are equally rare (the lantern of a lamp post, the bell of a
     * bell tower).
     */
    public static Item decorationIcon(Blueprint blueprint) {
        Map<Item, Integer> counts = new HashMap<>();
        Map<Item, Integer> heights = new HashMap<>();
        for (Blueprint.Entry entry : blueprint.entries()) {
            Item item = entry.state().getBlock().asItem();
            if (item == Items.AIR || entry.state().isAir()) {
                continue;
            }
            counts.merge(item, 1, Integer::sum);
            heights.merge(item, entry.offset().getY(), Math::max);
        }
        Item best = Items.OAK_PLANKS;
        int bestCount = Integer.MAX_VALUE;
        int bestHeight = Integer.MIN_VALUE;
        for (Map.Entry<Item, Integer> entry : counts.entrySet()) {
            int count = entry.getValue();
            int height = heights.get(entry.getKey());
            if (count < bestCount || count == bestCount && height > bestHeight) {
                best = entry.getKey();
                bestCount = count;
                bestHeight = height;
            }
        }
        return best;
    }

    @Nullable
    private static Blueprint load(MinecraftServer server, Identifier id) {
        return CACHE.computeIfAbsent(id, key -> server.getStructureManager().get(key).map(t -> parse(server, key, t)))
                .orElse(null);
    }

    private static Blueprint parse(MinecraftServer server, Identifier id, StructureTemplate template) {
        CompoundTag tag = template.save(new CompoundTag());
        ListTag paletteTag = tag.getListOrEmpty("palette");
        if (paletteTag.isEmpty()) {
            paletteTag = tag.getListOrEmpty("palettes").getListOrEmpty(0);
        }
        var blocks = server.registryAccess().lookupOrThrow(Registries.BLOCK);
        List<BlockState> palette = new ArrayList<>();
        paletteTag.compoundStream().forEach(t -> palette.add(NbtUtils.readBlockState(blocks, t)));

        List<BlockPos> positions = new ArrayList<>();
        List<BlockState> states = new ArrayList<>();
        BlockPos anchor = null;
        Direction anchorFacing = Direction.NORTH;
        for (CompoundTag entry : tag.getListOrEmpty("blocks").compoundStream().toList()) {
            ListTag posTag = entry.getListOrEmpty("pos");
            BlockPos pos = new BlockPos(posTag.getIntOr(0, 0), posTag.getIntOr(1, 0), posTag.getIntOr(2, 0));
            int index = entry.getIntOr("state", 0);
            BlockState state = index < palette.size() ? palette.get(index) : Block.stateById(0);
            if (state.getBlock() instanceof HutBlock && anchor == null) {
                anchor = pos;
                anchorFacing = state.getValue(HutBlock.FACING);
            }
            positions.add(pos);
            states.add(state);
        }
        if (anchor == null) {
            if (!id.getPath().contains("decorations/")) {
                Hearthstead.LOGGER.warn("Blueprint {} contains no hut block; using its bottom center as the anchor", id);
            }
            var size = template.getSize();
            anchor = new BlockPos(size.getX() / 2, 1, size.getZ() / 2);
        }
        List<Blueprint.Entry> entries = new ArrayList<>(positions.size());
        for (int i = 0; i < positions.size(); i++) {
            entries.add(new Blueprint.Entry(positions.get(i).subtract(anchor), states.get(i)));
        }
        return new Blueprint(entries, anchorFacing);
    }

    /**
     * Area occupied by a building: its current blueprint, or the level 1 blueprint
     * for a building that has not been built yet.
     */
    @Nullable
    public static BoundingBox footprint(MinecraftServer server, net.minecraft.server.level.ServerLevel level, BuildingData building) {
        Blueprint blueprint = get(server, building, Math.max(1, building.level()));
        if (blueprint == null) {
            return null;
        }
        BlockState hut = level.getBlockState(building.pos());
        Direction facing = hut.hasProperty(HutBlock.FACING) ? hut.getValue(HutBlock.FACING) : Direction.NORTH;
        return blueprint.bounds(building.pos(), facing);
    }

    public static Direction facing(net.minecraft.server.level.ServerLevel level, BlockPos hut) {
        BlockState state = level.getBlockState(hut);
        return state.hasProperty(HutBlock.FACING) ? state.getValue(HutBlock.FACING) : Direction.NORTH;
    }
}
