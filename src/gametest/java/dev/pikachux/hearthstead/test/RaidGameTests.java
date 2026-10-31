package dev.pikachux.hearthstead.test;

import dev.pikachux.hearthstead.colony.BuildingData;
import dev.pikachux.hearthstead.colony.BuildingType;
import dev.pikachux.hearthstead.colony.CitizenData;
import dev.pikachux.hearthstead.colony.Colony;
import dev.pikachux.hearthstead.colony.JobType;
import dev.pikachux.hearthstead.entity.raider.AbstractBarbarian;
import dev.pikachux.hearthstead.entity.raider.BarbarianEntity;
import dev.pikachux.hearthstead.registry.ModEntities;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;

import java.util.List;

public class RaidGameTests {
    private static final String PLATFORM = "hearthstead-gametest:platform";

    private static void prepareWorld(GameTestHelper helper, BlockPos center) {
        ServerLevel level = helper.getLevel();
        level.getServer().setDifficulty(Difficulty.NORMAL, true);
        ChunkPos chunk = new ChunkPos(center);
        for (int x = -5; x <= 5; x++) {
            for (int z = -5; z <= 5; z++) {
                level.setChunkForced(chunk.x + x, chunk.z + z, true);
            }
        }
    }

    /** A raid spawns barbarians at the colony edge and ends once all of them are dead. */
    @GameTest(environment = "hearthstead-gametest:raid", structure = PLATFORM, maxTicks = 400, skyAccess = true)
    public void raidStartsAndEnds(GameTestHelper helper) {
        Colony colony = TestColonies.found(helper, new BlockPos(16, 1, 16));
        prepareWorld(helper, colony.center());
        ServerLevel level = helper.getLevel();
        for (int i = 0; i < 6; i++) {
            colony.addNewCitizen(level);
        }
        if (!colony.raid().start(level.getServer(), colony, level)) {
            throw helper.assertionException("Raid did not start");
        }
        int size = colony.raid().total();
        if (size < 3 || !colony.raid().active()) {
            throw helper.assertionException("Expected at least 3 raiders, got " + size);
        }
        helper.runAfterDelay(20, () -> {
            List<AbstractBarbarian> raiders = level.getEntitiesOfClass(AbstractBarbarian.class,
                    new net.minecraft.world.phys.AABB(colony.center()).inflate(80));
            if (raiders.size() != size) {
                throw helper.assertionException("Expected " + size + " raiders in the world, found " + raiders.size());
            }
            raiders.forEach(raider -> raider.kill(level));
        });
        helper.succeedWhen(() -> {
            if (colony.raid().active()) {
                throw helper.assertionException("Raid still active with " + colony.raid().remaining() + " raiders");
            }
        });
    }

    /** A guard with a sword fights off a barbarian near its tower. */
    @GameTest(environment = "hearthstead-gametest:guard", structure = PLATFORM, maxTicks = 2400, skyAccess = true, maxAttempts = 3)
    public void guardDefeatsBarbarian(GameTestHelper helper) {
        Colony colony = TestColonies.found(helper, new BlockPos(4, 1, 27));
        prepareWorld(helper, colony.center());
        BuildingData tower = TestColonies.hut(helper, colony, new BlockPos(16, 1, 16), BuildingType.GUARD_TOWER, 1);
        TestColonies.give(helper, tower, Items.IRON_SWORD, 1);
        ServerLevel level = helper.getLevel();
        CitizenData guard = colony.addNewCitizen(level);
        BarbarianEntity[] barbarian = new BarbarianEntity[1];
        helper.runAfterDelay(200, () -> {
            if (guard.job() != JobType.GUARD) {
                throw helper.assertionException("Citizen did not become a guard");
            }
            BlockPos spawn = helper.absolutePos(new BlockPos(26, 1, 16));
            barbarian[0] = ModEntities.BARBARIAN.create(level, EntitySpawnReason.EVENT);
            barbarian[0].snapTo(spawn.getX() + 0.5, spawn.getY(), spawn.getZ() + 0.5, 0, 0);
            barbarian[0].finalizeSpawn(level, level.getCurrentDifficultyAt(spawn), EntitySpawnReason.EVENT, null);
            barbarian[0].setColony(colony);
            level.addFreshEntity(barbarian[0]);
        });
        helper.succeedWhen(() -> {
            if (barbarian[0] == null || barbarian[0].isAlive()) {
                throw helper.assertionException("Barbarian still alive (guard: " + guard.status() + ")");
            }
            if (colony.citizen(guard.id()) == null) {
                throw helper.assertionException("The guard died");
            }
        });
    }
}
