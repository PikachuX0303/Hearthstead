package dev.pikachux.hearthstead.test;

import dev.pikachux.hearthstead.colony.BuildingData;
import dev.pikachux.hearthstead.colony.BuildingType;
import dev.pikachux.hearthstead.colony.CitizenData;
import dev.pikachux.hearthstead.colony.Colony;
import dev.pikachux.hearthstead.colony.JobType;
import dev.pikachux.hearthstead.colony.Research;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.Items;

public class ResearchGameTests {
    private static final String PLATFORM = "hearthstead-gametest:platform";

    /**
     * Starting a research takes its materials, a researcher makes progress, and a finished
     * research unlocks the next tier (once the university is high enough).
     */
    @GameTest(environment = "hearthstead-gametest:research", structure = PLATFORM, maxTicks = 1200, skyAccess = true)
    public void researchProgresses(GameTestHelper helper) {
        Colony colony = TestColonies.found(helper, new BlockPos(4, 1, 27));
        BuildingData university = TestColonies.hut(helper, colony, new BlockPos(14, 1, 14), BuildingType.UNIVERSITY, 1);
        var level = helper.getLevel();

        String blocked = colony.research().start(level, colony, university, Research.SWIFT_CONSTRUCTION);
        if (!"gui.hearthstead.research.needs_parent".equals(blocked)) {
            throw helper.assertionException("Tier 2 research should need its parent, got " + blocked);
        }
        String missing = colony.research().start(level, colony, university, Research.APPRENTICESHIP);
        if (!"gui.hearthstead.research.missing_items".equals(missing)) {
            throw helper.assertionException("Research should need paper, got " + missing);
        }
        TestColonies.give(helper, university, Items.PAPER, 10);
        String started = colony.research().start(level, colony, university, Research.APPRENTICESHIP);
        if (started != null || colony.research().active() != Research.APPRENTICESHIP) {
            throw helper.assertionException("Research did not start: " + started);
        }
        if (TestColonies.count(helper, university, Items.PAPER) != 2) {
            throw helper.assertionException("Starting the research should take 8 paper");
        }

        CitizenData researcher = colony.addNewCitizen(level);
        helper.succeedWhen(() -> {
            if (researcher.job() != JobType.RESEARCHER || colony.research().progress() <= 0) {
                throw helper.assertionException("No research progress yet (" + researcher.status() + ")");
            }
            colony.research().addProgress(level.getServer(), colony, Research.APPRENTICESHIP.points());
            if (!colony.research().has(Research.APPRENTICESHIP) || colony.research().active() != null) {
                throw helper.assertionException("Research did not complete");
            }
            String next = colony.research().blocker(colony, Research.SWIFT_CONSTRUCTION);
            if (!"gui.hearthstead.research.needs_university".equals(next)) {
                throw helper.assertionException("Tier 2 should now only need a level 2 university, got " + next);
            }
        });
    }
}
