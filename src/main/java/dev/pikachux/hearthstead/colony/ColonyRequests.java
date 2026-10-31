package dev.pikachux.hearthstead.colony;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.Container;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The colony's open requests: what each hut is waiting for, and who can supply it.
 * Builds on {@link Logistics#requests}; players can hand items in through the hut window.
 */
public final class ColonyRequests {
    /** Who the request is waiting on. */
    public enum Status {
        /** The warehouse has it and a deliveryman will bring it. */
        DELIVERING,
        /** The warehouse has it, but the colony has no deliveryman to bring it. */
        NEEDS_DELIVERYMAN,
        /** Nothing in the colony can supply it: a player has to. */
        NEEDS_PLAYER
    }

    public record Entry(Logistics.Request request, BuildingData building, Item icon, Status status) {
    }

    /** Icons for requests that match many items (seeds, food, ...), by description. */
    private static final Map<String, Item> ICONS = new ConcurrentHashMap<>();

    private ColonyRequests() {
    }

    /** Open requests of one building, or of the whole colony when {@code only} is null. */
    public static List<Entry> list(ServerLevel level, Colony colony, @Nullable BuildingData only) {
        List<Container> warehouse = warehouse(level, colony);
        boolean deliveryman = colony.citizens().stream().anyMatch(c -> c.job() == JobType.DELIVERYMAN);
        List<Entry> entries = new ArrayList<>();
        for (BuildingData building : colony.buildings()) {
            if (only != null && only != building) {
                continue;
            }
            for (Logistics.Request request : Logistics.requests(level, colony, building)) {
                Status status = Storage.count(warehouse, request.matcher()) == 0 ? Status.NEEDS_PLAYER
                        : deliveryman ? Status.DELIVERING : Status.NEEDS_DELIVERYMAN;
                entries.add(new Entry(request, building, icon(request), status));
            }
        }
        // Requests only a player can fulfil come first.
        entries.sort((a, b) -> b.status().compareTo(a.status()));
        return entries;
    }

    private static List<Container> warehouse(ServerLevel level, Colony colony) {
        List<Container> containers = new ArrayList<>();
        for (BuildingData building : colony.buildings()) {
            if (building.type() == BuildingType.WAREHOUSE) {
                containers.addAll(Storage.containers(level, building));
            }
        }
        return containers;
    }

    /** An item to show for the request: the first item it accepts. */
    public static Item icon(Logistics.Request request) {
        return ICONS.computeIfAbsent(request.description(), description -> {
            for (Item item : BuiltInRegistries.ITEM) {
                if (item != Items.AIR && request.matcher().test(new ItemStack(item))) {
                    return item;
                }
            }
            return Items.BARRIER;
        });
    }

    /**
     * Moves items for the request of {@code target} described as {@code description} from the
     * player's inventory into that building. Returns how many items were handed in.
     */
    public static int fulfill(ServerLevel level, Colony colony, Player player, BlockPos target, String description) {
        BuildingData building = colony.building(target);
        if (building == null) {
            return 0;
        }
        Logistics.Request request = Logistics.requests(level, colony, building).stream()
                .filter(r -> r.description().equals(description))
                .findFirst()
                .orElse(null);
        if (request == null) {
            return 0;
        }
        List<Container> storage = Storage.containers(level, building);
        int remaining = request.count();
        int moved = 0;
        var items = player.getInventory().getNonEquipmentItems();
        for (int slot = 0; slot < items.size() && remaining > 0; slot++) {
            ItemStack stack = items.get(slot);
            if (stack.isEmpty() || !request.matcher().test(stack)) {
                continue;
            }
            int offered = Math.min(remaining, stack.getCount());
            // insert() shrinks the stack it is given, so hand it a copy and count what is left.
            ItemStack left = Storage.insert(storage, stack.copyWithCount(offered));
            int inserted = offered - left.getCount();
            if (inserted == 0) {
                break;
            }
            stack.shrink(inserted);
            remaining -= inserted;
            moved += inserted;
        }
        if (moved > 0) {
            player.getInventory().setChanged();
            colony.markDirty();
        }
        return moved;
    }
}
