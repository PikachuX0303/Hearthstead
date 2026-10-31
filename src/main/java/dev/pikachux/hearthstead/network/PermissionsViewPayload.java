package dev.pikachux.hearthstead.network;

import dev.pikachux.hearthstead.Hearthstead;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.List;
import java.util.UUID;

/**
 * A colony's members and what friends and visitors may do, for the permissions screen.
 * {@code canEdit} is true for the owner; {@code updateOnly} refreshes an open screen.
 */
public record PermissionsViewPayload(String colonyName, boolean canEdit, List<Member> members, List<String> friendRules,
                                     List<String> visitorRules, boolean updateOnly) implements CustomPacketPayload {
    public static final Type<PermissionsViewPayload> TYPE = new Type<>(Hearthstead.id("permissions_view"));
    public static final StreamCodec<RegistryFriendlyByteBuf, PermissionsViewPayload> CODEC =
            CustomPacketPayload.codec(PermissionsViewPayload::write, PermissionsViewPayload::read);

    /** A member of the colony and their rank id ({@code owner}, {@code officer} or {@code friend}). */
    public record Member(UUID uuid, String name, String rank) {
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    private void write(RegistryFriendlyByteBuf buf) {
        buf.writeUtf(colonyName);
        buf.writeBoolean(canEdit);
        BuildingViewPayload.writeList(buf, members, (b, m) -> {
            b.writeUUID(m.uuid());
            b.writeUtf(m.name());
            b.writeUtf(m.rank());
        });
        BuildingViewPayload.writeList(buf, friendRules, (b, r) -> b.writeUtf(r));
        BuildingViewPayload.writeList(buf, visitorRules, (b, r) -> b.writeUtf(r));
        buf.writeBoolean(updateOnly);
    }

    private static PermissionsViewPayload read(RegistryFriendlyByteBuf buf) {
        return new PermissionsViewPayload(buf.readUtf(), buf.readBoolean(),
                BuildingViewPayload.readList(buf, b -> new Member(b.readUUID(), b.readUtf(), b.readUtf())),
                BuildingViewPayload.readList(buf, b -> b.readUtf()),
                BuildingViewPayload.readList(buf, b -> b.readUtf()),
                buf.readBoolean());
    }
}
