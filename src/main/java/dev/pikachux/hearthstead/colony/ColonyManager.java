package dev.pikachux.hearthstead.colony;

import com.mojang.serialization.Codec;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Holds every colony on the server. Stored with the overworld's saved data.
 */
public class ColonyManager extends SavedData {
    /** Half the side length of the square area a colony claims. */
    public static final int COLONY_RADIUS = 64;
    /** Minimum distance between two town halls. */
    public static final int MIN_COLONY_DISTANCE = COLONY_RADIUS * 2 + 16;

    private static final Codec<ColonyManager> CODEC = CompoundTag.CODEC.xmap(ColonyManager::load, ColonyManager::save);
    public static final SavedDataType<ColonyManager> TYPE = new SavedDataType<>(
            "hearthstead_colonies", ColonyManager::new, CODEC, DataFixTypes.SAVED_DATA_COMMAND_STORAGE);
    /** Where colonies were saved before the mod was renamed to Hearthstead. */
    private static final SavedDataType<ColonyManager> LEGACY_TYPE = new SavedDataType<>(
            "minecolonies_colonies", ColonyManager::new, CODEC, DataFixTypes.SAVED_DATA_COMMAND_STORAGE);

    private final Map<Integer, Colony> colonies = new LinkedHashMap<>();
    private int nextId = 1;

    public static ColonyManager get(MinecraftServer server) {
        var storage = server.overworld().getDataStorage();
        ColonyManager manager = storage.get(TYPE);
        if (manager == null) {
            // First load after the rename: carry over the colonies saved under the old name.
            ColonyManager legacy = storage.get(LEGACY_TYPE);
            manager = legacy != null ? legacy : new ColonyManager();
            storage.set(TYPE, manager);
            manager.setDirty();
        }
        return manager;
    }

    public Collection<Colony> colonies() {
        return colonies.values();
    }

    @Nullable
    public Colony colony(int id) {
        return colonies.get(id);
    }

    @Nullable
    public Colony colonyAt(ResourceKey<Level> dimension, BlockPos pos) {
        for (Colony colony : colonies.values()) {
            if (colony.isInside(dimension, pos)) {
                return colony;
            }
        }
        return null;
    }

    @Nullable
    public Colony colonyOwnedBy(UUID player) {
        for (Colony colony : colonies.values()) {
            if (colony.owner().equals(player)) {
                return colony;
            }
        }
        return null;
    }

    @Nullable
    public Colony colonyOf(Player player) {
        Colony owned = colonyOwnedBy(player.getUUID());
        if (owned != null) {
            return owned;
        }
        for (Colony colony : colonies.values()) {
            if (colony.isMember(player)) {
                return colony;
            }
        }
        return null;
    }

    /** Returns true if no other colony is too close for a new town hall at the position. */
    public boolean canFoundAt(ResourceKey<Level> dimension, BlockPos pos) {
        for (Colony colony : colonies.values()) {
            if (colony.dimension().equals(dimension)
                    && Math.abs(colony.center().getX() - pos.getX()) < MIN_COLONY_DISTANCE
                    && Math.abs(colony.center().getZ() - pos.getZ()) < MIN_COLONY_DISTANCE) {
                return false;
            }
        }
        return true;
    }

    public Colony createColony(Player owner, ResourceKey<Level> dimension, BlockPos townHall) {
        Colony colony = new Colony(this, nextId++, owner.getName().getString() + "'s Colony", owner, dimension, townHall);
        colony.addBuilding(new BuildingData(townHall, BuildingType.TOWN_HALL, 0));
        colony.addWorkOrder(new WorkOrder(townHall, 1));
        colonies.put(colony.id(), colony);
        setDirty();
        return colony;
    }

    public void deleteColony(int id) {
        colonies.remove(id);
        setDirty();
    }

    public void tick(MinecraftServer server) {
        long time = server.overworld().getGameTime();
        for (Colony colony : colonies.values()) {
            colony.tick(server, time);
        }
    }

    private CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("nextId", nextId);
        ListTag list = new ListTag();
        colonies.values().forEach(c -> list.add(c.save()));
        tag.put("colonies", list);
        return tag;
    }

    private static ColonyManager load(CompoundTag tag) {
        ColonyManager manager = new ColonyManager();
        manager.nextId = tag.getIntOr("nextId", 1);
        tag.getListOrEmpty("colonies").compoundStream()
                .map(t -> Colony.load(manager, t))
                .forEach(c -> manager.colonies.put(c.id(), c));
        return manager;
    }
}
