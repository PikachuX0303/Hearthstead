package dev.pikachux.hearthstead.test;

import dev.pikachux.hearthstead.block.HutBlockEntity;
import dev.pikachux.hearthstead.colony.BuildingData;
import dev.pikachux.hearthstead.colony.BuildingType;
import dev.pikachux.hearthstead.colony.CitizenData;
import dev.pikachux.hearthstead.colony.Colony;
import dev.pikachux.hearthstead.colony.Illness;
import dev.pikachux.hearthstead.colony.JobType;
import dev.pikachux.hearthstead.colony.Logistics;
import dev.pikachux.hearthstead.config.HearthsteadConfig;
import dev.pikachux.hearthstead.entity.CitizenEntity;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public class IllnessGameTests {
    private static final String PLATFORM = "hearthstead-gametest:platform";

    /** Illness is saved with the citizen. */
    @GameTest
    public void illnessIsSaved(GameTestHelper helper) {
        CitizenData data = CitizenData.create(1, helper.getLevel().getRandom());
        data.fallIll(Illness.FEVER, 12);
        CitizenData copy = CitizenData.load(data.save());
        check(helper, copy.illness() == Illness.FEVER && copy.illnessDay() == 12, "the fever to be saved");
        copy.cure();
        check(helper, CitizenData.load(copy.save()).illness() == null, "a cured citizen to stay healthy");
        helper.succeed();
    }

    /** Sick citizens stop working, get better by themselves after some days, and pass illness on at home. */
    @GameTest(environment = "hearthstead-gametest:illness", structure = PLATFORM, maxTicks = 600)
    public void sickCitizensRest(GameTestHelper helper) {
        var level = helper.getLevel();
        Colony colony = TestColonies.found(helper, new BlockPos(4, 1, 27));
        TestColonies.hut(helper, colony, new BlockPos(14, 1, 14), BuildingType.LUMBERJACK, 1);
        CitizenData worker = colony.addNewCitizen(level);
        double illness = HearthsteadConfig.ILLNESS_CHANCE.get();
        double contagion = HearthsteadConfig.CONTAGION_CHANCE.get();
        helper.runAfterDelay(40, () -> {
            check(helper, worker.job() == JobType.LUMBERJACK, "a lumberjack");
            colony.fallIll(level, worker, Illness.COLD, 5);
            helper.runAfterDelay(60, () -> {
                CitizenEntity entity = (CitizenEntity) level.getEntity(worker.entityUuid());
                check(helper, !entity.isWorking() && worker.status().startsWith("Ill with cold"),
                        "the sick lumberjack to rest (" + worker.status() + ")");
                try {
                    HearthsteadConfig.ILLNESS_CHANCE.set(0.0);
                    HearthsteadConfig.CONTAGION_CHANCE.set(1.0);
                    CitizenData housemate = colony.addNewCitizen(level);
                    CitizenData neighbour = colony.addNewCitizen(level);
                    worker.setHomePos(BlockPos.ZERO);
                    housemate.setHomePos(BlockPos.ZERO);
                    neighbour.setHomePos(BlockPos.ZERO.above());
                    colony.dailyIllness(level, 6);
                    check(helper, housemate.illness() == Illness.COLD && !neighbour.sick(),
                            "the cold to spread to the housemate only");
                    HearthsteadConfig.CONTAGION_CHANCE.set(0.0);
                    colony.dailyIllness(level, 5 + Illness.COLD.recoveryDays());
                    check(helper, !worker.sick(), "the cold to pass after " + Illness.COLD.recoveryDays() + " days");
                } finally {
                    HearthsteadConfig.ILLNESS_CHANCE.set(illness);
                    HearthsteadConfig.CONTAGION_CHANCE.set(contagion);
                }
                helper.succeed();
            });
        });
    }

    /** A healer cures a sick citizen at the hospital with the right cure, which the hospital asks for. */
    @GameTest(environment = "hearthstead-gametest:cure", structure = PLATFORM, maxTicks = 1200)
    public void healersCureIllness(GameTestHelper helper) {
        var level = helper.getLevel();
        Colony colony = TestColonies.found(helper, new BlockPos(4, 1, 27));
        BuildingData hospital = TestColonies.hut(helper, colony, new BlockPos(14, 1, 14), BuildingType.HOSPITAL, 1);
        CitizenData healer = colony.addNewCitizen(level);
        CitizenData patient = colony.addNewCitizen(level);
        helper.runAfterDelay(20, () -> {
            check(helper, healer.job() == JobType.HEALER, "a healer");
            colony.fallIll(level, patient, Illness.STOMACH_BUG, 0);
            check(helper, Logistics.requests(level, colony, hospital).stream()
                    .anyMatch(r -> r.matcher().test(new ItemStack(Items.MILK_BUCKET))), "the hospital to ask for milk");
            ((HutBlockEntity) level.getBlockEntity(hospital.pos())).setItem(0, new ItemStack(Items.MILK_BUCKET));
            helper.succeedWhen(() -> {
                check(helper, !patient.sick(), "the healer to cure the stomach bug (" + patient.status() + " / "
                        + healer.status() + ")");
                check(helper, TestColonies.count(helper, hospital, Items.BUCKET) == 1, "the empty bucket back in the hospital");
            });
        });
    }

    private static void check(GameTestHelper helper, boolean condition, String expected) {
        if (!condition) {
            throw helper.assertionException("Expected " + expected);
        }
    }
}
