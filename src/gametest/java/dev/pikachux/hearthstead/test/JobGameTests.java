package dev.pikachux.hearthstead.test;

import dev.pikachux.hearthstead.colony.BuildingData;
import dev.pikachux.hearthstead.colony.BuildingType;
import dev.pikachux.hearthstead.colony.CitizenData;
import dev.pikachux.hearthstead.colony.Colony;
import dev.pikachux.hearthstead.colony.Storage;
import dev.pikachux.hearthstead.colony.Woodworking;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

public class JobGameTests {
    private static final String PLATFORM = "hearthstead-gametest:platform";

    /** The cook loads the smoker with raw food and coal and stores the meal in the hut. */
    @GameTest(environment = "hearthstead-gametest:cook", structure = PLATFORM, maxTicks = 4000, skyAccess = true)
    public void cookCooksFood(GameTestHelper helper) {
        Colony colony = TestColonies.found(helper, new BlockPos(4, 1, 27));
        BuildingData cook = TestColonies.hut(helper, colony, new BlockPos(14, 1, 14), BuildingType.COOK, 1);
        TestColonies.give(helper, cook, Items.BEEF, 4);
        TestColonies.give(helper, cook, Items.COAL, 2);
        CitizenData citizen = colony.addNewCitizen(helper.getLevel());
        helper.succeedWhen(() -> {
            if (TestColonies.count(helper, cook, Items.COOKED_BEEF) == 0) {
                throw helper.assertionException("No cooked beef yet (" + citizen.status() + ")");
            }
        });
    }

    /** The fisherman fishes in a nearby pond and brings the catch to the hut. */
    @GameTest(environment = "hearthstead-gametest:fisherman", structure = PLATFORM, maxTicks = 6000, skyAccess = true)
    public void fishermanCatchesFish(GameTestHelper helper) {
        Colony colony = TestColonies.found(helper, new BlockPos(4, 1, 27));
        BuildingData fisher = TestColonies.hut(helper, colony, new BlockPos(8, 1, 8), BuildingType.FISHERMAN, 1);
        // A pond with a stone rim so the water cannot spread onto the shore.
        for (BlockPos pos : BlockPos.betweenClosed(15, 0, 3, 27, 0, 15)) {
            boolean rim = pos.getX() == 15 || pos.getX() == 27 || pos.getZ() == 3 || pos.getZ() == 15;
            helper.setBlock(pos, rim ? Blocks.STONE : Blocks.WATER);
        }
        TestColonies.give(helper, fisher, Items.FISHING_ROD, 1);
        CitizenData citizen = colony.addNewCitizen(helper.getLevel());
        helper.succeedWhen(() -> {
            int caught = Storage.count(Storage.containers(helper.getLevel(), fisher), s -> !s.is(Items.FISHING_ROD));
            if (caught == 0) {
                throw helper.assertionException("Nothing caught yet (" + citizen.status() + ")");
            }
        });
    }

    /** The deliveryman brings food from the warehouse home and empties a worker hut into it. */
    @GameTest(environment = "hearthstead-gametest:deliveryman", structure = PLATFORM, maxTicks = 4000, skyAccess = true)
    public void deliverymanMovesItems(GameTestHelper helper) {
        Colony colony = TestColonies.found(helper, new BlockPos(4, 1, 4));
        BuildingData townHall = colony.building(helper.absolutePos(new BlockPos(4, 1, 4)));
        BuildingData warehouse = TestColonies.hut(helper, colony, new BlockPos(14, 1, 4), BuildingType.WAREHOUSE, 1);
        TestColonies.hut(helper, colony, new BlockPos(24, 1, 4), BuildingType.DELIVERYMAN, 1);
        BuildingData lumberjack = TestColonies.hut(helper, colony, new BlockPos(4, 1, 16), BuildingType.LUMBERJACK, 1);
        TestColonies.give(helper, warehouse, Items.BREAD, 16);
        TestColonies.give(helper, lumberjack, Items.OAK_LOG, 20);
        CitizenData citizen = colony.addNewCitizen(helper.getLevel());
        helper.succeedWhen(() -> {
            String state = " (" + citizen.job().displayName() + ": " + citizen.status() + ")";
            if (TestColonies.count(helper, townHall, Items.BREAD) == 0) {
                throw helper.assertionException("No bread delivered to the town hall" + state);
            }
            if (TestColonies.count(helper, lumberjack, Items.OAK_LOG) > 0 || TestColonies.count(helper, warehouse, Items.OAK_LOG) != 20) {
                throw helper.assertionException("Logs not moved to the warehouse" + state);
            }
        });
    }

