package dev.pikachux.hearthstead.network;

import dev.pikachux.hearthstead.Hearthstead;
import dev.pikachux.hearthstead.blueprint.Blueprint;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.level.block.Block;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * A blueprint sent to the client for the build tool preview. For an upgrade preview
 * {@code existingHut} is set and the preview is fixed to that hut. For a decoration
 * {@code decoration} holds its name and {@code buildingType} is empty.
 */
public record BlueprintPayload(String buildingType, int level, Blueprint blueprint, Optional<BlockPos> existingHut,
                               Direction hutFacing, String style, List<String> styles, String decoration, boolean move)
        implements CustomPacketPayload {
    public static final Type<BlueprintPayload> TYPE = new Type<>(Hearthstead.id("blueprint"));
    public static final StreamCodec<RegistryFriendlyByteBuf, BlueprintPayload> CODEC =
            CustomPacketPayload.codec(BlueprintPayload::write, BlueprintPayload::read);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    private void write(RegistryFriendlyByteBuf buf) {
        buf.writeUtf(buildingType);
        buf.writeVarInt(level);
        buf.writeEnum(blueprint.anchorFacing());
        buf.writeVarInt(blueprint.entries().size());
        for (Blueprint.Entry entry : blueprint.entries()) {
            buf.writeBlockPos(entry.offset());
            buf.writeVarInt(Block.getId(entry.state()));
        }
        buf.writeOptional(existingHut, (b, pos) -> b.writeBlockPos(pos));
        buf.writeEnum(hutFacing);
        buf.writeUtf(style);
        buf.writeCollection(styles, (b, s) -> b.writeUtf(s));
        buf.writeUtf(decoration);
        buf.writeBoolean(move);
    }

    private static BlueprintPayload read(RegistryFriendlyByteBuf buf) {
        String type = buf.readUtf();
        int level = buf.readVarInt();
        Direction anchorFacing = buf.readEnum(Direction.class);
        int size = buf.readVarInt();
        List<Blueprint.Entry> entries = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            entries.add(new Blueprint.Entry(buf.readBlockPos(), Block.stateById(buf.readVarInt())));
        }
        Optional<BlockPos> existingHut = buf.readOptional(b -> b.readBlockPos());
        Direction hutFacing = buf.readEnum(Direction.class);
        String style = buf.readUtf();
        List<String> styles = buf.readList(b -> b.readUtf());
        String decoration = buf.readUtf();
        boolean move = buf.readBoolean();
        return new BlueprintPayload(type, level, Blueprint.of(entries, anchorFacing), existingHut, hutFacing, style, styles, decoration, move);
    }
}
