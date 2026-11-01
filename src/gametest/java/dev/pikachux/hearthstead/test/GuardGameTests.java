package dev.pikachux.hearthstead.test;

import dev.pikachux.hearthstead.block.HutBlockEntity;
import dev.pikachux.hearthstead.colony.BuildingData;
import dev.pikachux.hearthstead.colony.BuildingType;
import dev.pikachux.hearthstead.colony.CitizenData;
import dev.pikachux.hearthstead.colony.Colony;
import dev.pikachux.hearthstead.colony.Guards;
import dev.pikachux.hearthstead.colony.HutSettings;
import dev.pikachux.hearthstead.colony.JobType;
import dev.pikachux.hearthstead.colony.Logistics;
import dev.pikachux.hearthstead.entity.CitizenEntity;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Difficulty;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.zombie.Husk;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.List;

public class GuardGameTests {
    private static final String PLATFORM = "hearthstead-gametest:platform";

    /** Classes and orders are hut settings of guard buildings, knights and patrols by default. */
    @GameTest
    public void guardSettingsCycle(GameTestHelper helper) {
        BuildingData tower = new BuildingData(BlockPos.ZERO, BuildingType.GUARD_TOWER, 1);
        check(helper, HutSettings.of(BuildingType.BARRACKS).contains(HutSettings.GUARD_CLASS), "barracks to have guard classes");
        check(helper, Guards.kind(tower) == Guards.Kind.KNIGHT && Guards.order(tower) == Guards.Order.PATROL, "knights on patrol by default");
        HutSettings.cycle(tower, HutSettings.GUARD_CLASS);
        HutSettings.cycle(tower, HutSettings.GUARD_ORDER);
        check(helper, Guards.kind(tower) == Guards.Kind.RANGER && Guards.order(tower) == Guards.Order.COLONY, "rangers patrolling the colony");
        BuildingData copy = BuildingData.load(tower.save());
        check(helper, Guards.kind(copy) == Guards.Kind.RANGER && Guards.order(copy) == Guards.Order.COLONY, "the settings to be saved");
        helper.succeed();
    }

    /** Each class asks for its own gear. */
    @GameTest(environment = "hearthstead-gametest:guard_gear", structure = PLATFORM)
    public void guardsRequestTheirGear(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Colony colony = TestColonies.found(helper, new BlockPos(4, 1, 27));
        BuildingData tower = TestColonies.hut(helper, colony, new BlockPos(14, 1, 14), BuildingType.GUARD_TOWER, 1);
        check(helper, requests(level, colony, tower).containsAll(List.of("Sword", "Shield")), "a knight's sword and shield");
        tower.setSetting(HutSettings.GUARD_CLASS.id(), "ranger");
        check(helper, requests(level, colony, tower).containsAll(List.of("Bow", "Arrows")), "a ranger's bow and arrows");
        tower.setSetting(HutSettings.GUARD_CLASS.id(), "druid");
        check(helper, requests(level, colony, tower).equals(List.of("Nether Wart")), "only nether wart for a druid");
        helper.succeed();
    }

    /** Knights take a sword and a shield and get extra armour. */
    @GameTest(environment = "hearthstead-gametest:knight", structure = PLATFORM, maxTicks = 600)
    public void knightsCarryShields(GameTestHelper helper) {
        Colony colony = TestColonies.found(helper, new BlockPos(4, 1, 27));
        BuildingData tower = TestColonies.hut(helper, colony, new BlockPos(14, 1, 14), BuildingType.GUARD_TOWER, 1);
        stock(helper, tower, new ItemStack(Items.IRON_SWORD), new ItemStack(Items.SHIELD));
        CitizenData guard = colony.addNewCitizen(helper.getLevel());
        helper.succeedWhen(() -> {
            CitizenEntity entity = entity(helper, guard);
            check(helper, entity.getMainHandItem().is(Items.IRON_SWORD) && entity.getOffhandItem().is(Items.SHIELD),
                    "a sword and a shield (" + guard.status() + ")");
            check(helper, entity.getAttributeValue(Attributes.ARMOR) >= CitizenEntity.guardArmor(null) + CitizenEntity.KNIGHT_ARMOR_BONUS,
                    "a knight's armour");
            check(helper, CitizenEntity.role(colony, guard).equals("Knight"), "the citizen to be called a knight");
        });
    }