    /** The baker turns wheat from the bakery into bread. */
    @GameTest(environment = "hearthstead-gametest:baker", structure = PLATFORM, maxTicks = 3000, skyAccess = true)
    public void bakerBakesBread(GameTestHelper helper) {
        Colony colony = TestColonies.found(helper, new BlockPos(4, 1, 27));
        BuildingData bakery = TestColonies.hut(helper, colony, new BlockPos(14, 1, 14), BuildingType.BAKER, 1);
        TestColonies.give(helper, bakery, Items.WHEAT, 9);
        CitizenData citizen = colony.addNewCitizen(helper.getLevel());
        helper.succeedWhen(() -> {
            if (TestColonies.count(helper, bakery, Items.BREAD) == 0) {
                throw helper.assertionException("No bread yet (" + citizen.status() + ")");
            }
        });
    }

    /** The smelter loads the smeltery's furnace with raw iron and coal and collects the ingots. */
    @GameTest(environment = "hearthstead-gametest:smelter", structure = PLATFORM, maxTicks = 4000, skyAccess = true)
    public void smelterSmeltsOre(GameTestHelper helper) {
        Colony colony = TestColonies.found(helper, new BlockPos(4, 1, 27));
        BuildingData smeltery = TestColonies.hut(helper, colony, new BlockPos(14, 1, 14), BuildingType.SMELTER, 1);
        TestColonies.give(helper, smeltery, Items.RAW_IRON, 3);
        TestColonies.give(helper, smeltery, Items.COAL, 2);
        CitizenData citizen = colony.addNewCitizen(helper.getLevel());
        helper.succeedWhen(() -> {
            if (TestColonies.count(helper, smeltery, Items.IRON_INGOT) == 0) {
                throw helper.assertionException("No iron ingot yet (" + citizen.status() + ")");
            }
        });
    }

    /** The shepherd shears a sheep near the hut and brings the wool home. */
    @GameTest(environment = "hearthstead-gametest:shepherd", structure = PLATFORM, maxTicks = 3000, skyAccess = true, maxAttempts = 3)
    public void shepherdShearsSheep(GameTestHelper helper) {
        Colony colony = TestColonies.found(helper, new BlockPos(4, 1, 27));
        BuildingData hut = TestColonies.hut(helper, colony, new BlockPos(14, 1, 14), BuildingType.SHEPHERD, 1);
        TestColonies.give(helper, hut, Items.SHEARS, 1);
        // A small fenced pen with a closed gate next to the hut, like a player would build.
        for (BlockPos pos : BlockPos.betweenClosed(20, 1, 20, 26, 1, 26)) {
            if (pos.getX() == 20 || pos.getX() == 26 || pos.getZ() == 20 || pos.getZ() == 26) {
                helper.setBlock(pos, Blocks.OAK_FENCE);
            }
        }
        helper.setBlock(new BlockPos(20, 1, 23), Blocks.OAK_FENCE_GATE.defaultBlockState()
                .setValue(net.minecraft.world.level.block.FenceGateBlock.FACING, net.minecraft.core.Direction.EAST));
        helper.spawn(EntityType.SHEEP, new BlockPos(23, 1, 23));
        CitizenData citizen = colony.addNewCitizen(helper.getLevel());
        helper.succeedWhen(() -> {
            int wool = Storage.count(Storage.containers(helper.getLevel(), hut), s -> s.is(ItemTags.WOOL));
            if (wool == 0) {
                throw helper.assertionException("No wool yet (" + citizen.status() + ")");
            }
        });
    }

