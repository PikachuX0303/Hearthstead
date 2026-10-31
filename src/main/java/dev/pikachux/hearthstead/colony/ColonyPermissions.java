package dev.pikachux.hearthstead.colony;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Who may do what inside a colony. The owner and officers may do everything; for friends and
 * visitors (everyone else) the owner chooses which {@link Action}s are allowed.
 */
public class ColonyPermissions {
    public enum Rank {
        OWNER, OFFICER, FRIEND, VISITOR;

        public String id() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    /** Things a player can do in a colony that the owner can allow or forbid. */
    public enum Action {
        /** Break blocks (hut blocks always need an officer). */
        BREAK,
        /** Place blocks and change them with items: tilling, stripping, buckets, bone meal, ... */
        BUILD,
        /** Use doors, trapdoors, gates, buttons, levers, beds and crafting stations. */
        USE_BLOCKS,
        /** Open chests, barrels, furnaces and other containers, and hut inventories. */
        CONTAINERS,
        /** Hurt citizens and animals. */
        HURT,
        /** Open hut windows to look at the colony (changing anything still needs an officer). */
        HUT_WINDOWS;

        public String id() {
            return name().toLowerCase(Locale.ROOT);
        }

        @Nullable
        public static Action byId(String id) {
            for (Action action : values()) {
                if (action.id().equals(id)) {
                    return action;
                }
            }
            return null;
        }
    }

    private final Colony colony;
    private final Set<UUID> friends = new LinkedHashSet<>();
    /** Names of members, remembered so they can be listed while they are offline. */
    private final Map<UUID, String> names = new HashMap<>();
    private final EnumSet<Action> friendRules = defaultFriendRules();
    private final EnumSet<Action> visitorRules = EnumSet.noneOf(Action.class);

    ColonyPermissions(Colony colony) {
        this.colony = colony;
    }

    private static EnumSet<Action> defaultFriendRules() {
        return EnumSet.of(Action.USE_BLOCKS, Action.CONTAINERS, Action.HUT_WINDOWS);
    }

    public Rank rank(UUID player) {
        if (player.equals(colony.owner())) {
            return Rank.OWNER;
        }
        if (colony.officers().contains(player)) {
            return Rank.OFFICER;
        }
        return friends.contains(player) ? Rank.FRIEND : Rank.VISITOR;
    }

    public Set<UUID> friends() {
        return friends;
    }

    /** Changes a player's rank (not the owner's); VISITOR removes them from the colony. */
    public void setRank(UUID player, String name, Rank rank) {
        if (player.equals(colony.owner()) || rank == Rank.OWNER) {
            return;
        }
        colony.officers().remove(player);
        friends.remove(player);
        switch (rank) {
            case OFFICER -> colony.officers().add(player);
            case FRIEND -> friends.add(player);
            default -> {
            }
        }
        if (rank == Rank.VISITOR) {
            names.remove(player);
        } else if (name != null && !name.isEmpty()) {
            names.put(player, name);
        }
        colony.markDirty();
    }

    public String name(UUID player) {
        if (player.equals(colony.owner())) {
            return colony.ownerName();
        }
        String name = names.get(player);
        return name != null ? name : player.toString().substring(0, 8);
    }

    /** Remembers a member's current name (they may have changed it). */
    public void rememberName(UUID player, String name) {
        if (rank(player) != Rank.VISITOR && !name.equals(names.get(player))) {
            names.put(player, name);
            colony.markDirty();
        }
    }

    public Set<Action> rules(Rank rank) {
        return switch (rank) {
            case OWNER, OFFICER -> EnumSet.allOf(Action.class);
            case FRIEND -> friendRules;
            case VISITOR -> visitorRules;
        };
    }

    /** Allows or forbids an action for friends or visitors. */
    public void setAllowed(Rank rank, Action action, boolean allowed) {
        EnumSet<Action> rules = rank == Rank.FRIEND ? friendRules : rank == Rank.VISITOR ? visitorRules : null;
        if (rules == null) {
            return;
        }
        if (allowed) {
            rules.add(action);
        } else {
            rules.remove(action);
        }
        colony.markDirty();
    }

    public boolean allows(Player player, Action action) {
        return allows(player.getUUID(), action);
    }

    public boolean allows(UUID player, Action action) {
        return rules(rank(player)).contains(action);
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        ListTag friendList = new ListTag();
        friends.forEach(f -> friendList.add(StringTag.valueOf(f.toString())));
        tag.put("friends", friendList);
        CompoundTag nameTag = new CompoundTag();
        names.forEach((uuid, name) -> nameTag.putString(uuid.toString(), name));
        tag.put("names", nameTag);
        tag.put("friendRules", rulesTag(friendRules));
        tag.put("visitorRules", rulesTag(visitorRules));
        return tag;
    }

    private static ListTag rulesTag(Set<Action> rules) {
        ListTag list = new ListTag();
        rules.forEach(a -> list.add(StringTag.valueOf(a.id())));
        return list;
    }

    public void load(CompoundTag tag) {
        friends.clear();
        tag.getListOrEmpty("friends").forEach(t -> t.asString().ifPresent(s -> friends.add(UUID.fromString(s))));
        names.clear();
        tag.getCompound("names").ifPresent(n -> {
            for (String key : n.keySet()) {
                n.getString(key).ifPresent(name -> names.put(UUID.fromString(key), name));
            }
        });
        if (tag.contains("friendRules")) {
            loadRules(tag.getListOrEmpty("friendRules"), friendRules);
        }
        loadRules(tag.getListOrEmpty("visitorRules"), visitorRules);
    }

    private static void loadRules(ListTag list, EnumSet<Action> rules) {
        rules.clear();
        list.forEach(t -> t.asString().map(Action::byId).ifPresent(rules::add));
    }
}
