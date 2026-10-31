package dev.pikachux.hearthstead.item;

import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;

/**
 * The build tool. All of its behaviour happens on the client, which shows a preview
 * of the chosen building; the server only receives the final placement request.
 */
public class BuildToolItem extends Item {
    public BuildToolItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        return InteractionResult.SUCCESS;
    }
}
