package dev.pikachux.hearthstead.entity.ai;

import dev.pikachux.hearthstead.block.HutBlockEntity;
import dev.pikachux.hearthstead.colony.BuildingData;
import dev.pikachux.hearthstead.colony.CitizenData;
import dev.pikachux.hearthstead.colony.Colony;
import dev.pikachux.hearthstead.colony.Guards;
import dev.pikachux.hearthstead.colony.JobType;
import dev.pikachux.hearthstead.colony.Skill;
import dev.pikachux.hearthstead.entity.CitizenEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Guard: picks up the gear of its class from the guard building (knights a sword and shield,
 * rangers a bow and arrows, druids nether wart for their potions) and then follows the
 * building's order: patrol around it, patrol the whole colony, hold it, or follow a colony
 * member and defend them. Fighting itself is done by the melee, ranged and potion goals.
 */
public class GuardPatrolGoal extends WorkGoal {
    /** How far a guard looks for the colony member to follow. */
    public static final double FOLLOW_RANGE = 64;
    /** How close a following guard stays to the player. */
    public static final double FOLLOW_DISTANCE = 4;

    @Nullable
    private BlockPos patrolTarget;
    private int timer;

    public GuardPatrolGoal(CitizenEntity citizen) {
        super(citizen, JobType.GUARD);
    }

    @Override
    public boolean canUse() {
        CitizenData data = citizen.data();
        return data != null && data.job() == JobType.GUARD && data.workPos() != null
                && data.saturation() > 2 && citizen.getTarget() == null;
    }

    @Override
    public void stop() {
        citizen.getNavigation().stop();
        patrolTarget = null;
    }

    @Override
    public void tick() {
        BlockPos tower = hutPos();
        BuildingData post = building();
        if (tower == null) {
            return;
        }
        Guards.Kind kind = Guards.kind(post);
        boolean nearTower = citizen.blockPosition().closerToCenterThan(tower.getCenter(), 4);
        if (!equipped(kind) && gearInBuilding(kind)) {
            // Fetch the class's gear from the building first; without it they still do their duty.
            if (!nearTower) {
                walkTo(tower, 3);
                citizen.setStatus("Fetching gear from the " + (post == null ? "tower" : post.type().displayName()));
                return;
            }
            equip(kind);
        }
        if (nearTower) {
            restock(kind);
        }
        switch (Guards.order(post)) {
            case HOLD -> hold(tower);
            case FOLLOW -> follow(tower);
            case COLONY -> patrolColony(tower);
            default -> patrol(tower);
        }
        CitizenData data = citizen.data();
        if (!equipped(kind) && data != null && data.status().startsWith("Patrolling")) {
            citizen.setStatus(data.status() + " (" + missingGear(kind) + ")");
        }
    }

    // ---------------------------------------------------------------- gear

    /** Whether the guard holds everything its class fights with. */
    public boolean equipped(Guards.Kind kind) {
        return switch (kind) {
            case KNIGHT -> citizen.getMainHandItem().is(ItemTags.SWORDS) && citizen.getOffhandItem().is(Items.SHIELD);
            case RANGER -> citizen.getMainHandItem().is(Items.BOW) && citizen.inventory().countItem(Items.ARROW) > 0;
            case DRUID -> citizen.inventory().countItem(Items.NETHER_WART) > 0;
        };
    }

    /** Whether the building holds something the guard is missing. */
    private boolean gearInBuilding(Guards.Kind kind) {
        HutBlockEntity hut = hut();
        if (hut == null) {
            return false;
        }
        java.util.function.Predicate<ItemStack> wanted = switch (kind) {
            case KNIGHT -> s -> !citizen.getMainHandItem().is(ItemTags.SWORDS) && s.is(ItemTags.SWORDS)
                    || !citizen.getOffhandItem().is(Items.SHIELD) && s.is(Items.SHIELD);
            case RANGER -> s -> !citizen.getMainHandItem().is(Items.BOW) && s.is(Items.BOW) || s.is(Items.ARROW);
            case DRUID -> s -> s.is(Items.NETHER_WART);
        };
        for (int i = 0; i < hut.getContainerSize(); i++) {
            if (wanted.test(hut.getItem(i))) {
                return true;
            }
        }
        return false;
    }

    private static String missingGear(Guards.Kind kind) {
        return switch (kind) {
            case KNIGHT -> "a sword and shield in the building would help";
            case RANGER -> "needs a bow and arrows";
            case DRUID -> "needs nether wart for potions";
        };
    }

    private void equip(Guards.Kind kind) {
        switch (kind) {
            case KNIGHT -> {
                equipTool(ItemTags.SWORDS);
                equipOffhand(s -> s.is(Items.SHIELD));
            }
            case RANGER -> {
                equipTool(s -> s.is(Items.BOW));
                // A ranger's shield would only be in the way; it goes back to the building.
                if (citizen.getOffhandItem().is(Items.SHIELD)) {
                    storeInHut(citizen.getOffhandItem());
                    citizen.setItemSlot(EquipmentSlot.OFFHAND, ItemStack.EMPTY);
                }
            }
            case DRUID -> {
                if (citizen.getMainHandItem().is(Items.BOW)) {
                    storeInHut(citizen.getMainHandItem());
                    citizen.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
                }
            }
        }
    }

