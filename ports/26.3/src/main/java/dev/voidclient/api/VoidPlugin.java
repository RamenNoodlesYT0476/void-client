package dev.voidclient.api;
import java.util.function.Function;
import net.minecraft.client.Minecraft;
public interface VoidPlugin {
    void register(Registry registry);
    interface Registry {
        void hud(String id, String label, Function<Minecraft,String> readout);
        void action(String id, String label, java.util.function.Consumer<Minecraft> run);
    }
}

