package dev.pikachux.hearthstead.test;

import dev.pikachux.hearthstead.colony.BuildingData;
import dev.pikachux.hearthstead.colony.BuildingType;
import dev.pikachux.hearthstead.colony.CitizenData;
import dev.pikachux.hearthstead.colony.Colony;
import dev.pikachux.hearthstead.colony.JobType;
import dev.pikachux.hearthstead.colony.Skill;
import dev.pikachux.hearthstead.entity.CitizenEntity;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;

import java.util.Arrays;

public class FamilyGameTests {
    private static final String PLATFORM = "hearthstead-gametest:platform";

    /** A child is small, does not work, and grows up into an ordinary citizen. */
    @GameTest(environment = "hearthstead-gametest:family", structure = PLATFORM, maxTicks = 400)
    public void childrenGrowUp(GameTestHelper helper) {
        Colony colony = TestColonies.found(helper, new BlockPos(4, 1, 27));
        BuildingData home = TestColonies.hut(helper, colony, new BlockPos(14, 1, 14), BuildingType.RESIDENCE, 1);
        TestColonies.hut(helper, colony, new BlockPos(4, 1, 4), BuildingType.LUMBERJACK, 1);
        CitizenData child = colony.addChild(helper.getLevel(), home, "Fletcher");
        check(helper, child.child() && child.name().endsWith(" Fletcher") && home.pos().equals(child.homePos()), "a child of the family");
        helper.runAfterDelay(40, () -> {
            CitizenEntity entity = (CitizenEntity) helper.getLevel().getEntity(child.entityUuid());
            check(helper, entity != null && entity.isBaby() && entity.getBbHeight() < 1.2F, "the child to be small");
            check(helper, child.job() == JobType.UNEMPLOYED, "children not to work");
            child.grow(CitizenData.GROW_UP_SECONDS - 2);
            helper.succeedWhen(() -> {
                check(helper, !child.child() && !entity.isBaby(), "the child to grow up");
                check(helper, child.job() == JobType.LUMBERJACK, "the grown-up to take the free job");
            });
        });
    }

    /** A child is born where two adults share a residence and there is a free bed. */
    @GameTest(environment = "hearthstead-gametest:births", structure = PLATFORM, maxTicks = 100)
    public void birthsNeedTwoAdults(GameTestHelper helper) {
        Colony colony = TestColonies.found(helper, new BlockPos(4, 1, 27));
        BuildingData home = TestColonies.hut(helper, colony, new BlockPos(14, 1, 14), BuildingType.RESIDENCE, 1);
        var level = helper.getLevel();
        CitizenData first = colony.addNewCitizen(level);
        check(helper, !colony.happenings().tryBirth(colony, level), "no birth with only one adult in the colony");
        CitizenData second = colony.addNewCitizen(level);
        first.setHomePos(home.pos());
        second.setHomePos(home.pos());
        int before = colony.citizens().size();
        check(helper, colony.happenings().tryBirth(colony, level), "a birth with two adults at home");
        check(helper, colony.citizens().size() == before + 1
                && colony.citizens().stream().anyMatch(c -> c.child() && home.pos().equals(c.homePos())), "the baby at home");
        helper.succeed();
    }

    /** Visitors stay at the tavern without working until they are recruited. */
    @GameTest(environment = "hearthstead-gametest:tavern", structure = PLATFORM, maxTicks = 200)
    public void visitorsCanBeRecruited(GameTestHelper helper) {
        Colony colony = TestColonies.found(helper, new BlockPos(4, 1, 27));
        BuildingData tavern = TestColonies.hut(helper, colony, new BlockPos(14, 1, 14), BuildingType.TAVERN, 1);
        TestColonies.hut(helper, colony, new BlockPos(4, 1, 4), BuildingType.LUMBERJACK, 1);
        var level = helper.getLevel();
        CitizenData visitor = colony.addVisitor(level, tavern);
        check(helper, visitor.visitor() && colony.residents() == 0 && tavern.pos().equals(visitor.homePos()), "a visitor at the tavern");
        check(helper, Arrays.stream(Skill.values()).filter(s -> s != Skill.MARKSMANSHIP).allMatch(s -> visitor.skill(s) >= 5),
                "visitors to be more skilled than settlers");
        check(helper, !visitor.recruitCost().isEmpty(), "a price to join");
        helper.runAfterDelay(40, () -> {
            check(helper, visitor.job() == JobType.UNEMPLOYED, "visitors not to work");
            check(helper, colony.recruit(visitor) && !visitor.visitor() && colony.residents() == 1, "recruiting the visitor");
            helper.succeedWhen(() -> check(helper, visitor.job() == JobType.LUMBERJACK, "the new citizen to take the free job"));
        });
    }

    /** The undertaker buries the dead, which ends the mourning. */
    @GameTest(environment = "hearthstead-gametest:graveyard", structure = PLATFORM, maxTicks = 1600, skyAccess = true)
    public void undertakerBuriesTheDead(GameTestHelper helper) {
        Colony colony = TestColonies.found(helper, new BlockPos(4, 1, 27));
        TestColonies.hut(helper, colony, new BlockPos(14, 1, 14), BuildingType.GRAVEYARD, 1);
        var level = helper.getLevel();
        CitizenData undertaker = colony.addNewCitizen(level);
        CitizenData departed = CitizenData.create(999, level.getRandom());
        colony.recordDeath(departed, level.getGameTime());
        check(helper, colony.mourning(level.getGameTime()) && colony.dead().size() == 1, "the colony to mourn");
        helper.succeedWhen(() -> {
            check(helper, colony.dead().isEmpty(), "the dead to be buried (" + undertaker.status() + ")");
            check(helper, !colony.mourning(level.getGameTime()), "the mourning to end with the burial");
            check(helper, colony.fallen().contains(departed.name()) || colony.citizen(departed.id()) != null,
                    "the departed buried or brought back");
        });
    }

    /** Children learn at a school with a teacher. */
    @GameTest(environment = "hearthstead-gametest:school", structure = PLATFORM, maxTicks = 1200, skyAccess = true)
    public void childrenLearnAtSchool(GameTestHelper helper) {
        Colony colony = TestColonies.found(helper, new BlockPos(4, 1, 27));
        TestColonies.hut(helper, colony, new BlockPos(14, 1, 14), BuildingType.SCHOOL, 1);
        BuildingData home = TestColonies.hut(helper, colony, new BlockPos(4, 1, 4), BuildingType.RESIDENCE, 1);
        var level = helper.getLevel();
        CitizenData teacher = colony.addNewCitizen(level);
        CitizenData child = colony.addChild(level, home, "Birch");
        helper.succeedWhen(() -> {
            check(helper, teacher.job() == JobType.TEACHER && colony.hasStaffed(BuildingType.SCHOOL), "a teacher at the school");
            double learned = Arrays.stream(Skill.values()).mapToDouble(s -> s == Skill.MARKSMANSHIP ? 0 : child.skill(s) - 1 + child.skillProgress(s)).sum();
            check(helper, learned > 0, "the child to learn at school (" + child.status() + ")");
        });
    }

    private static void check(GameTestHelper helper, boolean condition, String expected) {
        if (!condition) {
            throw helper.assertionException("Expected " + expected);
        }
    }
}
