package dev.pikachux.hearthstead.colony;

import dev.pikachux.hearthstead.config.HearthsteadConfig;
import dev.pikachux.hearthstead.entity.CitizenEntity;
import dev.pikachux.hearthstead.registry.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * A colony: a town hall, a set of huts and the citizens living there.
 */
public class Colony {
    private final ColonyManager manager;
    private final int id;
    private String name;
    private UUID owner;
    private String ownerName;
    private final Set<UUID> officers = new HashSet<>();
    private ResourceKey<Level> dimension;
    private BlockPos center;
    private final Map<BlockPos, BuildingData> buildings = new LinkedHashMap<>();
    private final Map<Integer, CitizenData> citizens = new LinkedHashMap<>();
    private final List<WorkOrder> workOrders = new ArrayList<>();
    private final ColonyRaid raid = new ColonyRaid();
    private final ColonyResearch research = new ColonyResearch();
    /** Style given to newly placed huts. */
    private String style = dev.pikachux.hearthstead.blueprint.Styles.CLASSIC;
    private int nextCitizenId = 1;
    private long spawnTimer;

    Colony(ColonyManager manager, int id) {
        this.manager = manager;
        this.id = id;
    }

    Colony(ColonyManager manager, int id, String name, Player owner, ResourceKey<Level> dimension, BlockPos center) {
        this(manager, id);
        this.name = name;
        this.owner = owner.getUUID();
        this.ownerName = owner.getName().getString();
        this.dimension = dimension;
        this.center = center.immutable();
    }

    public int id() {
        return id;
    }

