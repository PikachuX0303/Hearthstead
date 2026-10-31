package dev.pikachux.hearthstead.client;

import dev.pikachux.hearthstead.client.gui.UpdatePromptScreen;
import dev.pikachux.hearthstead.config.HearthsteadConfig;
import dev.pikachux.hearthstead.update.Updater;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;

/**
 * Automatic updates on the client: asks on the first title screen, checks GitHub at start when
 * allowed, and installs a downloaded release when the game closes.
 */
public final class UpdateClient {
    private static boolean asked;

    private UpdateClient() {
    }

    public static void register() {
        if (Updater.installedJar().isEmpty()) {
            return;
        }
        if (HearthsteadConfig.AUTOMATIC_UPDATES.is("on")) {
            check();
        }
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (!asked && client.screen instanceof TitleScreen title) {
                asked = true;
                if (HearthsteadConfig.AUTOMATIC_UPDATES.is("ask")) {
                    client.setScreen(new UpdatePromptScreen(title, enabled -> {
                        HearthsteadConfig.AUTOMATIC_UPDATES.set(enabled ? "on" : "off");
                        HearthsteadConfig.save();
                        if (enabled) {
                            check();
                        }
                    }));
                }
            }
        });
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> Updater.installPending());
    }

    private static void check() {
        Updater.checkAndDownload().thenAccept(version -> version.ifPresent(v -> {
            Minecraft client = Minecraft.getInstance();
            client.execute(() -> SystemToast.addOrUpdate(client.getToastManager(), SystemToast.SystemToastId.PERIODIC_NOTIFICATION,
                    Component.translatable("toast.hearthstead.update_ready", v),
                    Component.translatable("toast.hearthstead.update_ready.detail")));
        }));
    }
}
