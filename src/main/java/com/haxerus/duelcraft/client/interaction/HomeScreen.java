package com.haxerus.duelcraft.client.interaction;

import com.haxerus.duelcraft.DuelcraftClient;
import com.haxerus.duelcraft.client.collection.ClientCollectionState;
import com.haxerus.duelcraft.duel.PreparationView;
import com.haxerus.duelcraft.interaction.ScreenRoute;
import com.lowdragmc.lowdraglib2.gui.texture.SpriteTexture;
import net.minecraft.client.Minecraft;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Consumer;

public final class HomeScreen extends ManagementScreen {
    private final boolean connected;
    private PreparationView preparation;
    private final int featuredCode = new int[]{89631139, 46986414, 44508094}[ThreadLocalRandom.current().nextInt(3)];
    private HomeScreen(ClientCollectionState.View view, PreparationView preparation, Consumer<ScreenRoute> navigate, boolean connected) {
        super("home_screen", "Duelcraft");
        this.connected = connected;
        this.preparation = preparation;
        button("home-collection", () -> navigate.accept(ScreenRoute.COLLECTION));
        button("home-duels", () -> navigate.accept(ScreenRoute.LOBBY));
        button("home-invitation", () -> navigate.accept(ScreenRoute.LOBBY));
        button("home-resume", () -> navigate.accept(ScreenRoute.COLLECTION));
        button("home-close", this::onClose);
        button("home-help", () -> element("management-help").setDisplay(true));
        button("help-close", () -> hide("management-help"));
        button("home-retry", this::refresh);
        element("management-help").setDisplay(false);
        element("home-retry").setDisplay(false);
        update(view);
        invitation();
        element("home-art").style(style -> style.background((graphics, mx, my, x, y, w, h, tick) -> {
            var images = DuelcraftClient.getCardImageManager();
            var art = connected && images != null ? images.getCardArt(featuredCode) : null;
            graphics.fill((int)x, (int)y, (int)(x+w), (int)(y+h), 0xff18171e);
            if (art == null) return;
            SpriteTexture.of(art).draw(graphics, mx, my, x + w * .40625f, y, w * .59375f, h, tick);
            // Opaque left edge blends into transparent artwork without tinting menu text.
            for (int offset = 0; offset < 400; offset += 4) {
                int alpha = Math.max(0, 255 - offset * 255 / 400);
                int left = (int)(x + w * .40625f + offset * w / 1280f);
                int right = (int)(x + w * .40625f + (offset + 4) * w / 1280f);
                graphics.fill(left, (int)y, right, (int)(y+h), alpha << 24 | 0x18171e);
            }
        }));
    }
    public static HomeScreen create() {
        var screen = new HomeScreen(null, DuelcraftClient.preparation().view(), DuelcraftScreens::request, true);
        screen.refresh(); return screen;
    }
    public static HomeScreen create(ClientCollectionState.View view, PreparationView preparation, Consumer<ScreenRoute> navigate) {
        return new HomeScreen(view, preparation, navigate, false);
    }
    private void refresh() {
        text("home-active-deck", "Loading…");
        element("home-retry").setDisplay(false);
        DuelcraftClient.getCollectionClient().refresh().whenCompleteAsync((view, error) -> {
            if (removed) return;
            if (error == null) update(view);
            else { text("home-active-deck", "Could not load your deck. Try again."); element("home-retry").setDisplay(true); }
        }, Minecraft.getInstance()::execute);
    }
    private void update(ClientCollectionState.View view) {
        text("home-active-deck", deckSummary(view));
        element("home-resume").setDisplay(connected && DuelcraftScreens.hasDraft());
    }
    static String deckSummary(ClientCollectionState.View view) {
        if (view == null) return "Loading…";
        var active = view.summaries().stream().filter(s -> s.id().equals(view.activeId())).findFirst().orElse(null);
        if (active == null) {
            var report = view.clearedActivation();
            if (report != null && report.restrictionReason() != null) return "No active deck\n" + report.restrictionReason();
            if (report != null && report.ownershipRequired() && !report.missing().isEmpty()) return "No active deck\nDeposit the missing copies, then activate your saved deck.";
            return "No active deck";
        }
        return active.name() + "\nMain " + active.main() + " / Extra " + active.extra() + " / Side " + active.side();
    }
    private void invitation() {
        boolean incoming = preparation != null && preparation.mode() == PreparationView.Mode.INVITED && !preparation.outgoing();
        element("home-invitation-row").setDisplay(incoming);
        if (incoming) text("home-invitation-text", preparation.opponentName() + " invited you to duel.");
    }
    @Override public void tick() {
        super.tick();
        if (connected && preparation != DuelcraftClient.preparation().view()) {
            preparation = DuelcraftClient.preparation().view(); invitation();
        }
    }
}