    public String name() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
        markDirty();
    }

    public UUID owner() {
        return owner;
    }

    public String ownerName() {
        return ownerName;
    }

    public Set<UUID> officers() {
        return officers;
    }

    public ResourceKey<Level> dimension() {
        return dimension;
    }

    public BlockPos center() {
        return center;
    }

    public void markDirty() {
        manager.setDirty();
    }

    // ---------------------------------------------------------------- permissions

    public boolean isMember(Player player) {
        return player.getUUID().equals(owner) || officers.contains(player.getUUID());
    }

    public boolean isInside(ResourceKey<Level> dim, BlockPos pos) {
        if (!dim.equals(dimension)) {
            return false;
        }
        int radius = ColonyManager.colonyRadius();
        return Math.abs(pos.getX() - center.getX()) <= radius && Math.abs(pos.getZ() - center.getZ()) <= radius;
    }

    // ---------------------------------------------------------------- buildings

    public Collection<BuildingData> buildings() {
        return buildings.values();
    }

    @Nullable
    public BuildingData building(BlockPos pos) {
        return buildings.get(pos);
    }

    public int townHallLevel() {
        BuildingData townHall = buildings.get(center);
        return townHall == null ? 0 : townHall.level();
    }

    public void addBuilding(BuildingData building) {
        buildings.put(building.pos(), building);
        markDirty();
    }

    public void removeBuilding(BlockPos pos) {
        BuildingData removed = buildings.remove(pos);
        if (removed == null) {
            return;
        }
        workOrders.removeIf(o -> o.buildingPos().equals(pos));
        workOrders.forEach(o -> {
            if (pos.equals(o.builder())) {
                o.setBuilder(null);
            }
        });
        for (CitizenData citizen : citizens.values()) {
            if (pos.equals(citizen.workPos())) {
                citizen.assignJob(JobType.UNEMPLOYED, null);
            }
            if (pos.equals(citizen.homePos())) {
                citizen.setHomePos(null);
            }
        }
        markDirty();
    }

    public int housingCapacity() {
        return buildings.values().stream().mapToInt(this::housing).sum();
    }

    /** Citizens a building houses, including research bonuses. */
    public int housing(BuildingData building) {
        int housing = building.type().housing(building.level());
        if (building.type() == BuildingType.RESIDENCE && building.level() > 0 && research.has(Research.COMFORTABLE_HOMES)) {
            housing++;
        }
        return housing;
    }

    @Nullable
    public BuildingData nearestBuilding(BuildingType type, BlockPos from) {
        return buildings.values().stream()
                .filter(b -> b.type() == type)
                .min(Comparator.comparingDouble(b -> b.pos().distSqr(from)))
                .orElse(null);
    }

    public ColonyRaid raid() {
        return raid;
    }

    public ColonyResearch research() {
        return research;
    }

    public String style() {
        return style;
    }

    public void setStyle(String style) {
        this.style = style;
        markDirty();
    }

    // ---------------------------------------------------------------- work orders

    public List<WorkOrder> workOrders() {
        return workOrders;
    }

    @Nullable
    public WorkOrder workOrder(BlockPos buildingPos) {
        for (WorkOrder order : workOrders) {
            if (order.buildingPos().equals(buildingPos)) {
                return order;
            }
        }
        return null;
    }

    public void addWorkOrder(WorkOrder order) {
        workOrders.add(order);
        markDirty();
    }

    public void removeWorkOrder(WorkOrder order) {
        workOrders.remove(order);
        markDirty();
    }

    /**
     * Returns the order a builder should work on: the one it already claimed, or the
     * first unclaimed order it is skilled enough for. Builder's huts come first.
     */
    @Nullable
    public WorkOrder claimWorkOrder(BuildingData builderHut) {
        for (WorkOrder order : workOrders) {
            if (builderHut.pos().equals(order.builder())) {
                return order;
            }
        }
        int maxLevel = Math.max(1, builderHut.level());
        WorkOrder best = null;
        for (WorkOrder order : workOrders) {
            BuildingData target = buildings.get(order.buildingPos());
            if (order.builder() != null || target == null || order.targetLevel() > maxLevel) {
                continue;
            }
            if (best == null || (target.type() == BuildingType.BUILDER
                    && buildings.get(best.buildingPos()).type() != BuildingType.BUILDER)) {
                best = order;
            }
        }
        if (best != null) {
            best.setBuilder(builderHut.pos());
            markDirty();
        }
        return best;
    }

    // ---------------------------------------------------------------- citizens

    public Collection<CitizenData> citizens() {
        return citizens.values();
    }

    @Nullable
    public CitizenData citizen(int citizenId) {
        return citizens.get(citizenId);
    }

    public void removeCitizen(int citizenId) {
        citizens.remove(citizenId);
        markDirty();
    }

    @Nullable
    public ServerLevel level(MinecraftServer server) {
        return server.getLevel(dimension);
    }

    public void tick(MinecraftServer server, long gameTime) {
        ServerLevel level = level(server);
        if (level == null || !level.isLoaded(center)) {
            return;
        }
        assignHomes();
        assignJobs();
        raid.tick(server, this, level);

        if (citizens.size() < housingCapacity() && ++spawnTimer * 20 >= 20L * HearthsteadConfig.NEW_CITIZEN_INTERVAL.get()) {
            spawnTimer = 0;
            addNewCitizen(level);
        }

        for (CitizenData data : citizens.values()) {
            Entity entity = data.entityUuid() == null ? null : level.getEntity(data.entityUuid());
            if (entity instanceof CitizenEntity && entity.isAlive()) {
                continue;
            }
            if (data.entityUuid() == null || gameTime - data.lastSeen() > 20L * HearthsteadConfig.RESPAWN_DELAY.get()) {
                spawnCitizen(level, data);
            }
        }
    }

    /** A new settler arrives at the town hall. */
    public CitizenData addNewCitizen(ServerLevel level) {
        CitizenData data = CitizenData.create(nextCitizenId++, level.getRandom());
        citizens.put(data.id(), data);
        spawnCitizen(level, data);
        broadcast(level.getServer(), Component.translatable("message.hearthstead.citizen_arrived", data.name(), name));
        markDirty();
        return data;
    }

    private void spawnCitizen(ServerLevel level, CitizenData data) {
        CitizenEntity entity = ModEntities.CITIZEN.create(level, EntitySpawnReason.MOB_SUMMONED);
        if (entity == null) {
            return;
        }
        BlockPos spawn = findSpawnPos(level);
        entity.snapTo(spawn.getX() + 0.5, spawn.getY(), spawn.getZ() + 0.5, level.getRandom().nextFloat() * 360F, 0);
        entity.bind(this, data);
        data.setEntityUuid(entity.getUUID());
        data.setLastSeen(level.getGameTime());
        level.addFreshEntity(entity);
        markDirty();
    }

    /** A free spot next to the town hall, at ground level rather than on its roof. */
    private BlockPos findSpawnPos(ServerLevel level) {
        List<BlockPos> candidates = new ArrayList<>();
        for (BlockPos pos : BlockPos.betweenClosed(center.offset(-3, -2, -3), center.offset(3, 3, 3))) {
            if (!pos.equals(center) && isStandable(level, pos)) {
                candidates.add(pos.immutable());
            }
        }
        if (!candidates.isEmpty()) {
            return candidates.get(level.getRandom().nextInt(candidates.size()));
        }
        return level.getHeightmapPos(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, center.north());
    }

    private static boolean isStandable(ServerLevel level, BlockPos pos) {
        return level.getBlockState(pos).getCollisionShape(level, pos).isEmpty()
                && level.getBlockState(pos.above()).getCollisionShape(level, pos.above()).isEmpty()
                && level.getBlockState(pos.below()).isFaceSturdy(level, pos.below(), net.minecraft.core.Direction.UP);
    }

    private void assignHomes() {
        Map<BlockPos, Integer> occupancy = new LinkedHashMap<>();
        for (CitizenData citizen : citizens.values()) {
            BlockPos home = citizen.homePos();
            if (home != null) {
                BuildingData building = buildings.get(home);
                if (building == null || housing(building) <= occupancy.getOrDefault(home, 0)) {
                    citizen.setHomePos(null);
                } else {
                    occupancy.merge(home, 1, Integer::sum);
                }
            }
        }
        for (CitizenData citizen : citizens.values()) {
            if (citizen.homePos() != null) {
                continue;
            }
            for (BuildingData building : buildings.values()) {
                int capacity = housing(building);
                if (capacity > occupancy.getOrDefault(building.pos(), 0)) {
                    citizen.setHomePos(building.pos());
                    occupancy.merge(building.pos(), 1, Integer::sum);
                    markDirty();
                    break;
                }
            }
        }
    }

    private void assignJobs() {
        for (BuildingData building : buildings.values()) {
            JobType job = building.type().job();
            if (job == null) {
                continue;
            }
            long employed = citizens.values().stream().filter(c -> building.pos().equals(c.workPos())).count();
            int needed = building.type().workers(building.level()) - (int) employed;
            for (CitizenData citizen : citizens.values()) {
                if (needed <= 0) {
                    break;
                }
                if (citizen.job() == JobType.UNEMPLOYED) {
                    citizen.assignJob(job, building.pos());
                    needed--;
                    markDirty();
                }
            }
        }
    }

    public void broadcast(MinecraftServer server, Component message) {
        List<UUID> recipients = new ArrayList<>(officers);
        recipients.add(owner);
        for (UUID uuid : recipients) {
            ServerPlayer player = server.getPlayerList().getPlayer(uuid);
            if (player != null) {
                player.sendSystemMessage(message);
            }
        }
    }

    // ---------------------------------------------------------------- persistence

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("id", id);
        tag.putString("name", name);
        tag.putString("owner", owner.toString());
        tag.putString("ownerName", ownerName);
        ListTag officerList = new ListTag();
        officers.forEach(o -> officerList.add(net.minecraft.nbt.StringTag.valueOf(o.toString())));
        tag.put("officers", officerList);
        tag.putString("dimension", dimension.identifier().toString());
        tag.putLong("center", center.asLong());
        ListTag buildingList = new ListTag();
        buildings.values().forEach(b -> buildingList.add(b.save()));
        tag.put("buildings", buildingList);
        ListTag citizenList = new ListTag();
        citizens.values().forEach(c -> citizenList.add(c.save()));
        tag.put("citizens", citizenList);
        ListTag orderList = new ListTag();
        workOrders.forEach(o -> orderList.add(o.save()));
        tag.put("workOrders", orderList);
        tag.put("raid", raid.save());
        tag.put("research", research.save());
        tag.putString("style", style);
        tag.putInt("nextCitizenId", nextCitizenId);
        return tag;
    }

    static Colony load(ColonyManager manager, CompoundTag tag) {
        Colony colony = new Colony(manager, tag.getIntOr("id", 0));
        colony.name = tag.getStringOr("name", "Colony");
        colony.owner = UUID.fromString(tag.getStringOr("owner", new UUID(0, 0).toString()));
        colony.ownerName = tag.getStringOr("ownerName", "?");
        tag.getListOrEmpty("officers").forEach(t -> t.asString().ifPresent(s -> colony.officers.add(UUID.fromString(s))));
        colony.dimension = ResourceKey.create(Registries.DIMENSION, Identifier.parse(tag.getStringOr("dimension", "minecraft:overworld")));
        colony.center = BlockPos.of(tag.getLongOr("center", 0L));
        tag.getListOrEmpty("buildings").compoundStream().map(BuildingData::load).forEach(b -> colony.buildings.put(b.pos(), b));
        tag.getListOrEmpty("citizens").compoundStream().map(CitizenData::load).forEach(c -> colony.citizens.put(c.id(), c));
        colony.raid.load(tag.getCompoundOrEmpty("raid"));
        colony.research.load(tag.getCompoundOrEmpty("research"));
        colony.style = tag.getStringOr("style", dev.pikachux.hearthstead.blueprint.Styles.CLASSIC);
        tag.getListOrEmpty("workOrders").compoundStream().map(WorkOrder::load).forEach(colony.workOrders::add);
        colony.nextCitizenId = tag.getIntOr("nextCitizenId", colony.citizens.size() + 1);
        return colony;
    }
}
