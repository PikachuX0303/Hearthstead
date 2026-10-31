package dev.pikachux.hearthstead.block;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.pikachux.hearthstead.colony.BuildingData;
import dev.pikachux.hearthstead.colony.BuildingType;
import dev.pikachux.hearthstead.colony.Colony;
import dev.pikachux.hearthstead.colony.ColonyManager;
import dev.pikachux.hearthstead.colony.WorkOrder;
import dev.pikachux.hearthstead.network.BuildingViews;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

/**
 * A hut block. Placing one inside a colony registers the matching building;
 * placing a town hall outside of any colony founds a new colony.
 */
public class HutBlock extends BaseEntityBlock {
    public static final MapCodec<HutBlock> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Codec.STRING.xmap(BuildingType::byId, BuildingType::id).fieldOf("building").forGetter(HutBlock::type),
            propertiesCodec()
    ).apply(instance, HutBlock::new));

    public static final EnumProperty<Direction> FACING = HorizontalDirectionalBlock.FACING;

    private final BuildingType type;

    public HutBlock(BuildingType type, Properties properties) {
        super(properties);
        this.type = type;
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    public BuildingType type() {
        return type;
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Player player = context.getPlayer();
        Level level = context.getLevel();
        if (player != null && !level.isClientSide() && level instanceof ServerLevel serverLevel) {
            String error = placementError(serverLevel, context.getClickedPos(), player);
            if (error != null) {
                player.displayClientMessage(Component.translatable(error).withStyle(ChatFormatting.RED), true);
                return null;
            }
        }
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    /** Returns null if the player may place this hut here, otherwise a translation key. */
    @Nullable
    public String placementError(ServerLevel level, BlockPos pos, Player player) {
        ColonyManager manager = ColonyManager.get(level.getServer());
        Colony here = manager.colonyAt(level.dimension(), pos);
        if (type == BuildingType.TOWN_HALL) {
            if (here != null) {
                return "message.hearthstead.townhall_inside_colony";
            }
            if (manager.colonyOwnedBy(player.getUUID()) != null) {
                return "message.hearthstead.already_own_colony";
            }
            if (!manager.canFoundAt(level.dimension(), pos)) {
                return "message.hearthstead.too_close";
            }
            return null;
        }
        if (here == null) {
            return "message.hearthstead.not_in_colony";
        }
        if (!here.isMember(player)) {
            return "message.hearthstead.no_permission";
        }
        return null;
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (!(level instanceof ServerLevel serverLevel) || !(placer instanceof ServerPlayer player)) {
            return;
        }
        ColonyManager manager = ColonyManager.get(serverLevel.getServer());
        Colony colony;
        if (type == BuildingType.TOWN_HALL) {
            foundColony(serverLevel, pos, player);
            return;
        } else {
            colony = manager.colonyAt(level.dimension(), pos);
            if (colony == null) {
                return;
            }
            BuildingData building = new BuildingData(pos, type, 0);
            building.setStyle(colony.style());
            colony.addBuilding(building);
            colony.addWorkOrder(new WorkOrder(pos, 1));
            player.sendSystemMessage(Component.translatable("message.hearthstead.building_added",
                    Component.translatable(getDescriptionId()), colony.name()));
        }
        if (level.getBlockEntity(pos) instanceof HutBlockEntity hut) {
            hut.setColonyId(colony.id());
        }
    }

    /** Founds a colony around the town hall at {@code pos}, owned by the player. */
    public static Colony foundColony(ServerLevel level, BlockPos pos, Player player) {
        Colony colony = ColonyManager.get(level.getServer()).createColony(player, level.dimension(), pos);
        player.displayClientMessage(Component.translatable("message.hearthstead.colony_founded", colony.name())
                .withStyle(ChatFormatting.GREEN), false);
        if (level.getBlockEntity(pos) instanceof HutBlockEntity hut) {
            hut.setColonyId(colony.id());
        }
        return colony;
    }

    /**
     * Claims a town hall that belongs to no colony (one found in an abandoned settlement): the
     * player founds a colony there, if they could have placed a town hall at this spot.
     * Returns null on success, otherwise a translation key.
     */
    @Nullable
    public String claim(ServerLevel level, BlockPos pos, Player player) {
        if (type != BuildingType.TOWN_HALL) {
            return "message.hearthstead.not_in_colony";
        }
        String error = placementError(level, pos, player);
        if (error != null) {
            return error;
        }
        foundColony(level, pos, player);
        return null;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return InteractionResult.SUCCESS;
        }
        ColonyManager manager = ColonyManager.get(serverLevel.getServer());
        Colony colony = manager.colonyAt(level.dimension(), pos);
        BuildingData building = colony == null ? null : colony.building(pos);
        if (colony == null && type == BuildingType.TOWN_HALL) {
            String error = claim(serverLevel, pos, player);
            if (error != null) {
                player.displayClientMessage(Component.translatable(error).withStyle(ChatFormatting.RED), true);
                return InteractionResult.FAIL;
            }
            return InteractionResult.CONSUME;
        }
        if (colony == null || building == null) {
            return InteractionResult.PASS;
        }
        if (!colony.isMember(player)) {
            player.displayClientMessage(Component.translatable("message.hearthstead.no_permission").withStyle(ChatFormatting.RED), true);
            return InteractionResult.FAIL;
        }
        if (!player.isShiftKeyDown()) {
            BuildingViews.sendBuilding((ServerPlayer) player, colony, building);
        } else if (level.getBlockEntity(pos) instanceof HutBlockEntity hut) {
            player.openMenu(hut);
        }
        return InteractionResult.CONSUME;
    }

    @Override
    protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos, boolean movedByPiston) {
        super.affectNeighborsAfterRemoval(state, level, pos, movedByPiston);
        ColonyManager manager = ColonyManager.get(level.getServer());
        Colony colony = manager.colonyAt(level.dimension(), pos);
        if (colony == null) {
            return;
        }
        if (type == BuildingType.TOWN_HALL && pos.equals(colony.center())) {
            colony.broadcast(level.getServer(), Component.translatable("message.hearthstead.colony_disbanded", colony.name())
                    .withStyle(ChatFormatting.RED));
            manager.deleteColony(colony.id());
        } else {
            colony.removeBuilding(pos);
        }
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new HutBlockEntity(pos, state);
    }
}
