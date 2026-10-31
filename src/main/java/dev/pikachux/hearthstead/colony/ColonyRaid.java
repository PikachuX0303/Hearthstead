package dev.pikachux.hearthstead.colony;

import dev.pikachux.hearthstead.config.HearthsteadConfig;
import dev.pikachux.hearthstead.entity.raider.AbstractBarbarian;
import dev.pikachux.hearthstead.registry.ModEntities;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.BossEvent;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.levelgen.Heightmap;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Barbarian raids on a colony. At nightfall a colony with enough citizens may be
 * raided; the raiders spawn at the edge of the colony and march on the town hall.
 * The raid ends when every raider is dead, or at sunrise when the survivors retreat
 * (raids started by command during the day last ten minutes).
 */
public class ColonyRaid {
    /** Who raids: barbarians come over land, pirates land on the shore. */
    public enum Culture {
        BARBARIANS("barbarians"), PIRATES("pirates");

        private final String id;

        Culture(String id) {
            this.id = id;
        }

        public String id() {
            return id;
        }

        public static Culture byId(String id) {
            for (Culture culture : values()) {
                if (culture.id.equals(id)) {
                    return culture;
                }
            }
            return BARBARIANS;
        }

        /** Suffix of the culture's chat and boss bar messages ("" for barbarians). */
        String messageSuffix() {
            return this == PIRATES ? "_pirates" : "";
        }
    }

    /** Where pirates land, and the water their boats lie on. */
    private record Shore(BlockPos land, BlockPos water) {
    }

    private static final long DAY = 24000;
    private static final long DUSK = 13000;
    private static final long DAWN = 23500;
    /** How long a raid started during the day (by command) lasts. */
    private static final long DAYTIME_RAID_LENGTH = 20 * 60 * 10;

    private boolean enabled = true;
    private Culture culture = Culture.BARBARIANS;
    /** The pirates' boats, removed when the raid ends. */
    private final Set<UUID> boats = new HashSet<>();
    private boolean active;
    private int total;
    private final Set<UUID> raiders = new HashSet<>();
    private int quietNights;
    private long lastNight = -1;
    /** Game time at which surviving raiders retreat. */
    private long endTime;
    @Nullable
    private ServerBossEvent bossBar;

    public boolean enabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean active() {
        return active;
    }

    public int remaining() {
        return raiders.size();
    }

    public int total() {
        return total;
    }

    public Culture culture() {
        return culture;
    }

    // ---------------------------------------------------------------- ticking

    void tick(MinecraftServer server, Colony colony, ServerLevel level) {
        long time = level.getDayTime();
        long timeOfDay = time % DAY;
        if (active) {
            raiders.removeIf(uuid -> !(level.getEntity(uuid) instanceof AbstractBarbarian raider) || !raider.isAlive());
            if (raiders.isEmpty()) {
                end(server, colony, true);
            } else if (level.getGameTime() >= endTime) {
                retreat(server, colony, level);
            } else {
                updateBossBar(server, colony, level);
            }
            return;
        }
        long night = time / DAY;
        if (enabled && HearthsteadConfig.RAIDS_ENABLED.get() && timeOfDay >= DUSK && timeOfDay < DUSK + 1000 && night != lastNight) {
            lastNight = night;
            colony.markDirty();
            if (canBeRaided(colony, level) && level.getRandom().nextDouble() < HearthsteadConfig.RAID_CHANCE.get()
                    + HearthsteadConfig.RAID_CHANCE_INCREASE.get() * quietNights) {
                start(server, colony, level);
            } else {
                quietNights++;
            }
        }
    }

    private static boolean canBeRaided(Colony colony, ServerLevel level) {
        return level.getDifficulty() != Difficulty.PEACEFUL && colony.citizens().size() >= HearthsteadConfig.RAID_MIN_CITIZENS.get();
    }

    // ---------------------------------------------------------------- start and end

