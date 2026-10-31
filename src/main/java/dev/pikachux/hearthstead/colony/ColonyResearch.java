package dev.pikachux.hearthstead.colony;

import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * The research a colony has finished and the one it is working on.
 */
public class ColonyResearch {
    private final Set<Research> completed = EnumSet.noneOf(Research.class);
    @Nullable
    private Research active;
    private double progress;

    public boolean has(Research research) {
        return completed.contains(research);
    }

    @Nullable
    public Research active() {
        return active;
    }

    public double progress() {
        return progress;
    }

    /** Returns null if the research can be started now, otherwise a translation key. */
    @Nullable
    public String blocker(Colony colony, Research research) {
        if (completed.contains(research)) {
            return "gui.hearthstead.research.done";
        }
        if (active != null) {
            return "gui.hearthstead.research.busy";
        }
        if (research.parent() != null && !completed.contains(research.parent())) {
            return "gui.hearthstead.research.needs_parent";
        }
        if (universityLevel(colony) < research.tier()) {
            return "gui.hearthstead.research.needs_university";
        }
        return null;
    }

    public static int universityLevel(Colony colony) {
        return colony.buildings().stream()
                .filter(b -> b.type() == BuildingType.UNIVERSITY)
                .mapToInt(BuildingData::level)
                .max()
                .orElse(0);
    }

    /** Starts a research, taking its cost from the university. Returns null or an error key. */
    @Nullable
    public String start(ServerLevel level, Colony colony, BuildingData university, Research research) {
        String blocker = blocker(colony, research);
        if (blocker != null) {
            return blocker;
        }
        List<Container> storage = Storage.containers(level, university);
        for (ItemStack cost : research.cost()) {
            if (Storage.count(storage, s -> s.is(cost.getItem())) < cost.getCount()) {
                return "gui.hearthstead.research.missing_items";
            }
        }
        for (ItemStack cost : research.cost()) {
            Storage.extract(storage, s -> s.is(cost.getItem()), cost.getCount());
        }
        active = research;
        progress = 0;
        colony.markDirty();
        return null;
    }

    /** Adds research points to the active research, finishing it when complete. */
    public void addProgress(MinecraftServer server, Colony colony, double points) {
        if (active == null) {
            return;
        }
        progress += points;
        if (progress >= active.points()) {
            completed.add(active);
            colony.broadcast(server, Component.literal("✦ ").withStyle(ChatFormatting.LIGHT_PURPLE)
                    .append(Component.translatable("message.hearthstead.research_done",
                            Component.translatable(active.nameKey()), colony.name()).withStyle(ChatFormatting.AQUA)));
            active = null;
            progress = 0;
        }
        colony.markDirty();
    }

    /** Finishes a research immediately (operator command). */
    public void complete(Research research, Colony colony) {
        completed.add(research);
        if (active == research) {
            active = null;
            progress = 0;
        }
        colony.markDirty();
    }

    CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        ListTag list = new ListTag();
        completed.forEach(r -> list.add(StringTag.valueOf(r.id())));
        tag.put("completed", list);
        if (active != null) {
            tag.putString("active", active.id());
        }
        tag.putDouble("progress", progress);
        return tag;
    }

    void load(CompoundTag tag) {
        completed.clear();
        tag.getListOrEmpty("completed").forEach(t -> t.asString().map(Research::byId).ifPresent(completed::add));
        active = tag.getString("active").map(Research::byId).orElse(null);
        progress = tag.getDoubleOr("progress", 0);
    }
}
