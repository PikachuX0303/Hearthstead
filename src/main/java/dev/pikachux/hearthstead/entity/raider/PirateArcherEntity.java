package dev.pikachux.hearthstead.entity.raider;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;

/**
 * A pirate who stays back and shoots at citizens and players.
 */
public class PirateArcherEntity extends ArcherBarbarianEntity {
    public PirateArcherEntity(EntityType<? extends AbstractBarbarian> type, Level level) {
        super(type, level);
    }
}
