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
        REFRESH, OPEN_INVENTORY, UPGRADE, CANCEL_ORDER, RENAME_COLONY, TOGGLE_RAIDS, START_RESEARCH, CYCLE_STYLE
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
        buf.writeUtf(argument, 64);
    }

    private static HutActionPayload read(RegistryFriendlyByteBuf buf) {
        return new HutActionPayload(buf.readBlockPos(), buf.readEnum(Action.class), buf.readUtf(64));
    }
}
