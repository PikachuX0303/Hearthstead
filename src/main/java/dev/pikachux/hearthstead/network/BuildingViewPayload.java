package dev.pikachux.hearthstead.network;

import dev.pikachux.hearthstead.Hearthstead;
import dev.pikachux.hearthstead.colony.ColonyRequests;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.item.Item;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Function;

/**
 * Everything the hut screen shows, sent from the server when a player opens a hut.
 * With {@code updateOnly} set it only refreshes an already open screen.
 */
public record BuildingViewPayload(
        BlockPos pos,
        String buildingType,
        int level,
        int maxLevel,
        String colonyName,
        int residents,
        int housing,
        List<Worker> workers,
        boolean hasOrder,
        int orderLevel,
        boolean orderAssigned,
        /** What the hut's work order is: "build", "repair" or "remove" ("" without one). */
        String orderKind,
        int progressDone,
        int progressTotal,
        List<Material> materials,
        String upgradeBlocker,
        boolean needsBuilder,
        String builderJob,
        String style,
        ColonyInfo colony,
        List<RequestEntry> requests,
        List<ResearchEntry> research,
        /** A postbox's orders: the item, how many were ordered and how many have arrived. */
        List<Cost> postboxOrders,
        /** A crafting hut's taught recipes: the item, the stock to keep and how many there are. */
        List<Cost> recipes,
        List<SettingEntry> settings,
        boolean updateOnly
) implements CustomPacketPayload {
    public static final Type<BuildingViewPayload> TYPE = new Type<>(Hearthstead.id("building_view"));
    public static final StreamCodec<RegistryFriendlyByteBuf, BuildingViewPayload> CODEC =
            CustomPacketPayload.codec(BuildingViewPayload::write, BuildingViewPayload::read);

    private static final StreamCodec<RegistryFriendlyByteBuf, Item> ITEM_CODEC = ByteBufCodecs.registry(Registries.ITEM);

    public record Worker(String name, String job, String status) {
    }

    public record Material(Item item, int needed, int available) {
    }

    public record Citizen(int id, String name, String job, float happiness, float saturation, String status) {
    }

    /** A work order in the town hall's list; {@code decoration} holds a decoration's anchor so it can be cancelled. */
    public record WorkOrderEntry(String description, boolean assigned, Optional<BlockPos> decoration) {
    }

    public record Cost(Item item, int count, int have) {
    }

    /** A hut setting and its current value (see {@code HutSettings}). */
    public record SettingEntry(String id, String value) {
    }

    /**
     * An open request: what the hut at {@code target} waits for, who it waits on, and how many
     * matching items the viewing player carries. The town hall lists the whole colony's requests.
     */
    public record RequestEntry(BlockPos target, String buildingType, Item icon, int count, String description,
                               ColonyRequests.Status status, int playerHas, String crafterType) {
    }

    /** One node of the research tree as seen from the university. */
    public record ResearchEntry(String id, int branch, int tier, State state, float progress, List<Cost> cost,
                                String blocker, String blockerArgument) {
        public enum State { DONE, ACTIVE, AVAILABLE, LOCKED }
    }

    /** Colony wide information, only filled in for the town hall. */
    public record ColonyInfo(boolean present, String owner, int citizenCount, int housing, int buildingCount,
                             boolean isOwner, List<Citizen> citizens, List<WorkOrderEntry> workOrders,
                             boolean raidsEnabled, int raidersLeft, int raidSize, String style, boolean pirates, boolean festival,
                             int decorations, int intactDecorations, float decorationHappiness) {
        public static final ColonyInfo NONE = new ColonyInfo(false, "", 0, 0, 0, false, List.of(), List.of(), true, 0, 0, "", false, false,
                0, 0, 0);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    private void write(RegistryFriendlyByteBuf buf) {
        buf.writeBlockPos(pos);
        buf.writeUtf(buildingType);
        buf.writeVarInt(level);
        buf.writeVarInt(maxLevel);
        buf.writeUtf(colonyName);
        buf.writeVarInt(residents);
        buf.writeVarInt(housing);
        writeList(buf, workers, (b, w) -> {
            b.writeUtf(w.name());
            b.writeUtf(w.job());
            b.writeUtf(w.status());
        });
        buf.writeBoolean(hasOrder);
        buf.writeVarInt(orderLevel);
        buf.writeBoolean(orderAssigned);
        buf.writeUtf(orderKind);
        buf.writeVarInt(progressDone);
        buf.writeVarInt(progressTotal);
        writeList(buf, materials, (b, m) -> {
            ITEM_CODEC.encode(b, m.item());
            b.writeVarInt(m.needed());
            b.writeVarInt(m.available());
        });
        buf.writeUtf(upgradeBlocker);
        buf.writeBoolean(needsBuilder);
        buf.writeUtf(builderJob);
        buf.writeUtf(style);
        writeList(buf, requests, BuildingViewPayload::writeRequest);
        writeList(buf, research, (b, r) -> {
            b.writeUtf(r.id());
            b.writeVarInt(r.branch());
            b.writeVarInt(r.tier());
            b.writeEnum(r.state());
            b.writeFloat(r.progress());
            writeList(b, r.cost(), (b2, c) -> {
                ITEM_CODEC.encode(b2, c.item());
                b2.writeVarInt(c.count());
                b2.writeVarInt(c.have());
            });
            b.writeUtf(r.blocker());
            b.writeUtf(r.blockerArgument());
        });
        writeList(buf, postboxOrders, (b, c) -> {
            ITEM_CODEC.encode(b, c.item());
            b.writeVarInt(c.count());
            b.writeVarInt(c.have());
        });
        writeList(buf, recipes, (b, c) -> {
            ITEM_CODEC.encode(b, c.item());
            b.writeVarInt(c.count());
            b.writeVarInt(c.have());
        });
        writeList(buf, settings, (b, s) -> {
            b.writeUtf(s.id());
            b.writeUtf(s.value());
        });
        buf.writeBoolean(updateOnly);
        buf.writeBoolean(colony.present());
        if (colony.present()) {
            buf.writeUtf(colony.owner());
            buf.writeVarInt(colony.citizenCount());
            buf.writeVarInt(colony.housing());
            buf.writeVarInt(colony.buildingCount());
            buf.writeBoolean(colony.isOwner());
            writeList(buf, colony.citizens(), (b, c) -> {
                b.writeVarInt(c.id());
                b.writeUtf(c.name());
                b.writeUtf(c.job());
                b.writeFloat(c.happiness());
                b.writeFloat(c.saturation());
                b.writeUtf(c.status());
            });
            writeList(buf, colony.workOrders(), (b, o) -> {
                b.writeUtf(o.description());
                b.writeBoolean(o.assigned());
                b.writeOptional(o.decoration(), (b2, p) -> b2.writeBlockPos(p));
            });
            buf.writeBoolean(colony.raidsEnabled());
            buf.writeVarInt(colony.raidersLeft());
            buf.writeVarInt(colony.raidSize());
            buf.writeUtf(colony.style());
            buf.writeBoolean(colony.pirates());
            buf.writeBoolean(colony.festival());
            buf.writeVarInt(colony.decorations());
            buf.writeVarInt(colony.intactDecorations());
            buf.writeFloat(colony.decorationHappiness());
        }
    }

    private static BuildingViewPayload read(RegistryFriendlyByteBuf buf) {
        BlockPos pos = buf.readBlockPos();
        String type = buf.readUtf();
        int level = buf.readVarInt();
        int maxLevel = buf.readVarInt();
        String colonyName = buf.readUtf();
        int residents = buf.readVarInt();
        int housing = buf.readVarInt();
        List<Worker> workers = readList(buf, b -> new Worker(b.readUtf(), b.readUtf(), b.readUtf()));
        boolean hasOrder = buf.readBoolean();
        int orderLevel = buf.readVarInt();
        boolean orderAssigned = buf.readBoolean();
        String orderKind = buf.readUtf();
        int done = buf.readVarInt();
        int total = buf.readVarInt();
        List<Material> materials = readList(buf, b -> new Material(ITEM_CODEC.decode(b), b.readVarInt(), b.readVarInt()));
        String blocker = buf.readUtf();
        boolean needsBuilder = buf.readBoolean();
        String builderJob = buf.readUtf();
        String style = buf.readUtf();
        List<RequestEntry> requests = readList(buf, BuildingViewPayload::readRequest);
        List<ResearchEntry> research = readList(buf, b -> new ResearchEntry(b.readUtf(), b.readVarInt(), b.readVarInt(),
                b.readEnum(ResearchEntry.State.class), b.readFloat(),
                readList(b, b2 -> new Cost(ITEM_CODEC.decode(b2), b2.readVarInt(), b2.readVarInt())),
                b.readUtf(), b.readUtf()));
        List<Cost> postboxOrders = readList(buf, b -> new Cost(ITEM_CODEC.decode(b), b.readVarInt(), b.readVarInt()));
        List<Cost> recipes = readList(buf, b -> new Cost(ITEM_CODEC.decode(b), b.readVarInt(), b.readVarInt()));
        List<SettingEntry> settings = readList(buf, b -> new SettingEntry(b.readUtf(), b.readUtf()));
        boolean updateOnly = buf.readBoolean();
        ColonyInfo colony = ColonyInfo.NONE;
        if (buf.readBoolean()) {
            String owner = buf.readUtf();
            int citizenCount = buf.readVarInt();
            int colonyHousing = buf.readVarInt();
            int buildingCount = buf.readVarInt();
            boolean isOwner = buf.readBoolean();
            List<Citizen> citizens = readList(buf, b -> new Citizen(b.readVarInt(), b.readUtf(), b.readUtf(),
                    b.readFloat(), b.readFloat(), b.readUtf()));
            List<WorkOrderEntry> orders = readList(buf, b -> new WorkOrderEntry(b.readUtf(), b.readBoolean(),
                    b.readOptional(b2 -> b2.readBlockPos())));
            boolean raidsEnabled = buf.readBoolean();
            int raidersLeft = buf.readVarInt();
            int raidSize = buf.readVarInt();
            String colonyStyle = buf.readUtf();
            boolean pirates = buf.readBoolean();
            boolean festival = buf.readBoolean();
            int decorations = buf.readVarInt();
            int intactDecorations = buf.readVarInt();
            float decorationHappiness = buf.readFloat();
            colony = new ColonyInfo(true, owner, citizenCount, colonyHousing, buildingCount, isOwner, citizens, orders,
                    raidsEnabled, raidersLeft, raidSize, colonyStyle, pirates, festival, decorations, intactDecorations,
                    decorationHappiness);
        }
        return new BuildingViewPayload(pos, type, level, maxLevel, colonyName, residents, housing, workers, hasOrder,
                orderLevel, orderAssigned, orderKind, done, total, materials, blocker, needsBuilder, builderJob, style, colony, requests, research, postboxOrders, recipes, settings, updateOnly);
    }

    static void writeRequest(RegistryFriendlyByteBuf buf, RequestEntry request) {
        buf.writeBlockPos(request.target());
        buf.writeUtf(request.buildingType());
        ITEM_CODEC.encode(buf, request.icon());
        buf.writeVarInt(request.count());
        buf.writeUtf(request.description());
        buf.writeEnum(request.status());
        buf.writeVarInt(request.playerHas());
        buf.writeUtf(request.crafterType());
    }

    static RequestEntry readRequest(RegistryFriendlyByteBuf buf) {
        return new RequestEntry(buf.readBlockPos(), buf.readUtf(), ITEM_CODEC.decode(buf), buf.readVarInt(), buf.readUtf(),
                buf.readEnum(ColonyRequests.Status.class), buf.readVarInt(), buf.readUtf());
    }

    static <T> void writeList(RegistryFriendlyByteBuf buf, List<T> list, BiConsumer<RegistryFriendlyByteBuf, T> writer) {
        buf.writeVarInt(list.size());
        list.forEach(entry -> writer.accept(buf, entry));
    }

    static <T> List<T> readList(RegistryFriendlyByteBuf buf, Function<RegistryFriendlyByteBuf, T> reader) {
        int size = buf.readVarInt();
        List<T> list = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            list.add(reader.apply(buf));
        }
        return list;
    }
}
