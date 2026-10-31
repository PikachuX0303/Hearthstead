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

    /**
     * The tree is well formed: 24 nodes, each child one tier above its parent in the same branch
     * and listed after it, and every branch reaching tier 5.
     */
    @GameTest
    public void researchTreeIsConsistent(GameTestHelper helper) {
        Research[] all = Research.values();
        if (all.length != 24) {
            throw helper.assertionException("Expected 24 research nodes, got " + all.length);
        }
        for (Research research : all) {
            Research parent = research.parent();
            if (research.tier() < 1 || research.tier() > 5) {
                throw helper.assertionException(research + " has tier " + research.tier());
            }
            if (parent == null ? research.tier() != 1
                    : parent.branch() != research.branch() || parent.tier() != research.tier() - 1 || parent.ordinal() > research.ordinal()) {
                throw helper.assertionException(research + " does not follow its parent " + parent);
            }
        }
        for (Research.Branch branch : Research.Branch.values()) {
            if (java.util.Arrays.stream(all).noneMatch(r -> r.branch() == branch && r.tier() == 5)) {
                throw helper.assertionException("The " + branch + " branch has no tier 5 research");
            }
        }
        helper.succeed();
    }

    /** Housing and guard training follow the finished research. */
    @GameTest(structure = PLATFORM)
    public void researchEffectsApply(GameTestHelper helper) {
        Colony colony = TestColonies.found(helper, new BlockPos(4, 1, 27));
        BuildingData home = TestColonies.hut(helper, colony, new BlockPos(14, 1, 14), BuildingType.RESIDENCE, 1);
        int base = colony.housing(home);
        colony.research().complete(Research.COMFORTABLE_HOMES, colony);
        colony.research().complete(Research.SPACIOUS_HOMES, colony);
        if (colony.housing(home) != base + 2) {
            throw helper.assertionException("Expected 2 more residents, got " + colony.housing(home) + " from " + base);
        }
        var research = colony.research();
        if (dev.pikachux.hearthstead.entity.CitizenEntity.guardArmor(research) != 6.0
                || dev.pikachux.hearthstead.entity.CitizenEntity.guardHealth(research) != 20.0) {
            throw helper.assertionException("Untrained guards should have 6 armour and 20 extra health");
        }
        research.complete(Research.CHAINMAIL_TRAINING, colony);
        research.complete(Research.PLATE_ARMOR, colony);
        research.complete(Research.FIRST_AID, colony);
        if (dev.pikachux.hearthstead.entity.CitizenEntity.guardArmor(research) != 16.0
                || dev.pikachux.hearthstead.entity.CitizenEntity.guardHealth(research) != 30.0) {
            throw helper.assertionException("Trained guards should have 16 armour and 30 extra health");
        }
        helper.succeed();
    }
}
