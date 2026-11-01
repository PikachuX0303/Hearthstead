package dev.pikachux.hearthstead.network;

import dev.pikachux.hearthstead.Hearthstead;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Orders a decoration where the build tool preview was confirmed.
 */
public record PlaceDecorationPayload(String decoration, BlockPos pos, Direction facing, String style) implements CustomPacketPayload {
    public static final Type<PlaceDecorationPayload> TYPE = new Type<>(Hearthstead.id("place_decoration"));
    public static final StreamCodec<RegistryFriendlyByteBuf, PlaceDecorationPayload> CODEC =
            CustomPacketPayload.codec(PlaceDecorationPayload::write, PlaceDecorationPayload::read);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    private void write(RegistryFriendlyByteBuf buf) {
        buf.writeUtf(decoration, 64);
        buf.writeBlockPos(pos);
        buf.writeEnum(facing);
        buf.writeUtf(style, 64);
    }

    private static PlaceDecorationPayload read(RegistryFriendlyByteBuf buf) {
        return new PlaceDecorationPayload(buf.readUtf(64), buf.readBlockPos(), buf.readEnum(Direction.class), buf.readUtf(64));
    }
}
