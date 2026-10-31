package dev.pikachux.hearthstead.entity;

import dev.pikachux.hearthstead.config.HearthsteadConfig;
import dev.pikachux.hearthstead.Hearthstead;
import dev.pikachux.hearthstead.colony.CitizenData;
import dev.pikachux.hearthstead.colony.Colony;
import dev.pikachux.hearthstead.colony.ColonyResearch;
import dev.pikachux.hearthstead.colony.Research;
import dev.pikachux.hearthstead.colony.ColonyManager;
import dev.pikachux.hearthstead.colony.JobType;
import dev.pikachux.hearthstead.entity.ai.BakerGoal;
import dev.pikachux.hearthstead.entity.ai.BeekeeperGoal;
import dev.pikachux.hearthstead.entity.ai.BuilderGoal;
import dev.pikachux.hearthstead.entity.ai.ChickenHerderGoal;
import dev.pikachux.hearthstead.entity.ai.CitizenEatGoal;
import dev.pikachux.hearthstead.entity.ai.ComposterGoal;
import dev.pikachux.hearthstead.entity.ai.ConcreteMixerGoal;
import dev.pikachux.hearthstead.entity.ai.CowhandGoal;
import dev.pikachux.hearthstead.entity.ai.DyerGoal;
import dev.pikachux.hearthstead.entity.ai.SawmillGoal;
import dev.pikachux.hearthstead.entity.ai.StonemasonGoal;
import dev.pikachux.hearthstead.entity.ai.SwineherdGoal;
import dev.pikachux.hearthstead.entity.ai.CookGoal;
import dev.pikachux.hearthstead.entity.ai.DeliverymanGoal;
import dev.pikachux.hearthstead.entity.ai.FishermanGoal;
import dev.pikachux.hearthstead.entity.ai.FloristGoal;
import dev.pikachux.hearthstead.entity.ai.GlassblowerGoal;
import dev.pikachux.hearthstead.entity.ai.CitizenSleepGoal;
import dev.pikachux.hearthstead.entity.ai.FarmerGoal;
import dev.pikachux.hearthstead.entity.ai.GuardPatrolGoal;
import dev.pikachux.hearthstead.entity.ai.LumberjackGoal;
import dev.pikachux.hearthstead.entity.ai.MinerGoal;
import dev.pikachux.hearthstead.entity.ai.RabbitHerderGoal;
import dev.pikachux.hearthstead.entity.ai.ResearcherGoal;
import dev.pikachux.hearthstead.entity.ai.ShepherdGoal;
import dev.pikachux.hearthstead.entity.ai.SmelterGoal;
import dev.pikachux.hearthstead.network.BuildingViews;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.AvoidEntityGoal;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.OpenDoorGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jetbrains.annotations.Nullable;

/**
 * A colonist. All persistent state lives in {@link CitizenData}; the entity mirrors
 * a few values to the client for rendering.
 */
