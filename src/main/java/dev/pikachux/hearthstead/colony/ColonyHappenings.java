package dev.pikachux.hearthstead.colony;

import dev.pikachux.hearthstead.config.HearthsteadConfig;
import it.unimi.dsi.fastutil.ints.IntList;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.wanderingtrader.WanderingTrader;
import net.minecraft.world.entity.projectile.FireworkRocketEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.FireworkExplosion;
import net.minecraft.world.item.component.Fireworks;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.List;

/**
 * Colony events, rolled each morning: a wandering merchant visiting the town hall for the day,
 * and every few days a harvest festival that makes citizens happier until nightfall.
 */
public class ColonyHappenings {
    private static final long DAY = 24000;
    private static final long DUSK = 13000;
    /** Citizens a colony needs before events happen on their own. */
    public static final int MIN_CITIZENS = 3;
    /** Happiness every citizen gains during a festival. */
    public static final double FESTIVAL_HAPPINESS = 2.0;
    private static final int[] FESTIVE_COLORS = {0xF5B942, 0xC9822B, 0xFF4D6D, 0x5FD4FF, 0x8BE38B};

    private long lastMorning = -1;
    /** Game time at which the current festival ends. */
    private long festivalUntil;
    private int fireworkTimer;

    public boolean festival(long gameTime) {
        return gameTime < festivalUntil;
    }

    void tick(MinecraftServer server, Colony colony, ServerLevel level) {
        long time = level.getDayTime();
        long day = time / DAY;
        if (time % DAY < 1000 && day != lastMorning) {
            lastMorning = day;
            colony.markDirty();
            if (colony.citizens().size() >= MIN_CITIZENS) {
                int interval = HearthsteadConfig.FESTIVAL_INTERVAL.get();
                if (interval > 0 && day > 0 && day % interval == 0) {
                    startFestival(server, colony, level);
                }
                double merchant = HearthsteadConfig.MERCHANT_CHANCE.get() * (colony.research().has(Research.MARKET_DAYS) ? 3 : 1);
                if (level.getRandom().nextDouble() < merchant) {
                    sendMerchant(server, colony, level);
                }
                if (level.getRandom().nextDouble() < HearthsteadConfig.BIRTH_CHANCE.get()) {
                    tryBirth(colony, level);
                }
            }
            sendVisitors(colony, level, day);
            colony.dailyIllness(level, day);
        }
        // A rocket now and then while the festival lasts (the colony ticks once a second).
        if (festival(level.getGameTime()) && ++fireworkTimer >= 30) {
            fireworkTimer = 0;
            launchFireworks(level, colony.center(), 1);
        }
    }

    /** Visitors leave after this many days at the tavern. */
    public static final int VISITOR_DAYS = 3;

    /** A child is born in a residence where at least two adults live, if the colony has a free bed. */
    public boolean tryBirth(Colony colony, ServerLevel level) {
        if (colony.residents() >= colony.housingCapacity()) {
            return false;
        }
        for (BuildingData home : colony.buildings()) {
            if (home.type() != BuildingType.RESIDENCE || home.level() == 0) {
                continue;
            }
            List<CitizenData> adults = colony.citizens().stream()
                    .filter(c -> home.pos().equals(c.homePos()) && !c.child() && !c.visitor()).toList();
            if (adults.size() >= 2) {
                colony.addChild(level, home, adults.get(level.getRandom().nextInt(adults.size())).familyName());
                return true;
            }
        }
        return false;
    }

