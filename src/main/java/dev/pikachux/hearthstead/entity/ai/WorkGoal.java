package dev.pikachux.hearthstead.entity.ai;

import dev.pikachux.hearthstead.colony.Research;
import dev.pikachux.hearthstead.block.HutBlockEntity;
import dev.pikachux.hearthstead.colony.BuildingData;
import dev.pikachux.hearthstead.colony.CitizenData;
import dev.pikachux.hearthstead.colony.Colony;
import dev.pikachux.hearthstead.colony.JobType;
import dev.pikachux.hearthstead.entity.CitizenEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.EnumSet;
import java.util.List;

/**
 * Shared behaviour for all job goals: walking to targets, using the hut inventory,
 * equipping tools and harvesting blocks into the citizen's own inventory.
 */
public abstract class WorkGoal extends Goal {
    protected final CitizenEntity citizen;
    private final JobType job;
    /** Ticks without a usable path after which a stuck citizen is moved next to its target. */
    private static final int STUCK_TICKS = 100;

    private int pathCooldown;
    private int stuckTicks;

    protected WorkGoal(CitizenEntity citizen, JobType job) {
        this.citizen = citizen;
        this.job = job;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        CitizenData data = citizen.data();
        return data != null && data.job() == job && data.workPos() != null
                && citizen.level().isBrightOutside() && data.saturation() > 2 && citizen.getTarget() == null;
    }

    @Override
    public boolean canContinueToUse() {
        return canUse();
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    protected ServerLevel level() {
        return (ServerLevel) citizen.level();
    }

    @Nullable
    protected BlockPos hutPos() {
        CitizenData data = citizen.data();
        return data == null ? null : data.workPos();
    }

    @Nullable
    protected BuildingData building() {
        Colony colony = citizen.colony();
        BlockPos pos = hutPos();
        return colony == null || pos == null ? null : colony.building(pos);
    }

    @Nullable
    protected HutBlockEntity hut() {
        BlockPos pos = hutPos();
        return pos != null && level().getBlockEntity(pos) instanceof HutBlockEntity hut ? hut : null;
    }

    /** Moves towards the position; returns true once within the given distance. */
    protected boolean walkTo(BlockPos pos, double reach) {
        if (citizen.blockPosition().closerToCenterThan(pos.getCenter(), reach)) {
            citizen.getNavigation().stop();
            citizen.getLookControl().setLookAt(pos.getCenter());
            stuckTicks = 0;
            return true;
        }
        if (citizen.getNavigation().isDone() && ++stuckTicks > STUCK_TICKS) {
            // No path, e.g. the citizen got walled in or onto a roof: step over to the target.
            stuckTicks = 0;
            BlockPos spot = standingSpot(pos);
            if (spot != null) {
                citizen.teleportTo(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5);
                return false;
            }
        }
        if (--pathCooldown <= 0 || citizen.getNavigation().isDone()) {
            pathCooldown = 20;
            citizen.getNavigation().moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, 0.8);
        }
        return false;
    }

    /** A free, dry position near the target where the citizen can stand, or null. */
    @Nullable
    protected BlockPos standingSpot(BlockPos target) {
        BlockPos best = null;
        double bestDistance = Double.MAX_VALUE;
        for (BlockPos pos : BlockPos.betweenClosed(target.offset(-3, -3, -3), target.offset(3, 3, 3))) {
            if (pos.getX() == target.getX() && pos.getZ() == target.getZ()) {
                continue;
            }
            if (level().getBlockState(pos).getCollisionShape(level(), pos).isEmpty()
                    && level().getFluidState(pos).isEmpty()
                    && level().getBlockState(pos.above()).getCollisionShape(level(), pos.above()).isEmpty()
                    && !level().getBlockState(pos.below()).getCollisionShape(level(), pos.below()).isEmpty()) {
                // Prefer spots at the target's height over spots on top of things.
                double distance = pos.distSqr(target) + Math.max(0, pos.getY() - target.getY()) * 4;
                if (distance < bestDistance) {
                    bestDistance = distance;
                    best = pos.immutable();
                }
            }
        }
        return best;
    }

    /** Ensures a tool of the given tag is held, fetching one from the hut if needed. */
    protected boolean equipTool(TagKey<Item> tag) {
        return equipTool(stack -> stack.is(tag));
    }

    /** Ensures a matching tool is held, fetching one from the hut if needed. */
    protected boolean equipTool(java.util.function.Predicate<ItemStack> tool) {
        if (tool.test(citizen.getMainHandItem())) {
            return true;
        }
        HutBlockEntity hut = hut();
        if (hut == null) {
            return false;
        }
        for (int i = 0; i < hut.getContainerSize(); i++) {
            ItemStack stack = hut.getItem(i);
            if (!stack.isEmpty() && tool.test(stack)) {
                ItemStack previous = citizen.getMainHandItem();
                citizen.setItemSlot(EquipmentSlot.MAINHAND, stack.split(1));
                if (!previous.isEmpty()) {
                    storeInHut(previous);
                }
                hut.setChanged();
                return true;
            }
        }
        return false;
    }