    /** The composter turns plant waste into bone meal in the building's composters. */
    @GameTest(environment = "hearthstead-gametest:composter", structure = PLATFORM, maxTicks = 4000, skyAccess = true)
    public void composterMakesBoneMeal(GameTestHelper helper) {
        Colony colony = TestColonies.found(helper, new BlockPos(4, 1, 27));
        BuildingData composter = TestColonies.hut(helper, colony, new BlockPos(14, 1, 14), BuildingType.COMPOSTER, 1);
        TestColonies.give(helper, composter, Items.OAK_LEAVES, 64);
        TestColonies.give(helper, composter, Items.OAK_SAPLING, 64);
        CitizenData citizen = colony.addNewCitizen(helper.getLevel());
        helper.succeedWhen(() -> {
            if (TestColonies.count(helper, composter, Items.BONE_MEAL) == 0) {
                throw helper.assertionException("No bone meal yet (" + citizen.status() + ")");
            }
        });
    }

    /** Wooden building parts are crafted from logs with the game's own recipes. */
    @GameTest(environment = "hearthstead-gametest:woodworking", structure = PLATFORM)
    public void woodworkingCraftsFromLogs(GameTestHelper helper) {
        Colony colony = TestColonies.found(helper, new BlockPos(4, 1, 27));
        BuildingData sawmill = TestColonies.hut(helper, colony, new BlockPos(14, 1, 14), BuildingType.SAWMILL, 1);
        TestColonies.give(helper, sawmill, Items.SPRUCE_LOG, 4);
        var storage = Storage.containers(helper.getLevel(), sawmill);
        ItemStack stairs = Woodworking.craft(helper.getLevel(), storage, Items.SPRUCE_STAIRS);
        if (!stairs.is(Items.SPRUCE_STAIRS) || TestColonies.count(helper, sawmill, Items.SPRUCE_STAIRS) != stairs.getCount()) {
            throw helper.assertionException("Expected spruce stairs, got " + stairs);
        }
        ItemStack fence = Woodworking.craft(helper.getLevel(), storage, Items.SPRUCE_FENCE);
        if (!fence.is(Items.SPRUCE_FENCE)) {
            throw helper.assertionException("Expected a spruce fence, got " + fence);
        }
        if (!Woodworking.craft(helper.getLevel(), storage, Items.OAK_DOOR).isEmpty()) {
            throw helper.assertionException("An oak door cannot be made from spruce");
        }
        helper.succeed();
    }

    /** The sawmill worker keeps the colony stocked with planks. */
    @GameTest(environment = "hearthstead-gametest:sawmill", structure = PLATFORM, maxTicks = 2000, skyAccess = true)
    public void sawmillSawsPlanks(GameTestHelper helper) {
        Colony colony = TestColonies.found(helper, new BlockPos(4, 1, 27));
        BuildingData sawmill = TestColonies.hut(helper, colony, new BlockPos(14, 1, 14), BuildingType.SAWMILL, 1);
        TestColonies.give(helper, sawmill, Items.BIRCH_LOG, 4);
        CitizenData citizen = colony.addNewCitizen(helper.getLevel());
        helper.succeedWhen(() -> {
            if (TestColonies.count(helper, sawmill, Items.BIRCH_PLANKS) == 0) {
                throw helper.assertionException("No planks yet (" + citizen.status() + ")");
            }
        });
    }