    /** Rangers shoot arrows with their bow from a distance. */
    @GameTest(environment = "hearthstead-gametest:ranger", structure = PLATFORM, maxTicks = 800, skyAccess = true)
    public void rangersShootWithBows(GameTestHelper helper) {
        helper.getLevel().getServer().setDifficulty(Difficulty.NORMAL, true);
        Colony colony = TestColonies.found(helper, new BlockPos(4, 1, 27));
        BuildingData tower = TestColonies.hut(helper, colony, new BlockPos(14, 1, 14), BuildingType.GUARD_TOWER, 1);
        tower.setSetting(HutSettings.GUARD_CLASS.id(), "ranger");
        stock(helper, tower, new ItemStack(Items.BOW), new ItemStack(Items.ARROW, 32));
        CitizenData guard = colony.addNewCitizen(helper.getLevel());
        Husk[] husk = new Husk[1];
        helper.succeedWhen(() -> {
            CitizenEntity entity = entity(helper, guard);
            check(helper, entity.getMainHandItem().is(Items.BOW), "a bow (" + guard.status() + ")");
            if (husk[0] == null) {
                husk[0] = monster(helper, entity, 10);
                entity.setTarget(husk[0]);
            }
            check(helper, husk[0].getHealth() < husk[0].getMaxHealth(), "the ranger to hit the husk");
            check(helper, CitizenEntity.role(colony, guard).equals("Ranger"), "the citizen to be called a ranger");
        });
    }

    /** Druids throw potions at enemies: a healing potion hurts the undead. */
    @GameTest(environment = "hearthstead-gametest:druid", structure = PLATFORM, maxTicks = 800, skyAccess = true)
    public void druidsThrowPotions(GameTestHelper helper) {
        helper.getLevel().getServer().setDifficulty(Difficulty.NORMAL, true);
        Colony colony = TestColonies.found(helper, new BlockPos(4, 1, 27));
        BuildingData tower = TestColonies.hut(helper, colony, new BlockPos(14, 1, 14), BuildingType.GUARD_TOWER, 1);
        tower.setSetting(HutSettings.GUARD_CLASS.id(), "druid");
        stock(helper, tower, new ItemStack(Items.NETHER_WART, 8));
        CitizenData guard = colony.addNewCitizen(helper.getLevel());
        Husk[] husk = new Husk[1];
        helper.succeedWhen(() -> {
            CitizenEntity entity = entity(helper, guard);
            check(helper, entity.inventory().countItem(Items.NETHER_WART) > 0, "nether wart (" + guard.status() + ")");
            if (husk[0] == null) {
                husk[0] = monster(helper, entity, 7);
                entity.setTarget(husk[0]);
            }
            check(helper, husk[0].getHealth() < husk[0].getMaxHealth(), "the druid's potion to hurt the husk");
        });
    }

    /** Druids throw regeneration potions at hurt colonists. */
    @GameTest(environment = "hearthstead-gametest:druid_heal", structure = PLATFORM, maxTicks = 800)
    public void druidsHealColonists(GameTestHelper helper) {
        Colony colony = TestColonies.found(helper, new BlockPos(4, 1, 27));
        BuildingData tower = TestColonies.hut(helper, colony, new BlockPos(14, 1, 14), BuildingType.GUARD_TOWER, 1);
        tower.setSetting(HutSettings.GUARD_CLASS.id(), "druid");
        tower.setSetting(HutSettings.GUARD_ORDER.id(), "hold");
        stock(helper, tower, new ItemStack(Items.NETHER_WART, 8));
        CitizenData guard = colony.addNewCitizen(helper.getLevel());
        CitizenData patient = colony.addNewCitizen(helper.getLevel());
        // The patient has no job and would wander off the platform: keep them still, on the hut
        // block in the middle of the tower, where the druid holds.
        CitizenEntity still = entity(helper, patient);
        still.setNoAi(true);
        still.teleportTo(tower.pos().getX() + 0.5, tower.pos().getY() + 1, tower.pos().getZ() + 0.5);
        helper.succeedWhen(() -> {
            CitizenEntity druid = entity(helper, guard);
            CitizenEntity hurt = entity(helper, patient);
            check(helper, druid.inventory().countItem(Items.NETHER_WART) > 0, "nether wart (" + guard.status() + ")");
            if (hurt.getHealth() > 5 && !hurt.hasEffect(MobEffects.REGENERATION)) {
                hurt.setHealth(4);
            }
            check(helper, hurt.hasEffect(MobEffects.REGENERATION), "the druid to heal the hurt colonist");
        });
    }

