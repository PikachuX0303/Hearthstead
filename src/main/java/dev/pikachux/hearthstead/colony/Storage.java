package dev.pikachux.hearthstead.colony;

import dev.pikachux.hearthstead.block.HutBlockEntity;
import dev.pikachux.hearthstead.blueprint.Blueprints;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * Item storage of buildings. A hut stores items in its own block entity; a warehouse
 * additionally uses every chest and barrel inside its building.
 */
public final class Storage {
    private Storage() {
    }

    public static List<Container> containers(ServerLevel level, BuildingData building) {
        List<Container> containers = new ArrayList<>();
        if (level.getBlockEntity(building.pos()) instanceof HutBlockEntity hut) {
            containers.add(hut);
        }
        if (building.type() == BuildingType.WAREHOUSE && building.level() > 0) {
            BoundingBox box = Blueprints.footprint(level.getServer(), level, building);
            if (box != null) {
                for (BlockPos pos : BlockPos.betweenClosed(box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ())) {
                    var blockEntity = level.getBlockEntity(pos);
                    if (blockEntity instanceof BarrelBlockEntity || blockEntity instanceof ChestBlockEntity) {
                        containers.add((Container) blockEntity);
                    }
                }
            }
        }
        return containers;
    }

    public static int count(List<? extends Container> containers, Predicate<ItemStack> matcher) {
        int total = 0;
        for (Container container : containers) {
            for (int i = 0; i < container.getContainerSize(); i++) {
                ItemStack stack = container.getItem(i);
                if (!stack.isEmpty() && matcher.test(stack)) {
                    total += stack.getCount();
                }
            }
        }
        return total;
    }

    /** Removes up to {@code amount} matching items and returns them as stacks. */
    public static List<ItemStack> extract(List<? extends Container> containers, Predicate<ItemStack> matcher, int amount) {
        List<ItemStack> result = new ArrayList<>();
        for (Container container : containers) {
            for (int i = 0; i < container.getContainerSize() && amount > 0; i++) {
                ItemStack stack = container.getItem(i);
                if (!stack.isEmpty() && matcher.test(stack)) {
                    ItemStack part = stack.split(Math.min(amount, stack.getCount()));
                    amount -= part.getCount();
                    result.add(part);
                    container.setChanged();
                }
            }
        }
        return result;
    }

    /** Inserts a stack and returns what did not fit. */
    public static ItemStack insert(List<? extends Container> containers, ItemStack stack) {
        ItemStack rest = stack;
        for (Container container : containers) {
            for (int i = 0; i < container.getContainerSize() && !rest.isEmpty(); i++) {
                ItemStack slot = container.getItem(i);
                if (!slot.isEmpty() && ItemStack.isSameItemSameComponents(slot, rest) && slot.getCount() < slot.getMaxStackSize()) {
                    int moved = Math.min(rest.getCount(), slot.getMaxStackSize() - slot.getCount());
                    slot.grow(moved);
                    rest.shrink(moved);
                    container.setChanged();
                }
            }
        }
        for (Container container : containers) {
            for (int i = 0; i < container.getContainerSize() && !rest.isEmpty(); i++) {
                if (container.getItem(i).isEmpty()) {
                    container.setItem(i, rest);
                    container.setChanged();
                    rest = ItemStack.EMPTY;
                }
            }
        }
        return rest;
    }

    public static boolean hasSpace(List<? extends Container> containers) {
        for (Container container : containers) {
            for (int i = 0; i < container.getContainerSize(); i++) {
                if (container.getItem(i).isEmpty()) {
                    return true;
                }
            }
        }
        return false;
    }
}
