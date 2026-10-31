package dev.pikachux.hearthstead.network;

import dev.pikachux.hearthstead.Hearthstead;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * A button pressed on the permissions screen.
 * <ul>
 *   <li>{@code SET_RANK}: {@code first} is a member's UUID, {@code second} the new rank id ({@code visitor} removes them).</li>
 *   <li>{@code ADD_PLAYER}: {@code first} is an online player's name, {@code second} the rank id.</li>
 *   <li>{@code TOGGLE}: {@code first} is {@code friend} or {@code visitor}, {@code second} an action id.</li>
 * </ul>
 */
public record PermissionActionPayload(Action action, String first, String second) implements CustomPacketPayload {
    public static final Type<PermissionActionPayload> TYPE = new Type<>(Hearthstead.id("permission_action"));
    public static final StreamCodec<RegistryFriendlyByteBuf, PermissionActionPayload> CODEC =
            CustomPacketPayload.codec(PermissionActionPayload::write, PermissionActionPayload::read);

    public enum Action {
        REFRESH, SET_RANK, ADD_PLAYER, TOGGLE
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    private void write(RegistryFriendlyByteBuf buf) {
        buf.writeEnum(action);
        buf.writeUtf(first, 64);
        buf.writeUtf(second, 32);
    }

    private static PermissionActionPayload read(RegistryFriendlyByteBuf buf) {
        return new PermissionActionPayload(buf.readEnum(Action.class), buf.readUtf(64), buf.readUtf(32));
    }
}
