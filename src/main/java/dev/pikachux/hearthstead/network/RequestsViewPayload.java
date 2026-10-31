package dev.pikachux.hearthstead.network;

import dev.pikachux.hearthstead.Hearthstead;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.List;

/**
 * The colony's open requests, shown by the clipboard. {@code updateOnly} refreshes an open
 * clipboard screen without opening one.
 */
public record RequestsViewPayload(String colonyName, List<BuildingViewPayload.RequestEntry> requests, boolean updateOnly)
        implements CustomPacketPayload {
    public static final Type<RequestsViewPayload> TYPE = new Type<>(Hearthstead.id("requests_view"));
    public static final StreamCodec<RegistryFriendlyByteBuf, RequestsViewPayload> CODEC =
            CustomPacketPayload.codec(RequestsViewPayload::write, RequestsViewPayload::read);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    private void write(RegistryFriendlyByteBuf buf) {
        buf.writeUtf(colonyName);
        BuildingViewPayload.writeList(buf, requests, BuildingViewPayload::writeRequest);
        buf.writeBoolean(updateOnly);
    }

    private static RequestsViewPayload read(RegistryFriendlyByteBuf buf) {
        return new RequestsViewPayload(buf.readUtf(), BuildingViewPayload.readList(buf, BuildingViewPayload::readRequest),
                buf.readBoolean());
    }
}
