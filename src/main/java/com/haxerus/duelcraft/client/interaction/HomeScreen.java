package com.haxerus.duelcraft.client.interaction;

import com.haxerus.duelcraft.DuelcraftClient;
import com.haxerus.duelcraft.client.collection.ClientCollectionState;
import com.haxerus.duelcraft.duel.PreparationView;
import com.haxerus.duelcraft.interaction.ScreenRoute;
import com.lowdragmc.lowdraglib2.gui.texture.SpriteTexture;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Consumer;

public final class HomeScreen extends ManagementScreen {
    private final boolean connected;
    private PreparationView preparation;
    private final int featuredCode = new int[]{89631139, 46986414, 44508094}[ThreadLocalRandom.current().nextInt(3)];
    private HomeScreen(ClientCollectionState.View view, PreparationView preparation, Consumer<ScreenRoute> navigate, boolean connected, ResourceLocation artwork) {
        super("home_screen", "Duelcraft");
        this.connected = connected;
        this.preparation = preparation;
        button("home-collection", () -> navigate.accept(ScreenRoute.COLLECTION));
        button("home-duels", () -> navigate.accept(ScreenRoute.LOBBY));
        button("home-invitation", () -> navigate.accept(ScreenRoute.LOBBY));
        button("home-resume", () -> navigate.accept(ScreenRoute.COLLECTION));
        button("home-close", this::onClose);
        button("home-help", () -> show("management-help", "home-help"));
        button("help-close", () -> hide("management-help"));
        button("home-retry", this::refresh);
        element("management-help").setDisplay(false);
        element("home-retry").setDisplay(false);
        update(view);
        invitation();
        element("home-art").style(style -> style.background((graphics, mx, my, x, y, w, h, tick) -> {
            var images = DuelcraftClient.getCardImageManager();
            var art = connected && images != null ? images.getCardArt(featuredCode) : artwork;
            graphics.fill((int)x, (int)y, (int)(x+w), (int)(y+h), 0xff211c2a);
            if (art == null) return;
            var texture = SpriteTexture.of(art);
            var size = texture.getImageSize();
            float artWidth = w * .6875f;
            float scale = Math.max(artWidth / size.width, h / size.height);
            int cropWidth = Math.min(size.width, Math.round(artWidth / scale));
            int cropHeight = Math.min(size.height, Math.round(h / scale));
            texture.setSprite((size.width - cropWidth) / 2, (size.height - cropHeight) / 2, cropWidth, cropHeight)
                    .draw(graphics, mx, my, x + w - artWidth, y, artWidth, h, tick);
            // Fade artwork into the Gallery canvas behind the left menu.
            for (int offset = 0; offset < 640; offset += 4) {
                int alpha = Math.max(0, 255 - offset * 255 / 640);
                int left = (int)(x + (400 + offset) * w / 1280f);
                int right = (int)(x + (404 + offset) * w / 1280f);
                graphics.fill(left, (int)y, right, (int)(y+h), alpha << 24 | 0x211c2a);
            }
        }));
    }
    public static HomeScreen create() {
        var screen = new HomeScreen(null, DuelcraftClient.preparation().view(), DuelcraftScreens::request, true, null);
        screen.refresh(); return screen;
    }
    public static HomeScreen create(ClientCollectionState.View view, PreparationView preparation, Consumer<ScreenRoute> navigate) {
        return new HomeScreen(view, preparation, navigate, false, null);
    }
    public static HomeScreen create(ClientCollectionState.View view, PreparationView preparation, Consumer<ScreenRoute> navigate,
                                    ResourceLocation artwork) {
        return new HomeScreen(view, preparation, navigate, false, artwork);
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