public class CitizenEntity extends PathfinderMob {
    private static final EntityDataAccessor<Integer> DATA_SKIN =
            SynchedEntityData.defineId(CitizenEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<String> DATA_JOB =
            SynchedEntityData.defineId(CitizenEntity.class, EntityDataSerializers.STRING);

    private static final Identifier GUARD_HEALTH = Hearthstead.id("guard_health");
    private static final Identifier GUARD_ARMOR = Hearthstead.id("guard_armor");
    private static final Identifier GUARD_ATTACK = Hearthstead.id("guard_attack");

    /** Saturation lost per second. */
    private static final double HUNGER_RATE = 0.01;

    private final SimpleContainer inventory = new SimpleContainer(9);
    private int colonyId;
    private int citizenId;

    public CitizenEntity(EntityType<? extends PathfinderMob> type, Level level) {
        super(type, level);
        setPersistenceRequired();
        if (getNavigation() instanceof GroundPathNavigation navigation) {
            navigation.setCanOpenDoors(true);
        }
    }

    public static AttributeSupplier.Builder createAttributes() {
        return PathfinderMob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 20.0)
                .add(Attributes.MOVEMENT_SPEED, 0.3)
                .add(Attributes.ATTACK_DAMAGE, 3.0)
                .add(Attributes.FOLLOW_RANGE, 48.0);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_SKIN, 0);
        builder.define(DATA_JOB, JobType.UNEMPLOYED.id());
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(1, new MeleeAttackGoal(this, 1.1, true) {
            @Override
            public boolean canUse() {
                return job() == JobType.GUARD && super.canUse();
            }
        });
        goalSelector.addGoal(2, new AvoidEntityGoal<>(this, Monster.class, 8.0F, 0.8, 1.0, e -> job() != JobType.GUARD));
        goalSelector.addGoal(3, new CitizenEatGoal(this));
        goalSelector.addGoal(4, new CitizenSleepGoal(this));
        goalSelector.addGoal(5, new OpenDoorGoal(this, true));
        goalSelector.addGoal(6, new LumberjackGoal(this));
        goalSelector.addGoal(6, new FarmerGoal(this));
        goalSelector.addGoal(6, new MinerGoal(this));
        goalSelector.addGoal(6, new GuardPatrolGoal(this));
        goalSelector.addGoal(6, new BuilderGoal(this));
        goalSelector.addGoal(6, new DeliverymanGoal(this));
        goalSelector.addGoal(6, new CookGoal(this));
        goalSelector.addGoal(6, new FishermanGoal(this));
        goalSelector.addGoal(6, new BakerGoal(this));
        goalSelector.addGoal(6, new SmelterGoal(this));
        goalSelector.addGoal(6, new ShepherdGoal(this));
        goalSelector.addGoal(6, new ResearcherGoal(this));
        goalSelector.addGoal(6, new ComposterGoal(this));
        goalSelector.addGoal(6, new SawmillGoal(this));
        goalSelector.addGoal(6, new CowhandGoal(this));
        goalSelector.addGoal(6, new ChickenHerderGoal(this));
        goalSelector.addGoal(6, new SwineherdGoal(this));
        goalSelector.addGoal(6, new StonemasonGoal(this));
        goalSelector.addGoal(6, new FloristGoal(this));
        goalSelector.addGoal(6, new DyerGoal(this));
        goalSelector.addGoal(6, new BeekeeperGoal(this));
        goalSelector.addGoal(6, new RabbitHerderGoal(this));
        goalSelector.addGoal(6, new GlassblowerGoal(this));
        goalSelector.addGoal(6, new ConcreteMixerGoal(this));
        goalSelector.addGoal(8, new WaterAvoidingRandomStrollGoal(this, 0.6));
        goalSelector.addGoal(9, new LookAtPlayerGoal(this, Player.class, 6.0F));
        goalSelector.addGoal(10, new RandomLookAroundGoal(this));

