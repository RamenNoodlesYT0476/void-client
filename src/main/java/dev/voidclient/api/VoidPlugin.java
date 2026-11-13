package dev.voidclient.api;
import java.util.function.Function;
import net.minecraft.client.MinecraftClient;
public interface VoidPlugin {
    void register(Registry registry);
    interface Registry {
        void hud(String id, String label, Function<MinecraftClient,String> readout);
        void action(String id, String label, java.util.function.Consumer<MinecraftClient> run);
    }
}