    /** Keeps a supply of arrows or nether wart, from the building's stock. */
    private void restock(Guards.Kind kind) {
        CitizenData data = citizen.data();
        boolean shoots = kind == Guards.Kind.RANGER || data != null && data.skill(Skill.MARKSMANSHIP) >= GuardRangedGoal.MIN_MARKSMANSHIP;
        if (shoots && citizen.inventory().countItem(Items.ARROW) < 8) {
            takeFromHut(s -> s.is(Items.ARROW), kind == Guards.Kind.RANGER ? 32 : 16);
        }
        if (kind == Guards.Kind.DRUID && citizen.inventory().countItem(Items.NETHER_WART) < 2) {
            takeFromHut(s -> s.is(Items.NETHER_WART), 4);
        }
    }

    private boolean equipOffhand(java.util.function.Predicate<ItemStack> item) {
        if (item.test(citizen.getOffhandItem())) {
            return true;
        }
        HutBlockEntity hut = hut();
        if (hut == null) {
            return false;
        }
        for (int i = 0; i < hut.getContainerSize(); i++) {
            ItemStack stack = hut.getItem(i);
            if (!stack.isEmpty() && item.test(stack)) {
                ItemStack previous = citizen.getOffhandItem();
                citizen.setItemSlot(EquipmentSlot.OFFHAND, stack.split(1));
                if (!previous.isEmpty()) {
                    storeInHut(previous);
                }
                hut.setChanged();
                return true;
            }
        }
        return false;
    }

    // ---------------------------------------------------------------- orders

    private void patrol(BlockPos tower) {
        citizen.setStatus("Patrolling");
        if (patrolTarget == null || walkTo(patrolTarget, 2) || --timer <= 0) {
            int radius = 6 + (building() == null ? 0 : building().level() * 3);
            patrolTarget = tower.offset(citizen.getRandom().nextInt(radius * 2 + 1) - radius, 0,
                    citizen.getRandom().nextInt(radius * 2 + 1) - radius);
            timer = 200;
        }
    }

    /** Walks from one hut of the colony to another. */
    private void patrolColony(BlockPos tower) {
        Colony colony = citizen.colony();
        citizen.setStatus("Patrolling the colony");
        if (patrolTarget == null || walkTo(patrolTarget, 3) || --timer <= 0) {
            List<BlockPos> huts = new ArrayList<>();
            if (colony != null) {
                colony.buildings().forEach(b -> huts.add(b.pos()));
            }
            BlockPos hut = huts.isEmpty() ? tower : huts.get(citizen.getRandom().nextInt(huts.size()));
            patrolTarget = hut.offset(citizen.getRandom().nextInt(7) - 3, 0, citizen.getRandom().nextInt(7) - 3);
            timer = 400;
        }
    }

    private void hold(BlockPos tower) {
        citizen.setStatus("Holding the " + (building() == null ? "tower" : building().type().displayName()));
        walkTo(tower, 2.5);
    }

    private void follow(BlockPos tower) {
        ServerPlayer player = followed(citizen);
        if (player == null) {
            hold(tower);
            citizen.setStatus("Holding (no colony member nearby to follow)");
            return;
        }
        citizen.setStatus("Following " + player.getName().getString());
        if (citizen.distanceTo(player) > FOLLOW_DISTANCE) {
            citizen.getNavigation().moveTo(player, 1.1);
        } else {
            citizen.getNavigation().stop();
            citizen.getLookControl().setLookAt(player);
        }
        LivingEntity threat = threatTo(player);
        if (threat != null) {
            citizen.setTarget(threat);
        }
    }

    /** The nearest colony member (owner or officer) a following guard stays with. */
    @Nullable
    public static ServerPlayer followed(CitizenEntity citizen) {
        Colony colony = citizen.colony();
        if (colony == null || citizen.level().getServer() == null) {
            return null;
        }
        ServerPlayer best = null;
        for (ServerPlayer player : citizen.level().getServer().getPlayerList().getPlayers()) {
            if (player.level() == citizen.level() && colony.isMember(player) && !player.isSpectator() && player.isAlive()
                    && citizen.distanceTo(player) <= FOLLOW_RANGE
                    && (best == null || citizen.distanceTo(player) < citizen.distanceTo(best))) {
                best = player;
            }
        }
        return best;
    }

    /** Whatever attacked the player, or the monster the player is fighting. */
    @Nullable
    private LivingEntity threatTo(ServerPlayer player) {
        LivingEntity attacker = player.getLastHurtByMob();
        if (attacker != null && attacker.isAlive() && !(attacker instanceof CitizenEntity) && !(attacker instanceof ServerPlayer)
                && attacker.distanceTo(player) < 16) {
            return attacker;
        }
        LivingEntity fought = player.getLastHurtMob();
        if (fought instanceof Enemy && fought.isAlive() && fought.distanceTo(player) < 16) {
            return fought;
        }
        return null;
    }
}
