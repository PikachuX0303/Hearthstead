package dev.pikachux.hearthstead.test;

import dev.pikachux.hearthstead.colony.Colony;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.npc.wanderingtrader.WanderingTrader;
import net.minecraft.world.phys.AABB;

public class EventGameTests {
    private static final String PLATFORM = "hearthstead-gametest:platform";

    /** A festival runs until nightfall, and citizens are happier while it lasts. */
    @GameTest(environment = "hearthstead-gametest:festival", structure = PLATFORM, maxTicks = 400, skyAccess = true)
    public void festivalMakesCitizensHappier(GameTestHelper helper) {
        Colony colony = TestColonies.found(helper, new BlockPos(16, 1, 16));
        var level = helper.getLevel();
        var citizen = colony.addNewCitizen(level);
        long now = level.getGameTime();
        if (colony.happenings().festival(now)) {
            throw helper.assertionException("No festival before one starts");
        }
        helper.runAfterDelay(60, () -> {
            double before = citizen.happiness();
            colony.happenings().startFestival(level.getServer(), colony, level);
            if (!colony.happenings().festival(level.getGameTime())) {
                throw helper.assertionException("The festival should be running");
            }
            helper.succeedWhen(() -> {
                if (citizen.happiness() < Math.min(10, before + 1.5)) {
                    throw helper.assertionException("Happiness " + citizen.happiness() + " did not rise from " + before);
                }
            });
        });
    }

    /** A wandering merchant arrives next to the town hall and stays for a day. */
    @GameTest(environment = "hearthstead-gametest:merchant", structure = PLATFORM, maxTicks = 40, skyAccess = true)
    public void merchantVisitsTheTownHall(GameTestHelper helper) {
        Colony colony = TestColonies.found(helper, new BlockPos(16, 1, 16));
        var level = helper.getLevel();
        if (!colony.happenings().sendMerchant(level.getServer(), colony, level)) {
            throw helper.assertionException("The merchant did not come");
        }
        var traders = level.getEntitiesOfClass(WanderingTrader.class, new AABB(colony.center()).inflate(10));
        if (traders.size() != 1 || traders.getFirst().getDespawnDelay() < 20000) {
            throw helper.assertionException("Expected one merchant staying a day, found " + traders.size());
        }
        traders.forEach(trader -> trader.discard());
        helper.succeed();
    }
}
