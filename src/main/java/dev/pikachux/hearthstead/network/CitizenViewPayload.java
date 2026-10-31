package dev.pikachux.hearthstead.network;

import dev.pikachux.hearthstead.Hearthstead;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * Data for the citizen screen.
 */
public record CitizenViewPayload(
        int entityId,
        String name,
        String job,
        float health,
        float maxHealth,
        float saturation,
        float maxSaturation,
        float happiness,
        String status,
        String home,
        String work,
        ItemStack heldItem,
        List<ItemStack> inventory
) implements CustomPacketPayload {
    public static final Type<CitizenViewPayload> TYPE = new Type<>(Hearthstead.id("citizen_view"));
    public static final StreamCodec<RegistryFriendlyByteBuf, CitizenViewPayload> CODEC =
            CustomPacketPayload.codec(CitizenViewPayload::write, CitizenViewPayload::read);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    private void write(RegistryFriendlyByteBuf buf) {
        buf.writeVarInt(entityId);
        buf.writeUtf(name);
        buf.writeUtf(job);
        buf.writeFloat(health);
        buf.writeFloat(maxHealth);
        buf.writeFloat(saturation);
        buf.writeFloat(maxSaturation);
        buf.writeFloat(happiness);
        buf.writeUtf(status);
        buf.writeUtf(home);
        buf.writeUtf(work);
        ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, heldItem);
        ItemStack.OPTIONAL_LIST_STREAM_CODEC.encode(buf, inventory);
    }

    private static CitizenViewPayload read(RegistryFriendlyByteBuf buf) {
        return new CitizenViewPayload(buf.readVarInt(), buf.readUtf(), buf.readUtf(), buf.readFloat(), buf.readFloat(),
                buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readUtf(), buf.readUtf(), buf.readUtf(),
                ItemStack.OPTIONAL_STREAM_CODEC.decode(buf), ItemStack.OPTIONAL_LIST_STREAM_CODEC.decode(buf));
    }
}
