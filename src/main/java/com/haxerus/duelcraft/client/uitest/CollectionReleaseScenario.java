package com.haxerus.duelcraft.client.uitest;

import com.haxerus.duelcraft.DuelcraftClient;
import com.haxerus.duelcraft.client.carddata.CardImageManager;
import com.haxerus.duelcraft.client.carddata.CardInfo;
import com.haxerus.duelcraft.client.collection.CardSearch;
import com.haxerus.duelcraft.client.collection.CollectionCardGrid;
import com.haxerus.duelcraft.client.collection.CollectionCatalog;
import com.haxerus.duelcraft.client.collection.CollectionQuery;
import com.haxerus.duelcraft.client.collection.CollectionScreen;
import com.haxerus.duelcraft.client.collection.CollectionSearchWorker;
import com.haxerus.duelcraft.client.collection.DeckEditorModel;
import com.haxerus.duelcraft.collection.DeckList;
import com.haxerus.duelcraft.core.data.CardData;
import com.lowdragmc.lowdraglib2.gui.ui.elements.TextField;
import com.lowdragmc.lowdraglib2.registry.RegistrationEnvironment;
import com.lowdragmc.lowdraglib2.registry.annotation.LDLRegisterClient;
import com.lowdragmc.lowdraglib2.uitest.ScenarioBuilder;
import com.lowdragmc.lowdraglib2.uitest.ScenarioOptions;
import com.lowdragmc.lowdraglib2.uitest.TestContext;
import com.lowdragmc.lowdraglib2.uitest.UIScenario;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.function.Consumer;

/** Measurements and controlled asset failures; overflow/search scenarios own their existing assertions. */
@LDLRegisterClient(name = "collection_release", group = "duelcraft", registry = UIScenario.REGISTRY,
        environment = RegistrationEnvironment.DEV_ONLY)
public final class CollectionReleaseScenario implements UIScenario {
    private static final DeckList EMPTY = new DeckList(List.of(), List.of(), List.of());
    private static final int IMAGE_CODE = 2_000_000_001;

    @Override
    public void configure(ScenarioOptions options) {
        options.tags("collection", "release-catalog").requiresWorld(false).defaultTimeoutMs(30_000);
    }

    @Override
    public void define(ScenarioBuilder s) {
        s.step("prepare synthetic catalog", ctx -> ctx.put("cards", CollectionFixture.largeCatalog()));
        profile(s, "synthetic-16000", false);
        s.step("load real managed catalog off render thread", ctx -> ctx.put("managed", CardData.load().thenApplyAsync(snapshot -> {
            long start = System.nanoTime();
            try {
                var cards = CollectionCatalog.load(snapshot.database());
                return new Managed(snapshot.database(), cards, millis(start));
            } catch (SQLException exception) {
                throw new CompletionException(exception);
            }
        })))
         .waitUntil("managed metadata loaded", ctx -> ctx.<CompletableFuture<Managed>>get("managed").isDone())
         .step("record managed database", ctx -> {
             var managed = ctx.<CompletableFuture<Managed>>get("managed").join();
             ctx.put("cards", managed.cards());
             ctx.check("managed catalog is populated", !managed.cards().isEmpty());
             ctx.attach("managed catalog", "path=%s raw_cards=%d read_ms=%.3f (independent read; OS cache uncontrolled)"
                     .formatted(managed.database(), managed.cards().size(), managed.readMs()));
         })
         .waitUntil("real image manager initialized", ctx -> DuelcraftClient.getCardImageManager() != null);
        profile(s, "managed", true);
        unavailableDatabase(s);
        unavailableImage(s);
        s.teardown("close fixture and disposable image manager", ctx -> {
            ctx.mc().setScreen(null);
            CardImageManager images = ctx.get("fixtureImages");
            if (images != null) images.close();
        });
    }

