package dev.pikachux.hearthstead.colony;

import dev.pikachux.hearthstead.entity.Status;
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
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import net.minecraft.core.Direction;
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
    private final List<Decoration> decorations = new ArrayList<>();
    /** Scarecrows marking fields, in the order they were placed. */
    private final List<BlockPos> fields = new ArrayList<>();
    private int decorationTimer;
    private final ColonyRaid raid = new ColonyRaid();
    private final ColonyHappenings happenings = new ColonyHappenings();
    private final ColonyPermissions permissions = new ColonyPermissions(this);
    private final ColonyResearch research = new ColonyResearch();
    /** Style given to newly placed huts. */
    private String style = dev.pikachux.hearthstead.blueprint.Styles.CLASSIC;
    private int nextCitizenId = 1;
    private long spawnTimer;
    /** How fast citizens gain skill experience; raised by a staffed Library with books. */
    private double skillGainMultiplier = 1;
    /** Citizens who died and wait to be buried at a graveyard. */
    private final List<CitizenData> dead = new ArrayList<>();
    /** Game time until which the colony mourns its dead. */
    private long mourningUntil;
    /** Names of the citizens buried in the colony's graveyard, newest last. */
    private final List<String> fallen = new ArrayList<>();

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
        workOrders.removeIf(o -> !o.isDecoration() && o.buildingPos().equals(pos));
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
        if (building.type() == BuildingType.RESIDENCE && building.level() > 0) {
            housing += research.has(Research.COMFORTABLE_HOMES) ? 1 : 0;
            housing += research.has(Research.SPACIOUS_HOMES) ? 1 : 0;
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

    /** How fast citizens gain skill experience: 1, plus 0.25 per level of the best staffed Library that has books. */
    public double skillGainMultiplier() {
        return skillGainMultiplier;
    }

    private double libraryBonus(ServerLevel level) {
        double best = 1;
        for (BuildingData building : buildings.values()) {
            if (building.type() != BuildingType.LIBRARY || building.level() == 0
                    || citizens.values().stream().noneMatch(c -> building.pos().equals(c.workPos()))
                    || Storage.count(Storage.containers(level, building), s -> s.is(net.minecraft.world.item.Items.BOOK)) == 0) {
                continue;
            }
            // A library set to save books lends half as much.
            double perLevel = "thrifty".equals(HutSettings.get(building, HutSettings.BOOK_USE)) ? 0.125 : 0.25;
            best = Math.max(best, 1 + perLevel * building.level());
        }
        return best;
    }

    /** The colony's best built training ground of this type (Combat Academy or Archery), or null. */
    @Nullable
    public BuildingData trainingGround(BuildingType type) {
        BuildingData best = null;
        for (BuildingData building : buildings.values()) {
            if (building.type() == type && building.level() > 0 && HutSettings.isOn(building, HutSettings.TRAINING)
                    && (best == null || building.level() > best.level())) {
                best = building;
            }
        }
        return best;
    }

    /** Who may do what in the colony. */
    public ColonyPermissions permissions() {
        return permissions;
    }

    /** Colony events: merchant visits and festivals. */
    public ColonyHappenings happenings() {
        return happenings;
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
            if (!order.isDecoration() && order.buildingPos().equals(buildingPos)) {
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
            BuildingData target = order.isDecoration() ? null : buildings.get(order.buildingPos());
            // Removals and repairs need no skill beyond a level 1 builder's hut.
            int needed = order.kind() == WorkOrder.Kind.BUILD ? order.targetLevel() : 1;
            boolean orphan = target == null && !order.isDecoration() && order.kind() != WorkOrder.Kind.REMOVE;
            if (order.builder() != null || orphan || needed > maxLevel || !HutSettings.builderTakes(builderHut, order)) {
                continue;
            }
            if (best == null || (target != null && target.type() == BuildingType.BUILDER && !isBuilderHutOrder(best))) {
                best = order;
            }
        }
        if (best != null) {
            best.setBuilder(builderHut.pos());
            markDirty();
        }
        return best;
    }

    private boolean isBuilderHutOrder(WorkOrder order) {
        BuildingData target = order.isDecoration() ? null : buildings.get(order.buildingPos());
        return target != null && target.type() == BuildingType.BUILDER;
    }

    /**
     * Moves a hut to {@code to}: the hut block, its contents and everything that refers to it
     * (workers, residents, work orders) go to the new spot at once. A built hut keeps its level;
     * builders then clear the old site and rebuild the hut's current level at the new one.
     */
    public BuildingData moveBuilding(ServerLevel level, BuildingData building, BlockPos to, Direction facing) {
        BlockPos from = building.pos();
        Direction oldFacing = dev.pikachux.hearthstead.blueprint.Blueprints.facing(level, from);
        // Take the hut's contents along.
        List<ItemStack> contents = new ArrayList<>();
        if (level.getBlockEntity(from) instanceof dev.pikachux.hearthstead.block.HutBlockEntity hut) {
            for (int i = 0; i < hut.getContainerSize(); i++) {
                contents.add(hut.removeItemNoUpdate(i));
            }
        }
        BuildingData moved = building.movedTo(to);
        buildings.remove(from);
        buildings.put(to, moved);
        for (CitizenData citizen : citizens.values()) {
            if (from.equals(citizen.workPos())) {
                citizen.assignJob(citizen.job(), to);
            }
            if (from.equals(citizen.homePos())) {
                citizen.setHomePos(to);
            }
        }
        List<WorkOrder> kept = new ArrayList<>();
        for (WorkOrder order : workOrders) {
            if (!order.isDecoration() && order.buildingPos().equals(from)) {
                // A pending order follows the hut (unclaimed, since the site changed).
                kept.add(order.kind() == WorkOrder.Kind.REPAIR ? WorkOrder.repair(to, order.targetLevel())
                        : order.kind() == WorkOrder.Kind.BUILD ? new WorkOrder(to, order.targetLevel()) : order);
                continue;
            }
            if (from.equals(order.builder())) {
                order.setBuilder(to);
            }
            kept.add(order);
        }
        workOrders.clear();
        workOrders.addAll(kept);
        // The old block goes first; with no building left there, removing it changes nothing else.
        level.setBlock(from, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), net.minecraft.world.level.block.Block.UPDATE_ALL);
        dev.pikachux.hearthstead.block.HutBlock block = dev.pikachux.hearthstead.registry.ModBlocks.HUTS.get(building.type());
        level.setBlock(to, block.defaultBlockState().setValue(dev.pikachux.hearthstead.block.HutBlock.FACING, facing),
                net.minecraft.world.level.block.Block.UPDATE_ALL);
        if (level.getBlockEntity(to) instanceof dev.pikachux.hearthstead.block.HutBlockEntity hut) {
            hut.setColonyId(id);
            for (int i = 0; i < contents.size() && i < hut.getContainerSize(); i++) {
                hut.setItem(i, contents.get(i));
            }
        }
        if (moved.level() > 0 && workOrder(to) == null) {
            workOrders.add(WorkOrder.removal(from, building.type(), building.style(), building.level(), oldFacing, false));
            workOrders.add(WorkOrder.repair(to, moved.level()));
        }
        markDirty();
        return moved;
    }

    // ---------------------------------------------------------------- fields

    /** How far a field reaches from its scarecrow in each direction (a 9 by 9 field). */
    public static final int FIELD_RADIUS = 4;

    public List<BlockPos> fields() {
        return fields;
    }

    public void addField(BlockPos scarecrow) {
        if (!fields.contains(scarecrow)) {
            fields.add(scarecrow.immutable());
            markDirty();
        }
    }

    public void removeField(BlockPos scarecrow) {
        if (fields.remove(scarecrow)) {
            markDirty();
        }
    }

    /**
     * The fields a farmer's hut works: each field goes to the nearest farmer's hut that still has
     * room (one field per hut level, at least one), in the order the fields were marked.
     */
    public List<BlockPos> fieldsOf(BuildingData farmerHut) {
        List<BuildingData> farms = buildings.values().stream().filter(b -> b.type() == BuildingType.FARMER).toList();
        Map<BlockPos, Integer> room = new java.util.HashMap<>();
        farms.forEach(f -> room.put(f.pos(), Math.max(1, f.level())));
        List<BlockPos> mine = new ArrayList<>();
        for (BlockPos field : fields) {
            BuildingData nearest = null;
            for (BuildingData farm : farms) {
                if (room.get(farm.pos()) > 0
                        && (nearest == null || farm.pos().distSqr(field) < nearest.pos().distSqr(field))) {
                    nearest = farm;
                }
            }
            if (nearest == null) {
                break;
            }
            room.merge(nearest.pos(), -1, Integer::sum);
            if (nearest.pos().equals(farmerHut.pos())) {
                mine.add(field);
            }
        }
        return mine;
    }

    // ---------------------------------------------------------------- decorations

    public List<Decoration> decorations() {
        return decorations;
    }

    @Nullable
    public Decoration decoration(BlockPos pos) {
        for (Decoration decoration : decorations) {
            if (decoration.pos().equals(pos)) {
                return decoration;
            }
        }
        return null;
    }

    public void addDecoration(Decoration decoration) {
        decorations.removeIf(d -> d.pos().equals(decoration.pos()));
        decorations.add(decoration);
        markDirty();
    }

    public long intactDecorations() {
        return decorations.stream().filter(Decoration::intact).count();
    }

    /** Happiness every citizen gets from the colony's best Mystical Site: 0.25 per level. */
    public double mysticalHappiness() {
        int best = buildings.values().stream().filter(b -> b.type() == BuildingType.MYSTICAL_SITE)
                .mapToInt(BuildingData::level).max().orElse(0);
        return MYSTICAL_HAPPINESS_PER_LEVEL * best;
    }

    public static final double MYSTICAL_HAPPINESS_PER_LEVEL = 0.25;

    /** A postbox order is done once its items have all arrived; the members get a message. */
    public void checkPostboxes(MinecraftServer server, ServerLevel level) {
        for (BuildingData postbox : buildings.values()) {
            if (postbox.type() != BuildingType.POSTBOX || postbox.orders().isEmpty() || !level.isLoaded(postbox.pos())) {
                continue;
            }
            var storage = Storage.containers(level, postbox);
            for (Map.Entry<String, Integer> order : List.copyOf(postbox.orders().entrySet())) {
                net.minecraft.world.item.Item item = Logistics.postboxItem(order.getKey());
                if (item == null || Storage.count(storage, s -> s.is(item)) >= order.getValue()) {
                    postbox.setOrder(order.getKey(), 0);
                    markDirty();
                    if (item != null) {
                        broadcast(server, Component.translatable("message.hearthstead.postbox_arrived", order.getValue(),
                                item.getDefaultInstance().getHoverName()).withStyle(net.minecraft.ChatFormatting.GREEN));
                    }
                }
            }
        }
    }

    /** Happiness every citizen gets from the colony's intact decorations. */
    public double decorationHappiness() {
        return Math.min(Decoration.MAX_HAPPINESS, Decoration.HAPPINESS * intactDecorations());
    }

    /**
     * Looks at every decoration in a loaded chunk: it counts while most of it stands, and is
     * forgotten once it has been torn down.
     */
    public void checkDecorations(ServerLevel level) {
        boolean changed = decorations.removeIf(d -> d.loaded(level) && d.completeness(level) < Decoration.REMOVED_SHARE);
        for (Decoration decoration : decorations) {
            if (decoration.loaded(level)) {
                boolean intact = decoration.completeness(level) >= Decoration.INTACT_SHARE;
                if (intact != decoration.intact()) {
                    decoration.setIntact(intact);
                    changed = true;
                }
            }
        }
        if (changed) {
            markDirty();
        }
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
        skillGainMultiplier = libraryBonus(level);
        happenings.tick(server, this, level);
        if (++decorationTimer >= 60) {
            decorationTimer = 0;
            checkDecorations(level);
        }
        checkPostboxes(server, level);

        growChildren(level);
        if (residents() < housingCapacity() && ++spawnTimer * 20 >= 20L * HearthsteadConfig.NEW_CITIZEN_INTERVAL.get() / (research.has(Research.WELCOME_WAGONS) ? 2 : 1)) {
            spawnTimer = 0;
            addNewCitizen(level);
        }

        for (CitizenData data : citizens.values()) {
            if (data.onExpedition()) {
                // Away on a tavern expedition: not in the world until they come back.
                continue;
            }
            Entity entity = data.entityUuid() == null ? null : level.getEntity(data.entityUuid());
            if (entity instanceof CitizenEntity && entity.isAlive()) {
                continue;
            }
            if (data.entityUuid() == null || gameTime - data.lastSeen() > 20L * HearthsteadConfig.RESPAWN_DELAY.get()) {
                spawnCitizen(level, data);
            }
        }
    }

    /**
     * Each morning: sick citizens get better once their illness has run its course, pass it on to
     * the people they live with, and healthy ones may fall ill.
     */
    public void dailyIllness(ServerLevel level, long day) {
        net.minecraft.util.RandomSource random = level.getRandom();
        List<CitizenData> residents = citizens.values().stream().filter(c -> !c.visitor()).toList();
        for (CitizenData citizen : residents) {
            if (citizen.sick() && day - citizen.illnessDay() >= citizen.illness().recoveryDays()) {
                Illness illness = citizen.illness();
                citizen.cure();
                broadcast(level.getServer(), Component.translatable("message.hearthstead.recovered", citizen.name(),
                        Component.translatable("illness.hearthstead." + illness.id())).withStyle(net.minecraft.ChatFormatting.GRAY));
            }
        }
        double contagion = dev.pikachux.hearthstead.config.HearthsteadConfig.CONTAGION_CHANCE.get();
        for (CitizenData sick : residents.stream().filter(CitizenData::sick).toList()) {
            for (CitizenData other : residents) {
                if (!other.sick() && sick.homePos() != null && sick.homePos().equals(other.homePos())
                        && random.nextDouble() < contagion) {
                    fallIll(level, other, sick.illness(), day);
                }
            }
        }
        double chance = dev.pikachux.hearthstead.config.HearthsteadConfig.ILLNESS_CHANCE.get()
                * (research.has(Research.HERBAL_REMEDIES) ? 0.5 : 1);
        for (CitizenData citizen : residents) {
            double odds = chance * (citizen.saturation() <= 0 ? 2 : 1) * (citizen.homePos() == null ? 2 : 1);
            if (!citizen.sick() && random.nextDouble() < odds) {
                fallIll(level, citizen, Illness.values()[random.nextInt(Illness.values().length)], day);
            }
        }
        markDirty();
    }

    public void fallIll(ServerLevel level, CitizenData citizen, Illness illness, long day) {
        citizen.fallIll(illness, day);
        markDirty();
        broadcast(level.getServer(), Component.translatable("message.hearthstead.fell_ill", citizen.name(),
                Component.translatable("illness.hearthstead." + illness.id()),
                illness.cure().getDefaultInstance().getHoverName()).withStyle(net.minecraft.ChatFormatting.GOLD));
    }

    // ---------------------------------------------------------------- tavern expeditions

    /** Meals an expedition takes along. */
    public static final int EXPEDITION_FOOD = 6;
    /** Chance that an expedition comes back, with and without a sword. */
    public static final double EXPEDITION_SUCCESS = 0.85;
    public static final double EXPEDITION_SUCCESS_ARMED = 0.95;

    /**
     * Sends a visitor staying at the tavern on an expedition with food (and a sword, if there is
     * one) from the tavern. They come back the next morning with loot, or, rarely, not at all.
     * Returns null on success or a translation key describing the problem.
     */
    @Nullable
    public String sendExpedition(ServerLevel level, BuildingData tavern) {
        if (tavern.type() != BuildingType.TAVERN || tavern.level() == 0) {
            return "message.hearthstead.expedition_needs_tavern";
        }
        CitizenData visitor = citizens.values().stream().filter(c -> c.visitor() && !c.onExpedition()).findFirst().orElse(null);
        if (visitor == null) {
            return "message.hearthstead.expedition_no_visitor";
        }
        var storage = Storage.containers(level, tavern);
        if (Storage.count(storage, Logistics::isMeal) < EXPEDITION_FOOD) {
            return "message.hearthstead.expedition_needs_food";
        }
        Storage.extract(storage, Logistics::isMeal, EXPEDITION_FOOD);
        boolean armed = !Storage.extract(storage, s -> s.is(net.minecraft.tags.ItemTags.SWORDS), 1).isEmpty();
        long day = level.getDayTime() / 24000;
        visitor.startExpedition(day + 1, armed);
        visitor.setStatus(Status.of("away_on_expedition_back_tomorrow_morning"));
        if (visitor.entityUuid() != null && level.getEntity(visitor.entityUuid()) != null) {
            level.getEntity(visitor.entityUuid()).discard();
        }
        markDirty();
        broadcast(level.getServer(), Component.translatable("message.hearthstead.expedition_left", visitor.name())
                .withStyle(net.minecraft.ChatFormatting.AQUA));
        return null;
    }

    /** Each morning: expeditions due back return to the tavern with their loot, or are lost. */
    public void returnExpeditions(ServerLevel level, long day) {
        BuildingData tavern = trainingGround(BuildingType.TAVERN);
        for (CitizenData traveller : List.copyOf(citizens.values())) {
            if (!traveller.onExpedition() || day < traveller.expeditionReturnDay()) {
                continue;
            }
            boolean armed = traveller.expeditionArmed();
            traveller.endExpedition();
            if (level.getRandom().nextDouble() >= (armed ? EXPEDITION_SUCCESS_ARMED : EXPEDITION_SUCCESS)) {
                removeCitizen(traveller.id());
                broadcast(level.getServer(), Component.translatable("message.hearthstead.expedition_lost", traveller.name())
                        .withStyle(net.minecraft.ChatFormatting.RED));
                continue;
            }
            List<ItemStack> loot = expeditionLoot(tavern == null ? 1 : tavern.level(), armed, level.getRandom());
            BlockPos home = tavern != null ? tavern.pos() : center;
            var storage = tavern != null ? Storage.containers(level, tavern) : List.<net.minecraft.world.Container>of();
            for (ItemStack stack : loot) {
                ItemStack rest = Storage.insert(storage, stack.copy());
                if (!rest.isEmpty()) {
                    net.minecraft.world.entity.item.ItemEntity drop = new net.minecraft.world.entity.item.ItemEntity(level,
                            home.getX() + 0.5, home.getY() + 1, home.getZ() + 0.5, rest);
                    level.addFreshEntity(drop);
                }
            }
            // Back at the tavern for a few more days.
            traveller.setArrivalDay(day);
            spawnCitizen(level, traveller, home);
            String found = String.join(", ", loot.stream().map(s -> s.getCount() + "x " + s.getHoverName().getString()).toList());
            broadcast(level.getServer(), Component.translatable("message.hearthstead.expedition_back", traveller.name(), found)
                    .withStyle(net.minecraft.ChatFormatting.GREEN));
        }
    }

    /** What could turn up on an expedition: item, weight, smallest and largest count. */
    private record Find(net.minecraft.world.item.Item item, int weight, int min, int max) {
    }

    private static final List<Find> FINDS = List.of(
            new Find(net.minecraft.world.item.Items.EMERALD, 20, 1, 3),
            new Find(net.minecraft.world.item.Items.IRON_INGOT, 20, 2, 5),
            new Find(net.minecraft.world.item.Items.LEATHER, 12, 2, 6),
            new Find(net.minecraft.world.item.Items.GOLD_INGOT, 10, 1, 3),
            new Find(net.minecraft.world.item.Items.OAK_SAPLING, 6, 2, 4),
            new Find(net.minecraft.world.item.Items.CHERRY_SAPLING, 4, 1, 3),
            new Find(net.minecraft.world.item.Items.MELON_SEEDS, 5, 2, 6),
            new Find(net.minecraft.world.item.Items.PUMPKIN_SEEDS, 5, 2, 6),
            new Find(net.minecraft.world.item.Items.BOOK, 6, 1, 3),
            new Find(net.minecraft.world.item.Items.ENDER_PEARL, 5, 1, 2),
            new Find(net.minecraft.world.item.Items.SADDLE, 3, 1, 1),
            new Find(net.minecraft.world.item.Items.NAME_TAG, 3, 1, 1),
            new Find(net.minecraft.world.item.Items.GOLDEN_APPLE, 2, 1, 1),
            new Find(net.minecraft.world.item.Items.DIAMOND, 2, 1, 2));

    /** What an expedition brings back: 3 finds plus one per tavern level, and two more with a sword. */
    public static List<ItemStack> expeditionLoot(int tavernLevel, boolean armed, net.minecraft.util.RandomSource random) {
        int total = FINDS.stream().mapToInt(Find::weight).sum();
        List<ItemStack> loot = new ArrayList<>();
        int rolls = 3 + tavernLevel + (armed ? 2 : 0);
        for (int i = 0; i < rolls; i++) {
            int pick = random.nextInt(total);
            for (Find find : FINDS) {
                pick -= find.weight();
                if (pick < 0) {
                    int count = find.min() + random.nextInt(find.max() - find.min() + 1);
                    loot.stream().filter(s -> s.is(find.item()) && s.getCount() + count <= s.getMaxStackSize()).findFirst()
                            .ifPresentOrElse(s -> s.grow(count), () -> loot.add(new ItemStack(find.item(), count)));
                    break;
                }
            }
        }
        return loot;
    }

    /** Citizens who live in the colony: everyone but the tavern's visitors. */
    public int residents() {
        return (int) citizens.values().stream().filter(c -> !c.visitor()).count();
    }

    public boolean hasStaffed(BuildingType type) {
        return buildings.values().stream().anyMatch(b -> b.type() == type && b.level() > 0
                && citizens.values().stream().anyMatch(c -> b.pos().equals(c.workPos())));
    }

    private void growChildren(ServerLevel level) {
        boolean school = buildings.values().stream().anyMatch(b -> b.type() == BuildingType.SCHOOL && b.level() > 0
                && HutSettings.isOn(b, HutSettings.GROW_FASTER) && citizens.values().stream().anyMatch(c -> b.pos().equals(c.workPos())));
        int rate = school ? 2 : 1;
        for (CitizenData citizen : citizens.values()) {
            if (citizen.child() && citizen.grow(rate)) {
                broadcast(level.getServer(), Component.translatable("message.hearthstead.grown_up", citizen.name(), name)
                        .withStyle(net.minecraft.ChatFormatting.GREEN));
                markDirty();
            }
        }
    }

    /** A child is born in the residence, named after the family. */
    public CitizenData addChild(ServerLevel level, BuildingData home, String familyName) {
        CitizenData data = CitizenData.createChild(nextCitizenId++, level.getRandom(), familyName);
        data.setHomePos(home.pos());
        citizens.put(data.id(), data);
        spawnCitizen(level, data, home.pos());
        broadcast(level.getServer(), Component.translatable("message.hearthstead.child_born", data.name(), name)
                .withStyle(net.minecraft.ChatFormatting.LIGHT_PURPLE));
        markDirty();
        return data;
    }

    /** A visitor arrives at the tavern. */
    public CitizenData addVisitor(ServerLevel level, BuildingData tavern) {
        CitizenData data = CitizenData.createVisitor(nextCitizenId++, level.getRandom(), level.getDayTime() / 24000);
        data.setHomePos(tavern.pos());
        citizens.put(data.id(), data);
        spawnCitizen(level, data, tavern.pos());
        ItemStack cost = data.recruitCost();
        broadcast(level.getServer(), Component.translatable("message.hearthstead.visitor_arrived", data.name(), name,
                cost.getCount(), cost.getHoverName()).withStyle(net.minecraft.ChatFormatting.AQUA));
        markDirty();
        return data;
    }

    /** Records a citizen's death: the colony mourns, and a graveyard will bury them. */
    public void recordDeath(CitizenData data, long gameTime) {
        if (data.visitor()) {
            return;
        }
        dead.add(data);
        mourningUntil = Math.max(mourningUntil, gameTime + 24000);
        markDirty();
    }

    public List<CitizenData> dead() {
        return dead;
    }

    public List<String> fallen() {
        return fallen;
    }

    public boolean mourning(long gameTime) {
        return gameTime < mourningUntil;
    }

    /**
     * Buries the first of the dead. With a chance that grows with the graveyard level they come
     * back to life at the graveyard instead; once everyone is buried the mourning ends.
     * Returns the citizen brought back, or null.
     */
    @Nullable
    public CitizenData bury(ServerLevel level, BuildingData graveyard) {
        if (dead.isEmpty()) {
            return null;
        }
        CitizenData data = dead.removeFirst();
        CitizenData revived = null;
        if (HutSettings.isOn(graveyard, HutSettings.REVIVE) && level.getRandom().nextFloat() < 0.04F * graveyard.level()
                && residents() < housingCapacity()) {
            data.setEntityUuid(null);
            data.setHomePos(null);
            data.assignJob(JobType.UNEMPLOYED, null);
            citizens.put(data.id(), data);
            spawnCitizen(level, data, graveyard.pos());
            broadcast(level.getServer(), Component.translatable("message.hearthstead.revived", data.name(), name)
                    .withStyle(net.minecraft.ChatFormatting.GOLD));
            revived = data;
        } else {
            fallen.add(data.name());
            while (fallen.size() > 50) {
                fallen.removeFirst();
            }
            broadcast(level.getServer(), Component.translatable("message.hearthstead.buried", data.name(), name)
                    .withStyle(net.minecraft.ChatFormatting.GRAY));
        }
        if (dead.isEmpty()) {
            mourningUntil = 0;
        }
        markDirty();
        return revived;
    }

    /** The visitor joins the colony, if there is a free bed. Returns false otherwise. */
    public boolean recruit(CitizenData visitor) {
        if (!visitor.visitor() || residents() >= housingCapacity()) {
            return false;
        }
        visitor.recruit();
        markDirty();
        return true;
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
        spawnCitizen(level, data, null);
    }

    /** Spawns the citizen's entity next to {@code near}, or at the town hall. */
    private void spawnCitizen(ServerLevel level, CitizenData data, @Nullable BlockPos near) {
        CitizenEntity entity = ModEntities.CITIZEN.create(level, EntitySpawnReason.MOB_SUMMONED);
        if (entity == null) {
            return;
        }
        BlockPos spawn = near == null ? findSpawnPos(level)
                : level.getHeightmapPos(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, near.offset(0, 0, 2));
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
            if (home != null && !citizen.visitor()) {
                BuildingData building = buildings.get(home);
                if (building == null || housing(building) <= occupancy.getOrDefault(home, 0)) {
                    citizen.setHomePos(null);
                } else {
                    occupancy.merge(home, 1, Integer::sum);
                }
            }
        }
        for (CitizenData citizen : citizens.values()) {
            if (citizen.homePos() != null || citizen.visitor()) {
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
                if (citizen.job() == JobType.UNEMPLOYED && !citizen.child() && !citizen.visitor()) {
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
        ListTag deadList = new ListTag();
        dead.forEach(c -> deadList.add(c.save()));
        tag.put("dead", deadList);
        tag.putLong("mourningUntil", mourningUntil);
        ListTag fallenList = new ListTag();
        fallen.forEach(n -> fallenList.add(net.minecraft.nbt.StringTag.valueOf(n)));
        tag.put("fallen", fallenList);
        tag.put("citizens", citizenList);
        ListTag orderList = new ListTag();
        workOrders.forEach(o -> orderList.add(o.save()));
        tag.put("workOrders", orderList);
        ListTag fieldList = new ListTag();
        fields.forEach(f -> fieldList.add(net.minecraft.nbt.LongTag.valueOf(f.asLong())));
        tag.put("fields", fieldList);
        ListTag decorationList = new ListTag();
        decorations.forEach(d -> decorationList.add(d.save()));
        tag.put("decorations", decorationList);
        tag.put("raid", raid.save());
        tag.put("events", happenings.save());
        tag.put("permissions", permissions.save());
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
        colony.happenings.load(tag.getCompoundOrEmpty("events"));
        colony.permissions.load(tag.getCompoundOrEmpty("permissions"));
        colony.research.load(tag.getCompoundOrEmpty("research"));
        colony.style = tag.getStringOr("style", dev.pikachux.hearthstead.blueprint.Styles.CLASSIC);
        tag.getListOrEmpty("workOrders").compoundStream().map(WorkOrder::load).forEach(colony.workOrders::add);
        tag.getListOrEmpty("decorations").compoundStream().map(Decoration::load).forEach(colony.decorations::add);
        tag.getListOrEmpty("fields").forEach(t -> t.asLong().ifPresent(l -> colony.fields.add(BlockPos.of(l))));
        colony.nextCitizenId = tag.getIntOr("nextCitizenId", colony.citizens.size() + 1);
        tag.getListOrEmpty("dead").forEach(t -> t.asCompound().ifPresent(c -> colony.dead.add(CitizenData.load(c))));
        colony.mourningUntil = tag.getLongOr("mourningUntil", 0);
        tag.getListOrEmpty("fallen").forEach(t -> t.asString().ifPresent(colony.fallen::add));
        return colony;
    }
}