        targetSelector.addGoal(1, new HurtByTargetGoal(this) {
            @Override
            public boolean canUse() {
                return job() == JobType.GUARD && super.canUse();
            }
        });
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Monster.class, 10, true, false,
                (target, level) -> job() == JobType.GUARD && target instanceof Enemy));
    }

    // ---------------------------------------------------------------- colony binding

    public void bind(Colony colony, CitizenData data) {
        this.colonyId = colony.id();
        this.citizenId = data.id();
        syncFromData(data);
    }

    @Nullable
    public Colony colony() {
        if (!(level() instanceof ServerLevel serverLevel)) {
            return null;
        }
        return ColonyManager.get(serverLevel.getServer()).colony(colonyId);
    }

    @Nullable
    public CitizenData data() {
        Colony colony = colony();
        return colony == null ? null : colony.citizen(citizenId);
    }

    public JobType job() {
        return JobType.byId(entityData.get(DATA_JOB));
    }

    public int skin() {
        return entityData.get(DATA_SKIN);
    }

    public SimpleContainer inventory() {
        return inventory;
    }

    public void setStatus(String status) {
        CitizenData data = data();
        if (data != null) {
            data.setStatus(status);
        }
    }

    private void syncFromData(CitizenData data) {
        Colony colony = colony();
        updateGuardTraining(data.job() == JobType.GUARD, colony == null ? null : colony.research());
        entityData.set(DATA_SKIN, data.skin());
        entityData.set(DATA_JOB, data.job().id());
        Component name = Component.literal(data.name())
                .append(Component.literal(" (" + data.job().displayName() + ")").withStyle(ChatFormatting.GRAY));
        if (!name.equals(getCustomName())) {
            setCustomName(name);
        }
        setCustomNameVisible(true);
    }

    @Override
    protected void customServerAiStep(ServerLevel level) {
        super.customServerAiStep(level);
        if (tickCount % 20 != 0) {
            return;
        }
        Colony colony = colony();
        CitizenData data = colony == null ? null : colony.citizen(citizenId);
        if (data == null || !getUUID().equals(data.entityUuid())) {
            // Orphaned or duplicated citizen: the colony is the source of truth.
            discard();
            return;
        }
        data.setLastSeen(level.getGameTime());
        if (data.job() != job() && data.job() == JobType.UNEMPLOYED) {
            setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
        }
        syncFromData(data);

        data.setSaturation(data.saturation() - HUNGER_RATE * HearthsteadConfig.HUNGER_RATE.get() * (colony.research().has(Research.FEAST_DAYS) ? 0.7 : 1.0));
        double happiness = 5.0;
        happiness += data.homePos() != null ? 2 : -2;
        happiness += data.job() != JobType.UNEMPLOYED ? 1 : 0;
        happiness += colony.research().has(Research.COMMUNITY_SPIRIT) ? 1 : 0;
        happiness += data.saturation() > CitizenData.MAX_SATURATION / 2 ? 2 : (data.saturation() <= 0 ? -3 : 0);
        data.setHappiness(happiness);

        if (data.saturation() <= 0 && level.getGameTime() % 200 == 0) {
            hurtServer(level, damageSources().starve(), 1.0F);
        } else if (getHealth() < getMaxHealth() && data.saturation() > CitizenData.MAX_SATURATION / 2) {
            heal(0.5F);
        }
    }

    /** Guards are trained fighters: more health, armour and attack while they hold the job (more with research). */
    private void updateGuardTraining(boolean guard, @Nullable ColonyResearch research) {
        boolean drills = research != null && research.has(Research.MILITIA_DRILLS);
        boolean chainmail = research != null && research.has(Research.CHAINMAIL_TRAINING);
        apply(Attributes.MAX_HEALTH, GUARD_HEALTH, 20.0, guard);
        apply(Attributes.ARMOR, GUARD_ARMOR, chainmail ? 10.0 : 6.0, guard);
        apply(Attributes.ATTACK_DAMAGE, GUARD_ATTACK, drills ? 4.0 : 2.0, guard);
        if (getHealth() > getMaxHealth()) {
            setHealth(getMaxHealth());
        }
    }

    private void apply(Holder<Attribute> attribute, Identifier id, double amount, boolean active) {
        AttributeInstance instance = getAttribute(attribute);
        if (instance == null) {
            return;
        }
        if (active) {
            instance.addOrUpdateTransientModifier(new AttributeModifier(id, amount, AttributeModifier.Operation.ADD_VALUE));
        } else {
            instance.removeModifier(id);
        }
    }

    /** Multiplier applied to work speed based on happiness. */
    public double workSpeed() {
        Colony colony = colony();
        CitizenData data = colony == null ? null : colony.citizen(citizenId);
        if (data == null) {
            return 1.0;
        }
        double speed = (0.5 + data.happiness() / 10.0) * HearthsteadConfig.WORK_SPEED.get();
        if (colony.research().has(Research.APPRENTICESHIP)) {
            speed *= 1.10;
        }
        if (colony.research().has(Research.MASTER_CRAFTSMEN)) {
            speed *= 1.15;
        }
        return speed;
    }

    @Override
    protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (player instanceof ServerPlayer serverPlayer && hand == InteractionHand.MAIN_HAND) {
            CitizenData data = data();
            if (data != null) {
                BuildingViews.sendCitizen(serverPlayer, this, data);
            }
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public void die(DamageSource source) {
        if (level() instanceof ServerLevel serverLevel) {
            Colony colony = colony();
            CitizenData data = colony == null ? null : colony.citizen(citizenId);
            if (data != null && getUUID().equals(data.entityUuid())) {
                colony.broadcast(serverLevel.getServer(), Component.translatable("message.hearthstead.citizen_died",
                        data.name(), source.getLocalizedDeathMessage(this)).withStyle(ChatFormatting.RED));
                colony.removeCitizen(citizenId);
                for (int i = 0; i < inventory.getContainerSize(); i++) {
                    spawnAtLocation(serverLevel, inventory.removeItemNoUpdate(i));
                }
            }
        }
        super.die(source);
    }

    @Override
    public boolean removeWhenFarAway(double distance) {
        return false;
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.putInt("colony", colonyId);
        output.putInt("citizen", citizenId);
        ContainerHelper.saveAllItems(output.child("inventory"), inventory.getItems());
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        colonyId = input.getIntOr("colony", 0);
        citizenId = input.getIntOr("citizen", 0);
        ContainerHelper.loadAllItems(input.childOrEmpty("inventory"), inventory.getItems());
    }
}
