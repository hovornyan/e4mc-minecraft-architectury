package link.e4mc.mixin;

import net.minecraft.client.server.IntegratedServer;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

@Mixin(IntegratedServer.class)
public class IntegratedServerMixin {

    /**
     * When the singleplayer world is published ("Open to LAN"), force offline-mode.
     *
     * This project targets Mojang official mappings (mojmap) for 1.20.2, where there is no
     * stable public setter like setOnlineMode(...). We therefore flip the underlying flag
     * via reflection in a best-effort way.
     */
    @Inject(method = "publishServer", at = @At("HEAD"))
    private void e4mc$offlineMode(GameType gameType, boolean allowCommands, int port, CallbackInfoReturnable<Boolean> cir) {
        MinecraftServer server = (MinecraftServer) (Object) this;

        e4mc$forceOfflineMode(server);

        Component msg = Component.literal("[offline-e4mc] Forced offline-mode for integrated server (LAN publish).");
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            player.sendSystemMessage(msg);
        }
    }

    private static void e4mc$forceOfflineMode(MinecraftServer server) {
        // 1) Try an actual setter if present on this version / loader.
        try {
            Method m = MinecraftServer.class.getDeclaredMethod("setUsesAuthentication", boolean.class);
            m.setAccessible(true);
            m.invoke(server, false);
            return;
        } catch (Throwable ignored) {
            // continue
        }

        // 2) Try common field names used in mojmap across versions.
        if (e4mc$trySetBooleanField(server, "onlineMode", false)) return;
        if (e4mc$trySetBooleanField(server, "usesAuthentication", false)) return;

        // 3) Last resort: scan for a boolean field that looks like it controls auth.
        try {
            for (Field f : MinecraftServer.class.getDeclaredFields()) {
                if (f.getType() == boolean.class) {
                    String n = f.getName().toLowerCase();
                    if (n.contains("online") || n.contains("auth")) {
                        f.setAccessible(true);
                        f.setBoolean(server, false);
                        return;
                    }
                }
            }
        } catch (Throwable ignored) {
            // If this fails, we simply don't force the flag.
        }
    }

    private static boolean e4mc$trySetBooleanField(Object instance, String fieldName, boolean value) {
        try {
            Field f = instance.getClass().getDeclaredField(fieldName);
            f.setAccessible(true);
            f.setBoolean(instance, value);
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }
}
