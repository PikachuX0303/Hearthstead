package dev.pikachux.hearthstead.network;

import dev.pikachux.hearthstead.Hearthstead;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Moves the hut at {@code from} to where the build tool's move preview was confirmed.
 */
public record MoveHutPayload(BlockPos from, BlockPos to, Direction facing) implements CustomPacketPayload {
    public static final Type<MoveHutPayload> TYPE = new Type<>(Hearthstead.id("move_hut"));
    public static final StreamCodec<RegistryFriendlyByteBuf, MoveHutPayload> CODEC =
            CustomPacketPayload.codec(MoveHutPayload::write, MoveHutPayload::read);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    private void write(RegistryFriendlyByteBuf buf) {
        buf.writeBlockPos(from);
        buf.writeBlockPos(to);
        buf.writeEnum(facing);
    }

    private static MoveHutPayload read(RegistryFriendlyByteBuf buf) {
        return new MoveHutPayload(buf.readBlockPos(), buf.readBlockPos(), buf.readEnum(Direction.class));
    }
}
