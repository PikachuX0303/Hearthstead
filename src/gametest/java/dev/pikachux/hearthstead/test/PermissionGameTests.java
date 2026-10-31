package dev.pikachux.hearthstead.test;

import dev.pikachux.hearthstead.colony.Colony;
import dev.pikachux.hearthstead.colony.ColonyPermissions;
import dev.pikachux.hearthstead.colony.ColonyPermissions.Action;
import dev.pikachux.hearthstead.colony.ColonyPermissions.Rank;
import dev.pikachux.hearthstead.event.ColonyEvents;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;

import java.util.UUID;

public class PermissionGameTests {
    private static final String PLATFORM = "hearthstead-gametest:platform";

    /** Owners and officers may do everything; friends and visitors follow the colony's rules. */
    @GameTest(structure = PLATFORM)
    public void ranksAndRules(GameTestHelper helper) {
        Colony colony = TestColonies.found(helper, new BlockPos(4, 1, 27));
        ColonyPermissions permissions = colony.permissions();
        UUID owner = colony.owner();
        UUID friend = UUID.randomUUID();
        UUID officer = UUID.randomUUID();
        UUID visitor = UUID.randomUUID();
        permissions.setRank(friend, "Fern", Rank.FRIEND);
        permissions.setRank(officer, "Otto", Rank.OFFICER);

        check(helper, permissions.rank(owner) == Rank.OWNER && permissions.rank(officer) == Rank.OFFICER
                && permissions.rank(friend) == Rank.FRIEND && permissions.rank(visitor) == Rank.VISITOR, "the right ranks");
        check(helper, colony.officers().contains(officer), "officers in the officer list");
        for (Action action : Action.values()) {
            check(helper, permissions.allows(owner, action) && permissions.allows(officer, action), "owner and officer may " + action);
            check(helper, !permissions.allows(visitor, action), "visitors may not " + action + " by default");
        }
        check(helper, permissions.allows(friend, Action.USE_BLOCKS) && permissions.allows(friend, Action.CONTAINERS)
                && permissions.allows(friend, Action.HUT_WINDOWS), "friends to use doors, containers and hut windows");
        check(helper, !permissions.allows(friend, Action.BREAK) && !permissions.allows(friend, Action.BUILD)
                && !permissions.allows(friend, Action.HURT), "friends not to break, build or hurt by default");

        permissions.setAllowed(Rank.VISITOR, Action.USE_BLOCKS, true);
        permissions.setAllowed(Rank.FRIEND, Action.BUILD, true);
        check(helper, permissions.allows(visitor, Action.USE_BLOCKS) && permissions.allows(friend, Action.BUILD), "changed rules to apply");

        // Ranks and rules survive saving.
        ColonyPermissions copy = colony.permissions();
        var saved = permissions.save();
        permissions.setRank(friend, "Fern", Rank.VISITOR);
        permissions.setAllowed(Rank.VISITOR, Action.USE_BLOCKS, false);
        copy.load(saved);
        check(helper, copy.rank(friend) == Rank.FRIEND && copy.name(friend).equals("Fern")
                && copy.allows(visitor, Action.USE_BLOCKS) && copy.allows(friend, Action.BUILD), "permissions after loading");

        permissions.setRank(officer, "Otto", Rank.VISITOR);
        check(helper, !colony.officers().contains(officer) && permissions.rank(officer) == Rank.VISITOR, "removing a member");
        permissions.setRank(owner, "x", Rank.VISITOR);
        check(helper, permissions.rank(owner) == Rank.OWNER, "the owner never to lose their rank");
        helper.succeed();
    }

    /** Right-clicks are sorted into using, opening containers and building. */
    @GameTest(structure = PLATFORM)
    public void rightClicksAreClassified(GameTestHelper helper) {
        var level = helper.getLevel();
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        BlockPos door = helper.absolutePos(new BlockPos(2, 1, 2));
        BlockPos chest = helper.absolutePos(new BlockPos(5, 1, 2));
        BlockPos stone = helper.absolutePos(new BlockPos(8, 1, 2));
        level.setBlockAndUpdate(door, Blocks.OAK_TRAPDOOR.defaultBlockState());
        level.setBlockAndUpdate(chest, Blocks.CHEST.defaultBlockState());
        level.setBlockAndUpdate(stone, Blocks.STONE.defaultBlockState());
        check(helper, ColonyEvents.actionFor(level, player, ItemStack.EMPTY, door, level.getBlockState(door)) == Action.USE_BLOCKS,
                "a trapdoor to be used");
        check(helper, ColonyEvents.actionFor(level, player, new ItemStack(Items.DIRT), door, level.getBlockState(door)) == Action.USE_BLOCKS,
                "a trapdoor to be used even with a block in hand");
        check(helper, ColonyEvents.actionFor(level, player, ItemStack.EMPTY, chest, level.getBlockState(chest)) == Action.CONTAINERS,
                "a chest to be a container");
        check(helper, ColonyEvents.actionFor(level, player, new ItemStack(Items.DIRT), stone, level.getBlockState(stone)) == Action.BUILD,
                "placing dirt on stone to be building");
        check(helper, ColonyEvents.actionFor(level, player, new ItemStack(Items.WATER_BUCKET), stone, level.getBlockState(stone)) == Action.BUILD,
                "emptying a bucket to be building");
        helper.succeed();
    }

    private static void check(GameTestHelper helper, boolean condition, String expected) {
        if (!condition) {
            throw helper.assertionException("Expected " + expected);
        }
    }
}