    private static void profile(ScenarioBuilder s, String label, boolean realImages) {
        s.openScreen(label + " catalog measurements", ctx -> {
            var query = ctx.put("query", new TimedQuery(ctx));
            var cards = ctx.<List<CardInfo>>get("cards");
            ctx.put("samples", new ArrayList<String>());
            ctx.put("lastFrame", 0L);
            return CollectionScreen.create(new DeckEditorModel(EMPTY, Map.of()), cards,
                    code -> realImages ? DuelcraftClient.getCardImageManager().getCardTexture(code) : null,
                    code -> null, CollectionFixture.LIST_ID, CollectionFixture::saved, query);
        }).awaitModularUI()
         .waitUntil(label + " initial asynchronous results", ctx -> query(ctx).published != null)
         .ticks(3)
         .step(label + " first worker query and mounted rows", ctx -> {
             var found = results(ctx);
             ctx.check("catalog has searchable cards", !found.isEmpty());
             ctx.put("probe", Integer.toString(found.getFirst().code()));
             if (!realImages) ctx.check("synthetic input contains 16000 cards", ctx.<List<CardInfo>>get("cards").size() == 16000);
             CollectionLayoutScenario.recordViewport(ctx);
             observe(ctx, label + " top", realImages);
         })
         .step(label + " hover catalog", ctx -> {
             var bounds = ctx.el("#collection-results").bounds();
             ctx.input().moveTo(bounds.center().x, bounds.center().y);
         });
        for (int i = 0; i < 12; i++) {
            s.settleMs(0).step(label + " wheel sample " + i, ctx -> {
                long start = System.nanoTime();
                long previous = ctx.get("lastFrame");
                var bounds = ctx.el("#collection-results").bounds();
                ctx.input().scroll(bounds.center().x, bounds.center().y, -1);
                ctx.<ArrayList<String>>get("samples").add("interval_ms=%.3f input_ms=%.3f mounted_rows=%d pending_cards=%d"
                        .formatted(previous == 0 ? 0 : (start - previous) / 1_000_000d, millis(start),
                                grid(ctx).getMountedItemCount(), realImages ? pending(ctx, DuelcraftClient.getCardImageManager(), "cardLoading") : 0));
                ctx.put("lastFrame", start);
            });
        }
        s.step(label + " record scroll samples", ctx -> {
            ctx.attach("scroll samples", String.join("\n", ctx.<ArrayList<String>>get("samples")));
            ctx.attach("timing boundary", "Intervals are harness step/frame cadence with zero settle, including harness and rendering overhead; input_ms times wheel dispatch only. No FPS or render CPU guarantee.");
            observe(ctx, label + " after wheel", realImages);
         })
         .step(label + " scroll to end", ctx -> grid(ctx).verticalScroller.setNormalizedValue(1, true))
         .ticks(3)
         .step(label + " record last mounted rows", ctx -> observe(ctx, label + " end", realImages))
         .screenshot("collection-release-" + label)
         .step(label + " cold passcode query", ctx -> text(ctx, ctx.get("probe")))
         .waitUntil(label + " cold query delivered", ctx -> ctx.<String>get("probe").equals(query(ctx).published))
         .step(label + " reset query", ctx -> text(ctx, ""))
         .waitUntil(label + " reset delivered", ctx -> "".equals(query(ctx).published))
         .step(label + " warm passcode query", ctx -> text(ctx, ctx.get("probe")))
         .waitUntil(label + " warm query delivered", ctx -> ctx.<String>get("probe").equals(query(ctx).published))
         .step(label + " supersede an intermediate query", ctx -> {
             query(ctx).published = null;
             text(ctx, "duelcraft-no-such-card-" + UUID.randomUUID());
             text(ctx, ctx.get("probe"));
         }).ticks(5)
         .waitUntil(label + " latest query delivered", ctx -> ctx.<String>get("probe").equals(query(ctx).published))
         .step(label + " record delivered search timings", ctx -> {
             ctx.check("latest query displays exact passcode first", !results(ctx).isEmpty()
                     && results(ctx).getFirst().code() == Integer.parseInt(ctx.get("probe")));
             ctx.attach("search timings", String.join("\n", query(ctx).measurements));
             ctx.attach("search timing boundary", "Request-to-client-publication includes 150ms debounce, executor scheduling and frame delivery. Cold is first use of this query on this screen; warm is its repeat. JVM/OS caches are uncontrolled. Deterministic queued stale-delivery checks remain CollectionSearchWorkerTest's responsibility.");
         });
    }

