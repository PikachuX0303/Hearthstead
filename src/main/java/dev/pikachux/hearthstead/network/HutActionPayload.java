package dev.pikachux.hearthstead.network;

import dev.pikachux.hearthstead.Hearthstead;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * A button pressed in the hut screen.
 */
public record HutActionPayload(BlockPos pos, Action action, String argument) implements CustomPacketPayload {
    public static final Type<HutActionPayload> TYPE = new Type<>(Hearthstead.id("hut_action"));
    public static final StreamCodec<RegistryFriendlyByteBuf, HutActionPayload> CODEC =
            CustomPacketPayload.codec(HutActionPayload::write, HutActionPayload::read);

    public enum Action {
        REFRESH, OPEN_INVENTORY, UPGRADE, CANCEL_ORDER, RENAME_COLONY, TOGGLE_RAIDS, START_RESEARCH, CYCLE_STYLE,
        /** Hand in items for a request; the argument is {@link #requestArgument}. */
        FULFILL_REQUEST,
        /** Moves a hut setting to its next value; the argument is the setting's id. */
        CHANGE_SETTING,
        /** Opens the colony's permissions screen (town hall only). */
        OPEN_PERMISSIONS,
        /** Cancels a decoration's work order (town hall only); the argument is its anchor as a long. */
        CANCEL_DECORATION
    }

    private static final int MAX_ARGUMENT = 256;

    /** The argument of {@link Action#FULFILL_REQUEST}: the requesting hut and the request's description. */
    public static String requestArgument(BlockPos target, String description) {
        return target.asLong() + "|" + description;
    }

    public HutActionPayload(BlockPos pos, Action action) {
        this(pos, action, "");
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    private void write(RegistryFriendlyByteBuf buf) {
        buf.writeBlockPos(pos);
        buf.writeEnum(action);
        buf.writeUtf(argument, MAX_ARGUMENT);
    }

    private static HutActionPayload read(RegistryFriendlyByteBuf buf) {
        return new HutActionPayload(buf.readBlockPos(), buf.readEnum(Action.class), buf.readUtf(MAX_ARGUMENT));
    }
}
