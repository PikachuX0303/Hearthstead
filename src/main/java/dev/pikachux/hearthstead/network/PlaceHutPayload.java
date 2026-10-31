package dev.pikachux.hearthstead.network;

import dev.pikachux.hearthstead.Hearthstead;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Places a hut where the build tool preview was confirmed.
 */
public record PlaceHutPayload(String buildingType, BlockPos pos, Direction facing, String style) implements CustomPacketPayload {
    public static final Type<PlaceHutPayload> TYPE = new Type<>(Hearthstead.id("place_hut"));
    public static final StreamCodec<RegistryFriendlyByteBuf, PlaceHutPayload> CODEC =
            CustomPacketPayload.codec(PlaceHutPayload::write, PlaceHutPayload::read);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    private void write(RegistryFriendlyByteBuf buf) {
        buf.writeUtf(buildingType);
        buf.writeBlockPos(pos);
        buf.writeEnum(facing);
        buf.writeUtf(style, 64);
    }

    private static PlaceHutPayload read(RegistryFriendlyByteBuf buf) {
        return new PlaceHutPayload(buf.readUtf(), buf.readBlockPos(), buf.readEnum(Direction.class), buf.readUtf(64));
    }
}