    private static void unavailableDatabase(ScenarioBuilder s) {
        s.step("load a deliberately absent disposable database", ctx -> ctx.put("missingDatabase", CompletableFuture.supplyAsync(() -> {
            Path absent = Path.of(System.getProperty("java.io.tmpdir"), "duelcraft-absent-" + UUID.randomUUID(), "cards.cdb");
            try {
                CollectionCatalog.load(absent);
                throw new IllegalStateException("Absent database unexpectedly loaded");
            } catch (SQLException expected) {
                return expected.getMessage();
            }
        })))
         .waitUntil("missing database rejected", ctx -> ctx.<CompletableFuture<String>>get("missingDatabase").isDone())
         .openScreen("missing catalog with saved passcodes", ctx -> CollectionScreen.create(
                 new DeckEditorModel(CollectionFixture.model(false).draft(), Map.of()),
                 List.of(), code -> null, CollectionFixture.LIST_ID, CollectionFixture::saved, CollectionFixture.query()))
         .awaitModularUI()
         .step("publish catalog failure fixture", ctx -> {
             ctx.attach("database failure", ctx.<CompletableFuture<String>>get("missingDatabase").join());
             setCatalog(ctx, List.of(), true);
         }).ticks(2)
         .checkVisible("#collection-empty")
         .checkTextContains("#collection-empty", "unavailable")
         .click("#main-card-0").ticks(2)
         .checkTextContains("#inspector-name", "10001")
         .screenshot("collection-release-missing-database")
         .step("publish restored metadata through fixture seam", ctx -> setCatalog(ctx, CollectionFixture.cards(), false))
         .ticks(2)
         .checkHidden("#collection-empty")
         .checkText("#inspector-name", "Amber Dragon")
         .step("document database recovery boundary", ctx -> ctx.attach("recovery boundary",
                 "Recovery invokes controller.setCatalog in this DEV fixture. Production collectionCatalog is a one-shot future: reopening the screen does not retry a failed load; restart after correcting the underlying data is required."));
    }

