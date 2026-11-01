package dev.pikachux.hearthstead.network;

import dev.pikachux.hearthstead.Hearthstead;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.Optional;

/**
 * Asks the server for a blueprint to preview: level 1 of a hut type, or the next
 * level of an existing hut, or a decoration when {@code decoration} names one (the building type
 * is then ignored). An empty style means the building's or colony's style.
 */
public record BlueprintRequestPayload(String buildingType, Optional<BlockPos> existingHut, String style, String decoration)
        implements CustomPacketPayload {
    public static final Type<BlueprintRequestPayload> TYPE = new Type<>(Hearthstead.id("blueprint_request"));
    public static final StreamCodec<RegistryFriendlyByteBuf, BlueprintRequestPayload> CODEC =
            CustomPacketPayload.codec(BlueprintRequestPayload::write, BlueprintRequestPayload::read);

    public BlueprintRequestPayload(String buildingType, Optional<BlockPos> existingHut, String style) {
        this(buildingType, existingHut, style, "");
    }

    /** Asks for a decoration's blueprint. */
    public static BlueprintRequestPayload decoration(String name, String style) {
        return new BlueprintRequestPayload("", Optional.empty(), style, name);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    private void write(RegistryFriendlyByteBuf buf) {
        buf.writeUtf(buildingType);
        buf.writeOptional(existingHut, (b, pos) -> b.writeBlockPos(pos));
        buf.writeUtf(style, 64);
        buf.writeUtf(decoration, 64);
    }

    private static BlueprintRequestPayload read(RegistryFriendlyByteBuf buf) {
        return new BlueprintRequestPayload(buf.readUtf(), buf.readOptional(b -> b.readBlockPos()), buf.readUtf(64), buf.readUtf(64));
    }
}
