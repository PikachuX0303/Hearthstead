package dev.pikachux.hearthstead.entity.ai;

import dev.pikachux.hearthstead.entity.Status;
import dev.pikachux.hearthstead.colony.BuildingData;
import dev.pikachux.hearthstead.colony.BuildingType;
import dev.pikachux.hearthstead.colony.CitizenData;
import dev.pikachux.hearthstead.colony.Colony;
import dev.pikachux.hearthstead.colony.Logistics;
import dev.pikachux.hearthstead.colony.Storage;
import dev.pikachux.hearthstead.entity.CitizenEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.EnumSet;
import java.util.List;
import java.util.function.Predicate;

/**
 * Hungry citizens look for food in their home, the town hall, residences, the cook's
 * hut or the warehouse.
 */
public class CitizenEatGoal extends Goal {
    private final CitizenEntity citizen;
    @Nullable
    private BlockPos foodSource;
    private int pathCooldown;
    private int cooldown;

    public CitizenEatGoal(CitizenEntity citizen) {
        this.citizen = citizen;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (cooldown > 0) {
            cooldown--;
            return false;
        }
        CitizenData data = citizen.data();
        if (data == null || !data.isHungry() || citizen.getTarget() != null) {
            return false;
        }
        foodSource = findFoodSource(data);
        if (foodSource == null) {
            citizen.setStatus(Status.of("hungry_no_food_colony"));
            cooldown = 200;
            return false;
        }
        return true;
    }

    @Override
    public boolean canContinueToUse() {
        return foodSource != null;
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Nullable
    private BlockPos findFoodSource(CitizenData data) {
        Colony colony = citizen.colony();
        if (colony == null) {
            return null;
        }
        if (data.homePos() != null && hasFood(colony.building(data.homePos()))) {
            return data.homePos();
        }
        BuildingData best = null;
        for (BuildingData building : colony.buildings()) {
            BuildingType type = building.type();
            if ((type == BuildingType.TOWN_HALL || type == BuildingType.RESIDENCE || type == BuildingType.WAREHOUSE
                    || type == BuildingType.COOK || type == BuildingType.BAKER) && hasFood(building)
                    && (best == null || building.pos().distSqr(citizen.blockPosition()) < best.pos().distSqr(citizen.blockPosition()))) {
                best = building;
            }
        }
        return best == null ? null : best.pos();
    }

    private boolean hasFood(@Nullable BuildingData building) {
        return building != null
                && Storage.count(Storage.containers((ServerLevel) citizen.level(), building), s -> s.has(DataComponents.FOOD)) > 0;
    }

    @Override
    public void tick() {
        if (foodSource == null) {
            return;
        }
        if (!citizen.blockPosition().closerToCenterThan(foodSource.getCenter(), 3)) {
            citizen.setStatus(Status.of("looking_food"));
            if (--pathCooldown <= 0) {
                pathCooldown = 20;
                citizen.getNavigation().moveTo(foodSource.getX() + 0.5, foodSource.getY(), foodSource.getZ() + 0.5, 0.8);
            }
            return;
        }
        CitizenData data = citizen.data();
        Colony colony = citizen.colony();
        BuildingData building = colony == null ? null : colony.building(foodSource);
        if (data != null && building != null) {
            List<Container> storage = Storage.containers((ServerLevel) citizen.level(), building);
            // Cooked meals first, raw food only if nothing else is left.
            for (Predicate<ItemStack> kind : List.<Predicate<ItemStack>>of(Logistics::isMeal, s -> s.has(DataComponents.FOOD))) {
                while (data.isHungry()) {
                    List<ItemStack> taken = Storage.extract(storage, kind, 1);
                    if (taken.isEmpty()) {
                        break;
                    }
                    FoodProperties food = taken.getFirst().get(DataComponents.FOOD);
                    data.setSaturation(data.saturation() + food.nutrition() + food.saturation());
                    citizen.playSound(SoundEvents.GENERIC_EAT.value(), 1.0F, 1.0F);
                }
            }
            citizen.setStatus(Status.of("ate_meal"));
        }
        foodSource = null;
    }
}