    /** The cowhand milks a cow near the hut into a bucket. */
    @GameTest(environment = "hearthstead-gametest:cowhand", structure = PLATFORM, maxTicks = 3000, skyAccess = true, maxAttempts = 3)
    public void cowhandMilksCow(GameTestHelper helper) {
        Colony colony = TestColonies.found(helper, new BlockPos(4, 1, 27));
        BuildingData hut = TestColonies.hut(helper, colony, new BlockPos(14, 1, 14), BuildingType.COWHAND, 1);
        TestColonies.give(helper, hut, Items.BUCKET, 2);
        for (BlockPos pos : BlockPos.betweenClosed(20, 1, 20, 26, 1, 26)) {
            if (pos.getX() == 20 || pos.getX() == 26 || pos.getZ() == 20 || pos.getZ() == 26) {
                helper.setBlock(pos, Blocks.OAK_FENCE);
            }
        }
        helper.setBlock(new BlockPos(20, 1, 23), Blocks.OAK_FENCE_GATE.defaultBlockState()
                .setValue(net.minecraft.world.level.block.FenceGateBlock.FACING, net.minecraft.core.Direction.EAST));
        helper.spawn(EntityType.COW, new BlockPos(23, 1, 23));
        CitizenData citizen = colony.addNewCitizen(helper.getLevel());
        helper.succeedWhen(() -> {
            if (TestColonies.count(helper, hut, Items.MILK_BUCKET) == 0) {
                throw helper.assertionException("No milk yet (" + citizen.status() + ")");
            }
        });
    }

    /** With nothing else to do, the farmer speeds up growing crops with bone meal. */
    @GameTest(environment = "hearthstead-gametest:farmer", structure = PLATFORM, maxTicks = 2000, skyAccess = true)
    public void farmerUsesBoneMeal(GameTestHelper helper) {
        Colony colony = TestColonies.found(helper, new BlockPos(4, 1, 27));
        BuildingData farm = TestColonies.hut(helper, colony, new BlockPos(14, 1, 14), BuildingType.FARMER, 1);
        TestColonies.give(helper, farm, Items.IRON_HOE, 1);
        TestColonies.give(helper, farm, Items.BONE_MEAL, 8);
        // One young wheat plant in the field band just outside the farm building.
        BlockPos crop = new BlockPos(14, 1, 19);
        helper.setBlock(crop.below(), Blocks.FARMLAND);
        helper.setBlock(crop, Blocks.WHEAT);
        CitizenData citizen = colony.addNewCitizen(helper.getLevel());
        helper.succeedWhen(() -> {
            var state = helper.getBlockState(crop);
            boolean grown = state.is(Blocks.WHEAT) && state.getValue(net.minecraft.world.level.block.CropBlock.AGE) > 0;
            if (!grown && TestColonies.count(helper, farm, Items.BONE_MEAL) == 8) {
                throw helper.assertionException("Bone meal not used yet (" + citizen.status() + ")");
            }
        });
    }

    /** A fenced pen next to the hut. Herding tests may retry: animals move at random. */
    private static void pen(GameTestHelper helper) {
        for (BlockPos pos : BlockPos.betweenClosed(20, 1, 20, 26, 1, 26)) {
            if (pos.getX() == 20 || pos.getX() == 26 || pos.getZ() == 20 || pos.getZ() == 26) {
                helper.setBlock(pos, Blocks.OAK_FENCE);
            }
        }
    }

    /** The chicken herder gathers eggs lying next to the chickens. */
    @GameTest(environment = "hearthstead-gametest:chickenherder", structure = PLATFORM, maxTicks = 3000, skyAccess = true, maxAttempts = 3)
    public void chickenHerderGathersEggs(GameTestHelper helper) {
        Colony colony = TestColonies.found(helper, new BlockPos(4, 1, 27));
        BuildingData hut = TestColonies.hut(helper, colony, new BlockPos(14, 1, 14), BuildingType.CHICKEN_HERDER, 1);
        pen(helper);
        helper.spawn(EntityType.CHICKEN, new BlockPos(23, 1, 23));
        helper.spawnItem(Items.EGG, new BlockPos(23, 1, 23));
        CitizenData citizen = colony.addNewCitizen(helper.getLevel());
        helper.succeedWhen(() -> {
            if (TestColonies.count(helper, hut, Items.EGG) == 0) {
                throw helper.assertionException("No eggs yet (" + citizen.status() + ")");
            }
        });
    }