    /** Visitors who stayed too long leave; a new one may arrive at a tavern with room. */
    private void sendVisitors(Colony colony, ServerLevel level, long day) {
        for (CitizenData visitor : List.copyOf(colony.citizens())) {
            if (visitor.visitor() && day - visitor.arrivalDay() >= VISITOR_DAYS) {
                if (visitor.entityUuid() != null && level.getEntity(visitor.entityUuid()) != null) {
                    level.getEntity(visitor.entityUuid()).discard();
                }
                colony.removeCitizen(visitor.id());
                colony.broadcast(level.getServer(), Component.translatable("message.hearthstead.visitor_left", visitor.name(), colony.name())
                        .withStyle(ChatFormatting.GRAY));
            }
        }
        BuildingData tavern = colony.trainingGround(BuildingType.TAVERN);
        if (tavern != null && visitorCount(colony) < tavern.level()
                && level.getRandom().nextDouble() < HearthsteadConfig.VISITOR_CHANCE.get()) {
            colony.addVisitor(level, tavern);
        }
    }

    public static int visitorCount(Colony colony) {
        return (int) colony.citizens().stream().filter(CitizenData::visitor).count();
    }

    /** Starts a harvest festival that lasts until nightfall (or half a day when started at night). */
    public boolean startFestival(MinecraftServer server, Colony colony, ServerLevel level) {
        long timeOfDay = level.getDayTime() % DAY;
        long length = timeOfDay < DUSK ? DUSK - timeOfDay : DAY / 2;
        festivalUntil = level.getGameTime() + length;
        colony.markDirty();
        launchFireworks(level, colony.center(), 5);
        colony.broadcast(server, Component.literal("✿ ").withStyle(ChatFormatting.GOLD)
                .append(Component.translatable("message.hearthstead.festival", colony.name()).withStyle(ChatFormatting.YELLOW)));
        return true;
    }

    /** A wandering merchant arrives next to the town hall and stays for a day. */
    public boolean sendMerchant(MinecraftServer server, Colony colony, ServerLevel level) {
        RandomSource random = level.getRandom();
        BlockPos center = colony.center();
        BlockPos spot = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                center.offset(random.nextInt(7) - 3, 0, 3 + random.nextInt(3)));
        if (!level.isLoaded(spot) || !level.getFluidState(spot).isEmpty()) {
            return false;
        }
        WanderingTrader trader = EntityType.WANDERING_TRADER.create(level, EntitySpawnReason.EVENT);
        if (trader == null) {
            return false;
        }
        trader.snapTo(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5, random.nextFloat() * 360F, 0);
        trader.setDespawnDelay((int) DAY);
        trader.setWanderTarget(center);
        trader.finalizeSpawn(level, level.getCurrentDifficultyAt(spot), EntitySpawnReason.EVENT, null);
        level.addFreshEntity(trader);
        colony.broadcast(server, Component.literal("☀ ").withStyle(ChatFormatting.AQUA)
                .append(Component.translatable("message.hearthstead.merchant", colony.name()).withStyle(ChatFormatting.AQUA)));
        return true;
    }

    private static void launchFireworks(ServerLevel level, BlockPos center, int count) {
        RandomSource random = level.getRandom();
        for (int i = 0; i < count; i++) {
            int color = FESTIVE_COLORS[random.nextInt(FESTIVE_COLORS.length)];
            FireworkExplosion explosion = new FireworkExplosion(
                    random.nextBoolean() ? FireworkExplosion.Shape.LARGE_BALL : FireworkExplosion.Shape.STAR,
                    IntList.of(color), IntList.of(0xFFFFFF), true, random.nextBoolean());
            ItemStack rocket = new ItemStack(Items.FIREWORK_ROCKET);
            rocket.set(DataComponents.FIREWORKS, new Fireworks(1 + random.nextInt(2), List.of(explosion)));
            BlockPos at = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING, center.offset(random.nextInt(9) - 4, 0, random.nextInt(9) - 4));
            level.addFreshEntity(new FireworkRocketEntity(level, at.getX() + 0.5, at.getY() + 0.5, at.getZ() + 0.5, rocket));
        }
    }

    CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putLong("lastMorning", lastMorning);
        tag.putLong("festivalUntil", festivalUntil);
        return tag;
    }

    void load(CompoundTag tag) {
        lastMorning = tag.getLongOr("lastMorning", -1);
        festivalUntil = tag.getLongOr("festivalUntil", 0);
    }
}