    /** Guards told to hold stay at their building. */
    @GameTest(environment = "hearthstead-gametest:guard_hold", structure = PLATFORM, maxTicks = 800)
    public void guardsHoldTheirPost(GameTestHelper helper) {
        Colony colony = TestColonies.found(helper, new BlockPos(4, 1, 27));
        BuildingData tower = TestColonies.hut(helper, colony, new BlockPos(14, 1, 14), BuildingType.GUARD_TOWER, 1);
        tower.setSetting(HutSettings.GUARD_ORDER.id(), "hold");
        CitizenData guard = colony.addNewCitizen(helper.getLevel());
        helper.succeedWhen(() -> {
            CitizenEntity entity = entity(helper, guard);
            check(helper, guard.job() == JobType.GUARD && guard.status().startsWith("Holding")
                    && entity.blockPosition().closerToCenterThan(tower.pos().getCenter(), 3.5), "the guard to hold the tower ("
                    + guard.status() + ")");
        });
    }

    /** Guards told to follow stay with the nearest colony member and fight what attacks them. */
    @GameTest(environment = "hearthstead-gametest:guard_follow", structure = PLATFORM, maxTicks = 800)
    public void guardsFollowMembers(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Colony colony = TestColonies.found(helper, new BlockPos(4, 1, 27));
        BuildingData tower = TestColonies.hut(helper, colony, new BlockPos(14, 1, 14), BuildingType.GUARD_TOWER, 1);
        tower.setSetting(HutSettings.GUARD_ORDER.id(), "follow");
        var player = helper.makeMockServerPlayerInLevel();
        colony.officers().add(player.getUUID());
        BlockPos spot = helper.absolutePos(new BlockPos(4, 1, 6));
        player.teleportTo(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5);
        CitizenData guard = colony.addNewCitizen(level);
        Husk[] husk = new Husk[1];
        helper.succeedWhen(() -> {
            CitizenEntity entity = entity(helper, guard);
            check(helper, entity.distanceTo(player) <= 5, "the guard to follow the officer (" + guard.status() + ")");
            if (husk[0] == null) {
                husk[0] = monster(helper, entity, 6);
                player.setLastHurtByMob(husk[0]);
            }
            check(helper, entity.getTarget() == husk[0] || !husk[0].isAlive(), "the guard to defend the officer");
            level.getServer().getPlayerList().remove(player);
        });
    }

    // ---------------------------------------------------------------- helpers

    private static List<String> requests(ServerLevel level, Colony colony, BuildingData building) {
        return Logistics.requests(level, colony, building).stream().map(Logistics.Request::description).toList();
    }

    private static void stock(GameTestHelper helper, BuildingData building, ItemStack... stacks) {
        HutBlockEntity hut = (HutBlockEntity) helper.getLevel().getBlockEntity(building.pos());
        for (int i = 0; i < stacks.length; i++) {
            hut.setItem(i, stacks[i]);
        }
    }

    private static CitizenEntity entity(GameTestHelper helper, CitizenData data) {
        if (data.entityUuid() == null || !(helper.getLevel().getEntity(data.entityUuid()) instanceof CitizenEntity entity)) {
            throw helper.assertionException("Expected " + data.name() + " to be in the world");
        }
        return entity;
    }

    /** A frozen husk (it does not burn by day) some blocks east of the guard. */
    private static Husk monster(GameTestHelper helper, CitizenEntity near, int distance) {
        Husk husk = EntityType.HUSK.spawn(helper.getLevel(), near.blockPosition().offset(distance, 0, 0), EntitySpawnReason.COMMAND);
        husk.setNoAi(true);
        husk.setPersistenceRequired();
        return husk;
    }

    private static void check(GameTestHelper helper, boolean condition, String expected) {
        if (!condition) {
            throw helper.assertionException("Expected " + expected);
        }
    }
}