    /** With a full herd, the swineherd butchers a pig for pork. */
    @GameTest(environment = "hearthstead-gametest:swineherd", structure = PLATFORM, maxTicks = 3000, skyAccess = true, maxAttempts = 3)
    public void swineherdButchersFullHerd(GameTestHelper helper) {
        Colony colony = TestColonies.found(helper, new BlockPos(4, 1, 27));
        BuildingData hut = TestColonies.hut(helper, colony, new BlockPos(14, 1, 14), BuildingType.SWINEHERD, 1);
        TestColonies.give(helper, hut, Items.IRON_SWORD, 1);
        pen(helper);
        // More pigs than a level 1 hut keeps, so the herd is over its limit.
        for (int i = 0; i < 6; i++) {
            helper.spawn(EntityType.PIG, new BlockPos(22 + i % 3 * 2, 1, 22 + i / 3 * 2));
        }
        CitizenData citizen = colony.addNewCitizen(helper.getLevel());
        helper.succeedWhen(() -> {
            if (TestColonies.count(helper, hut, Items.PORKCHOP) == 0) {
                var level = helper.getLevel();
                var pigs = level.getEntitiesOfClass(net.minecraft.world.entity.animal.pig.Pig.class,
                        new net.minecraft.world.phys.AABB(hut.pos()).inflate(40));
                var entity = level.getEntity(citizen.entityUuid());
                throw helper.assertionException("No pork yet (" + citizen.status() + ") pigs " + pigs.stream()
                        .map(p -> p.blockPosition().toShortString() + (p.isBaby() ? "b" : "")).toList() + " hut " + hut.pos()
                        + " herder " + (entity == null ? "?" : entity.blockPosition().toShortString() + " "
                        + ((net.minecraft.world.entity.LivingEntity) entity).getMainHandItem()));
            }
        });
    }

    /** Stone parts are cut from stone with the game's stonecutter recipes. */
    @GameTest(environment = "hearthstead-gametest:stonemason", structure = PLATFORM)
    public void masonryCutsStone(GameTestHelper helper) {
        Colony colony = TestColonies.found(helper, new BlockPos(4, 1, 27));
        BuildingData hut = TestColonies.hut(helper, colony, new BlockPos(14, 1, 14), BuildingType.STONEMASON, 1);
        TestColonies.give(helper, hut, Items.STONE, 4);
        TestColonies.give(helper, hut, Items.SANDSTONE, 2);
        var storage = Storage.containers(helper.getLevel(), hut);
        ItemStack stairs = dev.pikachux.hearthstead.colony.Masonry.craft(helper.getLevel(), storage, Items.STONE_BRICK_STAIRS);
        ItemStack slabs = dev.pikachux.hearthstead.colony.Masonry.craft(helper.getLevel(), storage, Items.CUT_SANDSTONE);
        if (!stairs.is(Items.STONE_BRICK_STAIRS) || !slabs.is(Items.CUT_SANDSTONE)) {
            throw helper.assertionException("Expected stone brick stairs and cut sandstone, got " + stairs + " and " + slabs);
        }
        if (TestColonies.count(helper, hut, Items.STONE) != 3 || TestColonies.count(helper, hut, Items.SANDSTONE) != 1) {
            throw helper.assertionException("Each cut should use one block");
        }
        if (!dev.pikachux.hearthstead.colony.Masonry.craft(helper.getLevel(), storage, Items.OAK_PLANKS).isEmpty()) {
            throw helper.assertionException("Planks cannot be cut from stone");
        }
        helper.succeed();
    }