    /** Takes up to {@code count} matching items from the hut into the citizen's inventory. */
    protected int takeFromHut(java.util.function.Predicate<ItemStack> matcher, int count) {
        HutBlockEntity hut = hut();
        if (hut == null) {
            return 0;
        }
        int taken = 0;
        for (int i = 0; i < hut.getContainerSize() && taken < count; i++) {
            ItemStack stack = hut.getItem(i);
            if (matcher.test(stack)) {
                ItemStack part = stack.split(count - taken);
                taken += part.getCount();
                ItemStack rest = citizen.inventory().addItem(part);
                if (!rest.isEmpty()) {
                    taken -= rest.getCount();
                    stack.grow(rest.getCount());
                    break;
                }
            }
        }
        hut.setChanged();
        return taken;
    }

    /** Puts a stack into the hut, dropping whatever does not fit. */
    protected void storeInHut(ItemStack stack) {
        HutBlockEntity hut = hut();
        ItemStack rest = stack;
        if (hut != null) {
            for (int i = 0; i < hut.getContainerSize() && !rest.isEmpty(); i++) {
                ItemStack slot = hut.getItem(i);
                if (slot.isEmpty()) {
                    hut.setItem(i, rest);
                    rest = ItemStack.EMPTY;
                } else if (ItemStack.isSameItemSameComponents(slot, rest) && slot.getCount() < slot.getMaxStackSize()) {
                    int moved = Math.min(rest.getCount(), slot.getMaxStackSize() - slot.getCount());
                    slot.grow(moved);
                    rest.shrink(moved);
                }
            }
            hut.setChanged();
        }
        if (!rest.isEmpty()) {
            citizen.spawnAtLocation(level(), rest);
        }
    }

    /** Moves the whole carried inventory into the hut. */
    protected void depositAll() {
        SimpleContainer inventory = citizen.inventory();
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.removeItemNoUpdate(i);
            if (!stack.isEmpty()) {
                storeInHut(stack);
            }
        }
    }

    protected boolean inventoryFull() {
        SimpleContainer inventory = citizen.inventory();
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            if (inventory.getItem(i).isEmpty()) {
                return false;
            }
        }
        return true;
    }

    protected boolean inventoryEmpty() {
        return citizen.inventory().isEmpty();
    }

    /** Breaks a block with the held tool, adding its drops to the citizen inventory. */
    protected void harvest(BlockPos pos) {
        ServerLevel level = level();
        BlockState state = level.getBlockState(pos);
        ItemStack tool = citizen.getMainHandItem();
        List<ItemStack> drops = Block.getDrops(state, level, pos, level.getBlockEntity(pos), citizen, tool);
        level.destroyBlock(pos, false, citizen);
        for (ItemStack drop : drops) {
            ItemStack rest = citizen.inventory().addItem(drop);
            if (!rest.isEmpty()) {
                citizen.spawnAtLocation(level, rest);
            }
        }
        if (!tool.isEmpty() && tool.isDamageableItem()) {
            tool.hurtAndBreak(1, citizen, EquipmentSlot.MAINHAND);
        }
        citizen.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
    }

    /** Picks up item entities lying around the citizen. */
    protected void collectNearbyItems(double radius) {
        for (ItemEntity item : level().getEntitiesOfClass(ItemEntity.class, citizen.getBoundingBox().inflate(radius))) {
            if (!item.isAlive() || item.hasPickUpDelay()) {
                continue;
            }
            ItemStack rest = citizen.inventory().addItem(item.getItem().copy());
            if (rest.isEmpty()) {
                item.discard();
            } else {
                item.setItem(rest);
            }
        }
    }

    /** Whether the citizen's colony has finished a research. */
    protected boolean researched(Research research) {
        var colony = citizen.colony();
        return colony != null && colony.research().has(research);
    }

    /**
     * Adds one more of the block's main drop to the citizen's inventory: the largest stack it
     * would drop, so the produce rather than seeds or a sapling. Used by yield research.
     */
    protected void bonusDrop(BlockState state, BlockPos pos) {
        List<ItemStack> drops = Block.getDrops(state, level(), pos, null);
        drops.stream().max(java.util.Comparator.comparingInt(ItemStack::getCount)).ifPresent(drop -> {
            ItemStack rest = citizen.inventory().addItem(drop.copyWithCount(1));
            if (!rest.isEmpty()) {
                citizen.spawnAtLocation(level(), rest);
            }
        });
    }

    /** Ticks needed to break a block, scaled by citizen happiness. */
    protected int breakDelay(int base) {
        return Math.max(4, (int) (base / citizen.workSpeed()));
    }
}
