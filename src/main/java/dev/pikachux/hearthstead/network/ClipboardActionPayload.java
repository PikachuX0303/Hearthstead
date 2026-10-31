package dev.pikachux.hearthstead.network;

import dev.pikachux.hearthstead.Hearthstead;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * A button pressed on the clipboard screen. For {@link Action#FULFILL_REQUEST} the argument is
 * {@link HutActionPayload#requestArgument}.
 */
public record ClipboardActionPayload(Action action, String argument) implements CustomPacketPayload {
    public static final Type<ClipboardActionPayload> TYPE = new Type<>(Hearthstead.id("clipboard_action"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ClipboardActionPayload> CODEC =
            CustomPacketPayload.codec(ClipboardActionPayload::write, ClipboardActionPayload::read);

    public enum Action {
        REFRESH, FULFILL_REQUEST
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    private void write(RegistryFriendlyByteBuf buf) {
        buf.writeEnum(action);
        buf.writeUtf(argument, 256);
    }

    private static ClipboardActionPayload read(RegistryFriendlyByteBuf buf) {
        return new ClipboardActionPayload(buf.readEnum(Action.class), buf.readUtf(256));
    }
}