    /** The florist picks the flowers growing around its building. */
    @GameTest(environment = "hearthstead-gametest:florist", structure = PLATFORM, maxTicks = 3000, skyAccess = true)
    public void floristPicksFlowers(GameTestHelper helper) {
        Colony colony = TestColonies.found(helper, new BlockPos(4, 1, 27));
        BuildingData hut = TestColonies.hut(helper, colony, new BlockPos(14, 1, 14), BuildingType.FLORIST, 1);
        for (int x = 12; x <= 16; x += 2) {
            helper.setBlock(new BlockPos(x, 0, 19), Blocks.GRASS_BLOCK);
            helper.setBlock(new BlockPos(x, 1, 19), Blocks.POPPY);
        }
        CitizenData citizen = colony.addNewCitizen(helper.getLevel());
        helper.succeedWhen(() -> {
            if (TestColonies.count(helper, hut, Items.POPPY) < 3) {
                throw helper.assertionException("Flowers not picked yet (" + citizen.status() + ")");
            }
        });
    }

    /** The dyer turns flowers, white wool and planks into a red bed. */
    @GameTest(environment = "hearthstead-gametest:dyer", structure = PLATFORM)
    public void dyeingMakesBeds(GameTestHelper helper) {
        Colony colony = TestColonies.found(helper, new BlockPos(4, 1, 27));
        BuildingData hut = TestColonies.hut(helper, colony, new BlockPos(14, 1, 14), BuildingType.DYER, 1);
        TestColonies.give(helper, hut, Items.POPPY, 3);
        TestColonies.give(helper, hut, Items.WHITE_WOOL, 3);
        TestColonies.give(helper, hut, Items.OAK_PLANKS, 3);
        var storage = Storage.containers(helper.getLevel(), hut);
        ItemStack bed = dev.pikachux.hearthstead.colony.Dyeing.craft(helper.getLevel(), storage, Items.RED_BED);
        if (!bed.is(Items.RED_BED) || TestColonies.count(helper, hut, Items.RED_BED) != 1) {
            throw helper.assertionException("Expected a red bed, got " + bed);
        }
        if (TestColonies.count(helper, hut, Items.POPPY) != 0 || TestColonies.count(helper, hut, Items.WHITE_WOOL) != 0) {
            throw helper.assertionException("The bed should use three poppies and three white wool");
        }
        helper.succeed();
    }

    /** The beekeeper harvests a full beehive in the apiary. */
    @GameTest(environment = "hearthstead-gametest:beekeeper", structure = PLATFORM, maxTicks = 2000, skyAccess = true)
    public void beekeeperHarvestsHoney(GameTestHelper helper) {
        Colony colony = TestColonies.found(helper, new BlockPos(4, 1, 27));
        BuildingData hut = TestColonies.hut(helper, colony, new BlockPos(14, 1, 14), BuildingType.BEEKEEPER, 1);
        TestColonies.give(helper, hut, Items.SHEARS, 1);
        BlockPos hive = hut.pos().offset(-1, 0, 2);
        var level = helper.getLevel();
        if (!(level.getBlockState(hive).getBlock() instanceof net.minecraft.world.level.block.BeehiveBlock)) {
            throw helper.assertionException("Expected the apiary's beehive at " + hive + ", found " + level.getBlockState(hive));
        }
        level.setBlockAndUpdate(hive, level.getBlockState(hive).setValue(net.minecraft.world.level.block.BeehiveBlock.HONEY_LEVEL, 5));
        CitizenData citizen = colony.addNewCitizen(level);
        helper.succeedWhen(() -> {
            if (TestColonies.count(helper, hut, Items.HONEYCOMB) == 0) {
                throw helper.assertionException("No honeycomb yet (" + citizen.status() + ") hive " + level.getBlockState(hive));
            }
        });
    }

