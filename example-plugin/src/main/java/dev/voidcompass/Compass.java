package dev.voidcompass;
import dev.voidclient.api.VoidPlugin;
public class Compass implements VoidPlugin {
    public void register(Registry registry) {
        registry.hud("voidcompass:bearing","Compass bearing",c -> "Bearing  " + Math.floorMod(Math.round(c.player.getYaw()) + 180,360) + "°");
    }
}

