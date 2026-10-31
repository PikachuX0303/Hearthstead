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
import net.minecraft.util.RandomSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public class SkillGameTests {
    private static final String PLATFORM = "hearthstead-gametest:platform";

    /** Settlers start with a few points, experience raises levels, and skills are saved. */
    @GameTest
    public void skillsLevelUpAndSave(GameTestHelper helper) {
        CitizenData citizen = CitizenData.create(1, RandomSource.create(42));
        for (Skill skill : Skill.values()) {
            int level = citizen.skill(skill);
            check(helper, skill == Skill.MARKSMANSHIP ? level == 1 : level >= 1 && level <= 10, skill + " to start low, got " + level);
        }
        citizen.setSkill(Skill.STRENGTH, 3);
        check(helper, citizen.addExperience(Skill.STRENGTH, Skill.experienceFor(3)) && citizen.skill(Skill.STRENGTH) == 4,
                "enough experience to raise Strength to 4");
        citizen.addExperience(Skill.STRENGTH, 10);
        CitizenData loaded = CitizenData.load(citizen.save());
        check(helper, loaded.skill(Skill.STRENGTH) == 4 && Math.abs(loaded.skillProgress(Skill.STRENGTH) - citizen.skillProgress(Skill.STRENGTH)) < 1e-9,
                "skills and progress after loading");
        citizen.addExperience(Skill.COMBAT, 1_000_000);
        check(helper, citizen.skill(Skill.COMBAT) == Skill.MAX_LEVEL, "skills to stop at level " + Skill.MAX_LEVEL);
        check(helper, Skill.primary(JobType.MINER) == Skill.STRENGTH && Skill.primary(JobType.GUARD) == Skill.COMBAT
                && Skill.primary(JobType.UNEMPLOYED) == null, "each job's skill");
        helper.succeed();
    }

    /** A citizen works faster the higher their job's skill. */
    @GameTest(environment = "hearthstead-gametest:skills", structure = PLATFORM, maxTicks = 100)
    public void skillRaisesWorkSpeed(GameTestHelper helper) {
        Colony colony = TestColonies.found(helper, new BlockPos(4, 1, 27));
        BuildingData mine = TestColonies.hut(helper, colony, new BlockPos(14, 1, 14), BuildingType.MINER, 0);
        CitizenData data = colony.addNewCitizen(helper.getLevel());
        data.assignJob(JobType.MINER, mine.pos());
        helper.runAfterDelay(5, () -> {
            CitizenEntity entity = (CitizenEntity) helper.getLevel().getEntity(data.entityUuid());
            data.setSkill(Skill.STRENGTH, 1);
            double novice = entity.workSpeed();
            data.setSkill(Skill.STRENGTH, 50);
            double master = entity.workSpeed();
            check(helper, Math.abs(master / novice - 1.5 / 1.01) < 1e-6, "a master to work 1.5/1.01 times as fast, got " + master / novice);
            helper.succeed();
        });
    }

    /** A staffed library with books makes the whole colony learn faster. */
    @GameTest(environment = "hearthstead-gametest:library", structure = PLATFORM, maxTicks = 400)
    public void libraryBoostsLearning(GameTestHelper helper) {
        Colony colony = TestColonies.found(helper, new BlockPos(4, 1, 27));
        BuildingData library = TestColonies.hut(helper, colony, new BlockPos(14, 1, 14), BuildingType.LIBRARY, 2);
        check(helper, colony.skillGainMultiplier() == 1, "no bonus before the library is open");
        TestColonies.give(helper, library, Items.BOOK, 2);
        colony.addNewCitizen(helper.getLevel());
        helper.succeedWhen(() -> check(helper, colony.skillGainMultiplier() == 1.5,
                "a level 2 library to make citizens learn 50% faster, got " + colony.skillGainMultiplier()));
    }

    /** Guards train Combat at the combat academy. */
    @GameTest(environment = "hearthstead-gametest:combatacademy", structure = PLATFORM, maxTicks = 3600, skyAccess = true)
    public void guardsTrainAtTheAcademy(GameTestHelper helper) {
        Colony colony = TestColonies.found(helper, new BlockPos(4, 1, 27));
        TestColonies.hut(helper, colony, new BlockPos(14, 1, 14), BuildingType.GUARD_TOWER, 1);
        TestColonies.hut(helper, colony, new BlockPos(4, 1, 4), BuildingType.COMBAT_ACADEMY, 1);
        CitizenData guard = colony.addNewCitizen(helper.getLevel());
        guard.setSkill(Skill.COMBAT, 1);
        helper.succeedWhen(() -> check(helper, guard.skill(Skill.COMBAT) >= 2,
                "the guard to train Combat (status: " + guard.status() + ")"));
    }

    /** A guard who trained at the archery shoots arrows at a monster out of sword reach. */
    @GameTest(environment = "hearthstead-gametest:marksman", structure = PLATFORM, maxTicks = 600, skyAccess = true)
    public void trainedGuardsShootArrows(GameTestHelper helper) {
        helper.getLevel().getServer().setDifficulty(Difficulty.NORMAL, true);
        Colony colony = TestColonies.found(helper, new BlockPos(4, 1, 27));
        BuildingData tower = TestColonies.hut(helper, colony, new BlockPos(14, 1, 14), BuildingType.GUARD_TOWER, 1);
        CitizenData guard = colony.addNewCitizen(helper.getLevel());
        guard.setSkill(Skill.MARKSMANSHIP, 20);
        helper.runAfterDelay(20, () -> {
            CitizenEntity entity = (CitizenEntity) helper.getLevel().getEntity(guard.entityUuid());
            check(helper, guard.job() == JobType.GUARD && tower.pos().equals(guard.workPos()), "the citizen to guard the tower");
            entity.inventory().addItem(new ItemStack(Items.ARROW, 16));
            // A husk does not burn by day; spawned 9 blocks away, out of sword reach.
            var zombie = EntityType.HUSK.spawn(helper.getLevel(), entity.blockPosition().offset(9, 0, 0),
                    net.minecraft.world.entity.EntitySpawnReason.COMMAND);
            zombie.setNoAi(true);
            entity.setTarget(zombie);
            helper.succeedWhen(() -> check(helper, entity.inventory().countItem(Items.ARROW) < 16,
                    "the guard to shoot arrows (" + guard.status() + ")"));
        });
    }

    private static void check(GameTestHelper helper, boolean condition, String expected) {
        if (!condition) {
            throw helper.assertionException("Expected " + expected);
        }
    }
}