    /** Starts a raid now, by pirates or barbarians as the colony's surroundings allow. */
    public boolean start(MinecraftServer server, Colony colony, ServerLevel level) {
        return start(server, colony, level, null);
    }

    /**
     * Starts a raid now. {@code forced} picks the raiders (pirates fall back to landing anywhere
     * when there is no shore); otherwise colonies near water may get pirates. Returns false if no
     * spawn point was found.
     */
    public boolean start(MinecraftServer server, Colony colony, ServerLevel level, @Nullable Culture forced) {
        if (active) {
            return false;
        }
        RandomSource random = level.getRandom();
        Shore shore = forced == Culture.BARBARIANS || (forced == null && !HearthsteadConfig.PIRATES.get())
                ? null : findShore(colony, level, random);
        Culture chosen = forced != null ? forced
                : shore != null && random.nextDouble() < HearthsteadConfig.PIRATE_CHANCE.get() ? Culture.PIRATES : Culture.BARBARIANS;
        BlockPos spawn = chosen == Culture.PIRATES && shore != null ? shore.land() : findSpawn(colony, level, random);
        if (spawn == null) {
            return false;
        }
        culture = chosen;
        int citizens = colony.citizens().size();
        int size = Math.min(HearthsteadConfig.RAID_MAX_RAIDERS.get(), 2 + citizens / 2);
        if (colony.research().has(Research.STONE_WALLS)) {
            size = Math.max(1, size * 3 / 4);
        }
        int archers = citizens >= 6 ? size / 3 : 0;
        boolean chief = citizens >= 10;

        boolean pirates = culture == Culture.PIRATES;
        List<EntityType<? extends AbstractBarbarian>> types = new ArrayList<>();
        if (chief) {
            types.add(pirates ? ModEntities.PIRATE_CAPTAIN : ModEntities.CHIEF_BARBARIAN);
        }
        for (int i = 0; i < archers; i++) {
            types.add(pirates ? ModEntities.PIRATE_ARCHER : ModEntities.ARCHER_BARBARIAN);
        }
        while (types.size() < size) {
            types.add(pirates ? ModEntities.PIRATE : ModEntities.BARBARIAN);
        }

        raiders.clear();
        for (EntityType<? extends AbstractBarbarian> type : types) {
            BlockPos pos = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                    spawn.offset(random.nextInt(7) - 3, 0, random.nextInt(7) - 3));
            // Next to a shore the scattered spot may be water: gather on the spawn point instead.
            if (!level.getFluidState(pos).isEmpty() || !level.getFluidState(pos.below()).isEmpty()) {
                pos = spawn;
            }
            AbstractBarbarian raider = type.create(level, EntitySpawnReason.EVENT);
            if (raider == null) {
                continue;
            }
            raider.snapTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, random.nextFloat() * 360F, 0);
            raider.finalizeSpawn(level, level.getCurrentDifficultyAt(pos), EntitySpawnReason.EVENT, null);
            raider.setColony(colony);
            level.addFreshEntity(raider);
            raiders.add(raider.getUUID());
        }
        if (raiders.isEmpty()) {
            return false;
        }
        if (pirates && shore != null) {
            beachBoats(level, shore, random);
        }
        active = true;
        total = raiders.size();
        long timeOfDay = level.getDayTime() % DAY;
        endTime = level.getGameTime() + (timeOfDay >= DUSK && timeOfDay < DAWN ? DAWN - timeOfDay : DAYTIME_RAID_LENGTH);
        quietNights = 0;
        colony.markDirty();

        String direction = direction(colony.center(), spawn);
        colony.broadcast(server, Component.literal("⚔ ").withStyle(ChatFormatting.DARK_RED)
                .append(Component.translatable("message.hearthstead.raid_start" + culture.messageSuffix(), total, colony.name(),
                        Component.translatable("direction.hearthstead." + direction)).withStyle(ChatFormatting.RED)));
        for (UUID member : members(colony)) {
            ServerPlayer player = server.getPlayerList().getPlayer(member);
            if (player != null) {
                level.playSound(null, player.blockPosition(), SoundEvents.RAID_HORN.value(), SoundSource.HOSTILE, 4.0F, 1.0F);
            }
        }
        updateBossBar(server, colony, level);
        return true;
    }

    /** Called when a raider dies. */
    public void onRaiderDeath(MinecraftServer server, Colony colony, AbstractBarbarian raider) {
        if (raiders.remove(raider.getUUID())) {
            colony.markDirty();
            if (raiders.isEmpty()) {
                end(server, colony, true);
            } else {
                ServerLevel level = colony.level(server);
                if (level != null) {
                    updateBossBar(server, colony, level);
                }
            }
        }
    }

    private void retreat(MinecraftServer server, Colony colony, ServerLevel level) {
        for (UUID uuid : raiders) {
            Entity entity = level.getEntity(uuid);
            if (entity != null) {
                entity.discard();
            }
        }
        end(server, colony, false);
    }

    /** Ends the raid; operators can also stop one with a command. */
    public void stop(MinecraftServer server, Colony colony) {
        ServerLevel level = colony.level(server);
        if (active && level != null) {
            retreat(server, colony, level);
        }
    }

    private void end(MinecraftServer server, Colony colony, boolean victory) {
        active = false;
        raiders.clear();
        total = 0;
        if (bossBar != null) {
            bossBar.removeAllPlayers();
            bossBar = null;
        }
        colony.markDirty();
        ServerLevel level = colony.level(server);
        for (UUID uuid : boats) {
            Entity boat = level == null ? null : level.getEntity(uuid);
            if (boat != null) {
                boat.discard();
            }
        }
        boats.clear();
        colony.broadcast(server, Component.translatable(victory ? "message.hearthstead.raid_won"
                : "message.hearthstead.raid_retreat" + culture.messageSuffix(), colony.name())
                .withStyle(victory ? ChatFormatting.GREEN : ChatFormatting.GOLD));
    }

    private void updateBossBar(MinecraftServer server, Colony colony, ServerLevel level) {
        if (bossBar == null) {
            bossBar = new ServerBossEvent(Component.empty(), BossEvent.BossBarColor.RED, BossEvent.BossBarOverlay.NOTCHED_10);
            bossBar.setDarkenScreen(true);
        }
        bossBar.setName(Component.translatable("bossbar.hearthstead.raid" + culture.messageSuffix(), colony.name(), raiders.size()));
        bossBar.setProgress(total == 0 ? 0 : Math.min(1F, (float) raiders.size() / total));
        for (ServerPlayer player : level.players()) {
            boolean show = colony.isInside(level.dimension(), player.blockPosition());
            if (show && !bossBar.getPlayers().contains(player)) {
                bossBar.addPlayer(player);
            } else if (!show && bossBar.getPlayers().contains(player)) {
                bossBar.removePlayer(player);
            }
        }
    }

    // ---------------------------------------------------------------- helpers

    @Nullable
    private static BlockPos findSpawn(Colony colony, ServerLevel level, RandomSource random) {
        int distance = ColonyManager.colonyRadius() - 12;
        for (int attempt = 0; attempt < 16; attempt++) {
            double angle = random.nextDouble() * Math.PI * 2;
            BlockPos column = colony.center().offset((int) (Math.cos(angle) * distance), 0, (int) (Math.sin(angle) * distance));
            if (!level.isLoaded(column)) {
                continue;
            }
            BlockPos ground = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, column);
            if (level.getFluidState(ground.below()).isEmpty() && level.getFluidState(ground).isEmpty()) {
                return ground;
            }
        }
        return null;
    }

    /**
     * A spot on the shore near the colony's edge: land next to open water. Pirates gather there
     * and leave their boats on the water.
     */
    @Nullable
    private static Shore findShore(Colony colony, ServerLevel level, RandomSource random) {
        int radius = ColonyManager.colonyRadius();
        BlockPos center = colony.center();
        for (int attempt = 0; attempt < 32; attempt++) {
            double angle = random.nextDouble() * Math.PI * 2;
            int distance = radius - 8 - random.nextInt(Math.max(1, radius / 2));
            BlockPos column = center.offset((int) (Math.cos(angle) * distance), 0, (int) (Math.sin(angle) * distance));
            if (!level.isLoaded(column)) {
                continue;
            }
            BlockPos surface = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING, column);
            if (!level.getFluidState(surface.below()).is(net.minecraft.tags.FluidTags.WATER)) {
                continue;
            }
            // Walk from the water towards the town hall until there is dry land.
            double dx = center.getX() - column.getX();
            double dz = center.getZ() - column.getZ();
            double length = Math.sqrt(dx * dx + dz * dz);
            for (int step = 1; step <= 16 && length > 0; step++) {
                BlockPos land = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                        column.offset((int) Math.round(dx / length * step), 0, (int) Math.round(dz / length * step)));
                if (level.isLoaded(land) && level.getFluidState(land.below()).isEmpty() && level.getFluidState(land).isEmpty()) {
                    return new Shore(land, surface.below());
                }
            }
        }
        return null;
    }

    /** Two boats pulled up at the water's edge where the pirates came ashore. */
    private void beachBoats(ServerLevel level, Shore shore, RandomSource random) {
        boats.clear();
        for (int i = 0; i < 2; i++) {
            var boat = EntityType.OAK_BOAT.create(level, EntitySpawnReason.EVENT);
            if (boat == null) {
                continue;
            }
            BlockPos water = shore.water().offset(random.nextInt(5) - 2, 0, random.nextInt(5) - 2);
            if (!level.getFluidState(water).is(net.minecraft.tags.FluidTags.WATER)) {
                water = shore.water();
            }
            boat.snapTo(water.getX() + 0.5, water.getY() + 1, water.getZ() + 0.5, random.nextFloat() * 360F, 0);
            level.addFreshEntity(boat);
            boats.add(boat.getUUID());
        }
    }

    private static String direction(BlockPos center, BlockPos target) {
        double angle = Math.toDegrees(Math.atan2(target.getX() - center.getX(), center.getZ() - target.getZ()));
        String[] names = {"north", "northeast", "east", "southeast", "south", "southwest", "west", "northwest"};
        int index = (int) Math.round(((angle + 360) % 360) / 45.0) % 8;
        return names[index];
    }

    private static List<UUID> members(Colony colony) {
        List<UUID> members = new ArrayList<>(colony.officers());
        members.add(colony.owner());
        return members;
    }

    // ---------------------------------------------------------------- persistence

    CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putBoolean("enabled", enabled);
        tag.putString("culture", culture.id());
        ListTag boatList = new ListTag();
        boats.forEach(uuid -> boatList.add(StringTag.valueOf(uuid.toString())));
        tag.put("boats", boatList);
        tag.putBoolean("active", active);
        tag.putInt("total", total);
        ListTag list = new ListTag();
        raiders.forEach(uuid -> list.add(StringTag.valueOf(uuid.toString())));
        tag.put("raiders", list);
        tag.putInt("quietNights", quietNights);
        tag.putLong("lastNight", lastNight);
        tag.putLong("endTime", endTime);
        return tag;
    }

    void load(CompoundTag tag) {
        enabled = tag.getBooleanOr("enabled", true);
        culture = Culture.byId(tag.getStringOr("culture", Culture.BARBARIANS.id()));
        boats.clear();
        tag.getListOrEmpty("boats").forEach(t -> t.asString().ifPresent(s -> boats.add(UUID.fromString(s))));
        active = tag.getBooleanOr("active", false);
        total = tag.getIntOr("total", 0);
        raiders.clear();
        tag.getListOrEmpty("raiders").forEach(t -> t.asString().ifPresent(s -> raiders.add(UUID.fromString(s))));
        quietNights = tag.getIntOr("quietNights", 0);
        lastNight = tag.getLongOr("lastNight", -1);
        endTime = tag.getLongOr("endTime", 0);
    }
}