    /** With a full warren, the rabbit herder takes a rabbit for meat and hide. */
    @GameTest(environment = "hearthstead-gametest:rabbitherder", structure = PLATFORM, maxTicks = 3000, skyAccess = true, maxAttempts = 3)
    public void rabbitHerderCullsFullWarren(GameTestHelper helper) {
        Colony colony = TestColonies.found(helper, new BlockPos(4, 1, 27));
        BuildingData hut = TestColonies.hut(helper, colony, new BlockPos(14, 1, 14), BuildingType.RABBIT_HERDER, 1);
        TestColonies.give(helper, hut, Items.IRON_SWORD, 1);
        pen(helper);
        for (int i = 0; i < 6; i++) {
            helper.spawn(EntityType.RABBIT, new BlockPos(22 + i % 3 * 2, 1, 22 + i / 3 * 2));
        }
        CitizenData citizen = colony.addNewCitizen(helper.getLevel());
        helper.succeedWhen(() -> {
            int produce = Storage.count(Storage.containers(helper.getLevel(), hut),
                    s -> s.is(Items.RABBIT) || s.is(Items.RABBIT_HIDE) || s.is(Items.COOKED_RABBIT));
            if (produce == 0) {
                throw helper.assertionException("No rabbit produce yet (" + citizen.status() + ")");
            }
        });
    }

    /** The glassblower smelts sand into glass in its workshop and makes panes from it. */
    @GameTest(environment = "hearthstead-gametest:glassblower", structure = PLATFORM, maxTicks = 4000, skyAccess = true)
    public void glassblowerMakesGlass(GameTestHelper helper) {
        Colony colony = TestColonies.found(helper, new BlockPos(4, 1, 27));
        BuildingData hut = TestColonies.hut(helper, colony, new BlockPos(14, 1, 14), BuildingType.GLASSBLOWER, 1);
        TestColonies.give(helper, hut, Items.SAND, 3);
        TestColonies.give(helper, hut, Items.COAL, 2);
        TestColonies.give(helper, hut, Items.GLASS, 8);
        TestColonies.give(helper, hut, Items.BLUE_DYE, 1);
        var storage = Storage.containers(helper.getLevel(), hut);
        ItemStack panes = dev.pikachux.hearthstead.colony.Glasswork.craft(helper.getLevel(), storage, Items.BLUE_STAINED_GLASS_PANE);
        if (!panes.is(Items.BLUE_STAINED_GLASS_PANE)) {
            throw helper.assertionException("Expected blue stained glass panes, got " + panes);
        }
        CitizenData citizen = colony.addNewCitizen(helper.getLevel());
        helper.succeedWhen(() -> {
            if (TestColonies.count(helper, hut, Items.GLASS) == 0) {
                throw helper.assertionException("No glass smelted yet (" + citizen.status() + ")");
            }
        });
    }

    /** The concrete mixer makes concrete powder from sand, gravel and dye and sets it into concrete. */
    @GameTest(environment = "hearthstead-gametest:concretemixer", structure = PLATFORM, maxTicks = 2000, skyAccess = true)
    public void concreteMixerMakesConcrete(GameTestHelper helper) {
        Colony colony = TestColonies.found(helper, new BlockPos(4, 1, 27));
        BuildingData hut = TestColonies.hut(helper, colony, new BlockPos(14, 1, 14), BuildingType.CONCRETE_MIXER, 1);
        TestColonies.give(helper, hut, Items.SAND, 4);
        TestColonies.give(helper, hut, Items.GRAVEL, 4);
        TestColonies.give(helper, hut, Items.RED_DYE, 1);
        CitizenData citizen = colony.addNewCitizen(helper.getLevel());
        helper.succeedWhen(() -> {
            if (TestColonies.count(helper, hut, Items.RED_CONCRETE) != 8) {
                throw helper.assertionException("Expected 8 red concrete (" + citizen.status() + ")");
            }
        });
    }
}