    private static void unavailableImage(ScenarioBuilder s) {
        s.openScreen("unavailable disposable image", ctx -> {
            try {
                Path cache = ctx.put("imageCache", Files.createTempDirectory("duelcraft-release-images-"));
                ctx.put("fixtureImages", new CardImageManager("http://127.0.0.1:1/cards_small/", cache));
                var card = new CardInfo(IMAGE_CODE, "Unavailable artwork fixture", "Metadata remains readable.", 1, 0, 0, 4, 1, 1);
                return CollectionScreen.create(new DeckEditorModel(EMPTY, Map.of()), List.of(card),
                        code -> ctx.<CardImageManager>get("fixtureImages").getCardTexture(code), code -> null,
                        CollectionFixture.LIST_ID, CollectionFixture::saved, CollectionFixture.query());
            } catch (Exception exception) {
                throw new IllegalStateException(exception);
            }
         }).awaitModularUI()
         .waitUntil("controlled local image failure completed", ctx -> ctx.<Set<Integer>>getField(ctx.get("fixtureImages"), "cardFailed").contains(IMAGE_CODE)
                 && pending(ctx, ctx.get("fixtureImages"), "cardLoading") == 0)
         .checkVisible("#collection-card-" + IMAGE_CODE + " .card-placeholder")
         .screenshot("collection-release-missing-image")
         .step("restore disposable disk image and observe same-manager retry", ctx -> {
             try {
                 Path cache = ctx.get("imageCache");
                 var image = new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB);
                 image.setRGB(0, 0, 0x5abf79);
                 image.setRGB(1, 1, 0x5abf79);
                 ImageIO.write(image, "png", cache.resolve("cards/" + IMAGE_CODE + ".png").toFile());
                 var images = ctx.<CardImageManager>get("fixtureImages");
                 ctx.check("failed code remains a placeholder in this manager session", images.getCardTexture(IMAGE_CODE) == null
                         && pending(ctx, images, "cardLoading") == 0);
                 images.close();
                 ctx.put("fixtureImages", new CardImageManager("http://127.0.0.1:1/cards_small/", cache));
                 ctx.attach("image retry boundary", "The same manager does not retry a failed code even after a disk file appears. Recreating the manager uses the restored disposable PNG. Production keeps its manager until process restart; screen reopen does not clear failures. Cache=" + cache);
             } catch (Exception exception) {
                 throw new IllegalStateException(exception);
             }
         })
         .waitUntil("new manager loads restored disposable image", ctx -> ctx.count("#collection-card-" + IMAGE_CODE + ".texture-ready") == 1)
         .checkHidden("#collection-card-" + IMAGE_CODE + " .card-placeholder")
         .screenshot("collection-release-image-recreated-manager");
    }

    private static void observe(TestContext ctx, String label, boolean realImages) {
        var grid = grid(ctx);
        int rowLimit = (int) Math.ceil(grid.viewPort.getContentHeight()
                / grid.getVirtualScrollerViewStyle().estimatedItemHeight()) + 3;
        int mountedTiles = ctx.count("#collection-results .collection-card");
        ctx.check(label + " mounts rows within viewport and overscan", grid.getMountedItemCount() > 0
                && grid.getMountedItemCount() <= rowLimit);
        ctx.check(label + " mounts at most four card tiles per bounded row", mountedTiles > 0
                && mountedTiles <= grid.getMountedItemCount() * 4 && mountedTiles <= rowLimit * 4);
        ctx.attach(label, "raw_cards=%d results=%d rows=%d mounted_rows=%d mounted_tiles=%d row_limit=%d viewport=%.1f offset=%.5f pending_cards=%d pending_art=%d"
                .formatted(ctx.<List<CardInfo>>get("cards").size(), results(ctx).size(), grid.getItemCount(),
                        grid.getMountedItemCount(), mountedTiles, rowLimit, grid.viewPort.getContentHeight(),
                        grid.verticalScroller.getNormalizedValue(), realImages ? pending(ctx, DuelcraftClient.getCardImageManager(), "cardLoading") : 0,
                        realImages ? pending(ctx, DuelcraftClient.getCardImageManager(), "artLoading") : 0));
    }

    private static int pending(TestContext ctx, CardImageManager images, String field) { return ctx.<Set<Integer>>getField(images, field).size(); }
    private static CollectionCardGrid grid(TestContext ctx) { return ctx.el("#collection-results").as(CollectionCardGrid.class); }
    private static TimedQuery query(TestContext ctx) { return ctx.get("query"); }
    private static void text(TestContext ctx, String text) { ctx.el("#collection-search").as(TextField.class).setText(text, true); }
    private static List<CardInfo> results(TestContext ctx) { return ctx.getField(ctx.getField(ctx.screen(), "controller"), "results"); }
    private static double millis(long start) { return (System.nanoTime() - start) / 1_000_000d; }

    private static void setCatalog(TestContext ctx, List<CardInfo> cards, boolean failed) {
        Object controller = ctx.getField(ctx.screen(), "controller");
        try {
            var method = controller.getClass().getDeclaredMethod("setCatalog", List.class, boolean.class);
            method.setAccessible(true);
            method.invoke(controller, cards, failed);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private record Managed(Path database, List<CardInfo> cards, double readMs) {}

    private static final class TimedQuery implements CollectionQuery {
        private final CollectionSearchWorker worker;
        private final List<String> measurements = new ArrayList<>();
        private String published;

        private TimedQuery(TestContext ctx) { worker = new CollectionSearchWorker(ctx.mc()::execute); }

        @Override
        public void submit(List<CardInfo> cards, String text, CardSearch.Filters filters, Map<Integer, Long> counts,
                           DeckList draft, Consumer<List<CardInfo>> apply) {
            long start = System.nanoTime();
            worker.submit(cards, text, filters, counts, draft, found -> {
                measurements.add("query='%s' results=%d request_to_publication_ms=%.3f".formatted(text, found.size(), millis(start)));
                apply.accept(found);
                published = text;
            });
        }

        @Override public void close() { worker.close(); }
    }
}
