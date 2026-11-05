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
        net.minecraft.network.chat.Component status,
        net.minecraft.network.chat.Component home,
        net.minecraft.network.chat.Component work,
        ItemStack heldItem,
        List<ItemStack> inventory,
        List<Integer> skills,
        int primarySkill
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
        net.minecraft.network.chat.ComponentSerialization.STREAM_CODEC.encode(buf, status);
        net.minecraft.network.chat.ComponentSerialization.STREAM_CODEC.encode(buf, home);
        net.minecraft.network.chat.ComponentSerialization.STREAM_CODEC.encode(buf, work);
        ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, heldItem);
        ItemStack.OPTIONAL_LIST_STREAM_CODEC.encode(buf, inventory);
        BuildingViewPayload.writeList(buf, skills, (b, level) -> b.writeVarInt(level));
        buf.writeVarInt(primarySkill + 1);
    }

    private static CitizenViewPayload read(RegistryFriendlyByteBuf buf) {
        return new CitizenViewPayload(buf.readVarInt(), buf.readUtf(), buf.readUtf(), buf.readFloat(), buf.readFloat(),
                buf.readFloat(), buf.readFloat(), buf.readFloat(), net.minecraft.network.chat.ComponentSerialization.STREAM_CODEC.decode(buf),
                net.minecraft.network.chat.ComponentSerialization.STREAM_CODEC.decode(buf),
                net.minecraft.network.chat.ComponentSerialization.STREAM_CODEC.decode(buf),
                ItemStack.OPTIONAL_STREAM_CODEC.decode(buf), ItemStack.OPTIONAL_LIST_STREAM_CODEC.decode(buf),
                BuildingViewPayload.readList(buf, b -> b.readVarInt()), buf.readVarInt() - 1);
    }
}
