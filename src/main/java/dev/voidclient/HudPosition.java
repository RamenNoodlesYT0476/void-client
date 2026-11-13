package dev.voidclient;

import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

final class HudPosition extends Screen {
    private final Screen parent;

    HudPosition(Screen parent) {
        super(Text.literal("Place Void HUD"));
        this.parent = parent;
    }

    @Override public void render(DrawContext context, int x, int y, float delta) {
        String hint = "Click to place the HUD • Esc to cancel";
        context.drawCenteredTextWithShadow(textRenderer, hint, width / 2, 8, 0xFFFFFFFF);
        int left = Math.max(0, Math.min(x, width - 100));
        int top = Math.max(0, Math.min(y, height - 29));
        context.fill(left, top, left + 100, top + 29, 0x22000000);
        context.drawText(textRenderer, "XYZ  8 / 13 / 8", left + 6, top + 4, 0xFFCCD0D6, false);
        context.drawText(textRenderer, "FPS  120", left + 6, top + 16, 0xFFFFFFFF, false);
    }

    @Override public boolean mouseClicked(Click click, boolean twice) {
        if (click.button() != 0) return false;
        VoidClient.set("hud.x", normalized(click.x(), width - 100));
        VoidClient.set("hud.y", normalized(click.y(), height - 29));
        VoidClient.set("hud.custom", "true");
        close();
        return true;
    }

    private static String normalized(double point, int available) {
        return Long.toString(Math.round(Math.clamp(point / Math.max(1, available), 0, 1) * 1000));
    }

    @Override public void close() { client.setScreen(parent); }
}

