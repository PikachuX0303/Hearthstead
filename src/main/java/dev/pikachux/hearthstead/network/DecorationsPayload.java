package dev.pikachux.hearthstead.network;

import dev.pikachux.hearthstead.Hearthstead;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.item.Item;

import java.util.List;

/**
 * The decorations the server has blueprints for, with an icon each, for the build tool.
 * Sent when a player joins and after data packs are reloaded.
 */
public record DecorationsPayload(List<Entry> decorations) implements CustomPacketPayload {
    public static final Type<DecorationsPayload> TYPE = new Type<>(Hearthstead.id("decorations"));
    private static final StreamCodec<RegistryFriendlyByteBuf, Item> ITEM_CODEC = ByteBufCodecs.registry(Registries.ITEM);
    public static final StreamCodec<RegistryFriendlyByteBuf, DecorationsPayload> CODEC =
            CustomPacketPayload.codec(DecorationsPayload::write, DecorationsPayload::read);

    public record Entry(String name, Item icon) {
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    private void write(RegistryFriendlyByteBuf buf) {
        buf.writeCollection(decorations, (b, e) -> {
            b.writeUtf(e.name(), 64);
            ITEM_CODEC.encode((RegistryFriendlyByteBuf) b, e.icon());
        });
    }

    private static DecorationsPayload read(RegistryFriendlyByteBuf buf) {
        return new DecorationsPayload(buf.readList(b -> new Entry(b.readUtf(64), ITEM_CODEC.decode((RegistryFriendlyByteBuf) b))));
    }
}
