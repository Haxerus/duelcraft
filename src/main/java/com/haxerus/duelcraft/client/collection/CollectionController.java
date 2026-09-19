package com.haxerus.duelcraft.client.collection;

import com.haxerus.duelcraft.client.carddata.CardInfo;
import com.haxerus.duelcraft.client.carddata.CardStringHelper;
import com.haxerus.duelcraft.collection.CollectionCommand;
import com.haxerus.duelcraft.collection.CollectionReply;
import com.haxerus.duelcraft.collection.DeckEligibility;
import com.lowdragmc.lowdraglib2.gui.ui.UI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;
import com.lowdragmc.lowdraglib2.gui.ui.elements.ScrollerView;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Selector;
import com.lowdragmc.lowdraglib2.gui.ui.elements.TextElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.TextField;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents;
import com.lowdragmc.lowdraglib2.gui.ui.utils.UIElementProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.function.IntFunction;

import static com.haxerus.duelcraft.core.OcgConstants.*;

/** Local editor state is retained independently of mounted/recycled card widgets. */
final class CollectionController {
    private final UI ui;
    private final DeckEditorModel model;
    private List<CardInfo> cards;
    private final Map<Integer, CardInfo> byCode = new HashMap<>();
    private final IntFunction<ResourceLocation> textures;
    private final IntFunction<ResourceLocation> art;
    private final SavedDeckController lists;
    private DeckEligibility.Report displayedEligibility;
    private final CollectionQuery search;
    private long queryGeneration;
    private boolean disposed;
    private String catalogState = "";
    private final Runnable close;
    private final CollectionCardGrid collection;
    private int selectedCode;
    private enum EditMode { DECK, SIDE }
    private EditMode editMode = EditMode.DECK;
    private DeckEditorModel.Section selectedStoredSection;
    private boolean largeCards;
    private boolean sideOpen;
    private boolean filtersOpen;
    private boolean alternateFormats;
    private String query = "";
    private CardSearch.Filters filters = CardSearch.Filters.ALL;
    private CardSearch.Ownership ownership = CardSearch.Ownership.ALL;
    private CardSearch.Sort sort = CardSearch.Sort.NAME;
    private CardSearch.Measure measure = CardSearch.Measure.ANY;
    private final Map<String, Long> masks = new HashMap<>();
    private final Map<String, String> ranges = new HashMap<>();
    private record FilterChoice(String group, long bit, Button button) {}
    private final Map<String, FilterChoice> choices = new LinkedHashMap<>();
    private final Map<ScrollerView, Float> pendingScrollOffsets = new HashMap<>();
    private List<CardInfo> results = List.of();

    CollectionController(UI ui, DeckEditorModel model, List<CardInfo> cards,
            IntFunction<ResourceLocation> textures, IntFunction<ResourceLocation> art, UUID id,
            DeckSaveHandler saveDraft, CollectionQuery search,
            Function<CollectionCommand, CompletionStage<CollectionReply>> request,
            Supplier<CompletionStage<ClientCollectionState.View>> refresh, Executor client, Runnable close) {
        this.ui = ui;
        this.model = model;
        this.cards = List.copyOf(cards);
        this.textures = textures;
        this.art = art;
        this.search = search;
        lists = new SavedDeckController(model, id, "New list", saveDraft, request, refresh, client, this::lifecycleChanged);
        this.close = close;
        ((TextElement) element("editor-status")).setText(Component.translatable("duelcraft.collection.unavailable"));
        ((TextElement) element("inspector-empty-prompt")).setText(Component.translatable("duelcraft.collection.select_card"));
        for (var entry : Map.of("lists-title", "lists", "list-name-label", "list_name",
                "list-supported-checks", "supported_checks", "list-save-first", "save_first",
                "delete-title", "delete_confirm", "delete-copy", "delete_copy",
                "close-title", "save_changes", "close-copy", "save_changes_copy").entrySet()) {
            ((TextElement) element(entry.getKey())).setText(Component.translatable("duelcraft.collection." + entry.getValue()));
        }
        cards.forEach(card -> byCode.put(card.code(), card));
        collection = new CollectionCardGrid(textures, code -> {
            selectedStoredSection = null;
            selectCard(code);
        });
        collection.setId("collection-results");
        element("collection-results-host").addChild(collection);
        button("save-deck").setOnClick(event -> lists.save());
        button("add-card").setOnClick(event -> edit(true));
        button("remove-card").setOnClick(event -> edit(false));
        for (var mode : EditMode.values()) {
            button("section-" + mode.name().toLowerCase(Locale.ROOT)).setOnClick(event -> {
                editMode = mode;
                selectedStoredSection = null;
                if (mode == EditMode.SIDE) sideOpen = true;
                refreshPanes();
                refreshInspector(false);
            });
        }
        button("toggle-side").setOnClick(event -> {
            sideOpen = !sideOpen;
            refreshPanes();
        });
        button("card-density").setOnClick(event -> {
            largeCards = !largeCards;
            refreshPanes();
        });
        button("toggle-filters").setOnClick(event -> showFilters(!filtersOpen));
        button("alternate-formats").setOnClick(event -> {
            alternateFormats = !alternateFormats;
            applyOrdering();
        });
        button("alternate-formats").getStyle().tooltips(Component.literal(
                "Include Anime, pre-errata, video game, custom, Speed and Rush cards."));
        button("filter-apply").setOnClick(event -> {
            if (applyFilters()) showFilters(false);
        });
        button("filter-clear").setOnClick(event -> clearFilters());
        ((TextField) element("collection-search")).setTextResponder(text -> {
            query = text;
            refreshResults(true);
            refreshChips();
        });
        configureSelector("collection-ownership", List.of(CardSearch.Ownership.values()), ownership,
                value -> { ownership = value; applyOrdering(); });
        configureSelector("card-sort", List.of(CardSearch.Sort.values()), sort,
                value -> { sort = value; applyOrdering(); });
        configureSelector("filter-measure", List.of(CardSearch.Measure.values()), measure,
                value -> measure = value);
        element("collection-ownership").getStyle().tooltips(Component.literal("Missing: this draft needs more than you own."),
                Component.literal("Extras: owned copies beyond this draft's requirement."));
        createFilters();
        button("close-save").setOnClick(event -> lists.save());
        button("close-discard").setOnClick(event -> lists.discardNavigation());
        button("close-cancel").setOnClick(event -> lists.cancelNavigation());
        for (String unavailable : List.of("deposit-cards", "withdraw-card")) {
            element(unavailable).setActive(false).getStyle()
                    .tooltips(Component.translatable("duelcraft.collection.unavailable"));
        }
        configureLists();
        installSelectorAlignment();
        button("eligibility-details").setText(Component.translatable("duelcraft.collection.details"));
        button("eligibility-details").setOnClick(event -> element("eligibility-dialog").setDisplay(true));
        button("eligibility-close").setText(Component.translatable("duelcraft.collection.done"));
        button("eligibility-close").setOnClick(event -> element("eligibility-dialog").setDisplay(false));
        ((TextElement) element("eligibility-title")).setText(Component.translatable("duelcraft.collection.eligibility_title"));
        element("eligibility-dialog").setDisplay(false);
        element("close-dialog").setDisplay(false);
        showFilters(false);
        refreshDecks();
        refreshPanes();
        refreshInspector(true);
        refreshResults(true);
        refreshChips();
        lifecycleChanged();
    }

    @SuppressWarnings("unchecked")
    private void configureLists() {
        button("saved-lists").setOnClick(event -> element("lists-dialog").setDisplay(true));
        button("lists-close").setOnClick(event -> element("lists-dialog").setDisplay(false));
        ((TextField) element("list-name")).setTextResponder(lists::rename);
        ((Selector<UUID>) element("list-picker")).dialog.style(style -> style.zIndex(95));
        ((Selector<UUID>) element("list-picker")).setOnValueChanged(target -> {
            if (target != null && !target.equals(lists.id())) lists.navigate(() -> lists.select(target));
        });
        button("list-new").setOnClick(event -> lists.navigate(lists::newDraft));
        button("list-rename").setOnClick(event -> lists.save());
        button("list-duplicate").setOnClick(event -> { lists.duplicate(); lists.save(); });
        button("list-delete").setOnClick(event -> element("delete-dialog").setDisplay(true));
        button("delete-confirm").setOnClick(event -> {
            element("delete-dialog").setDisplay(false);
            lists.delete();
        });
        button("delete-cancel").setOnClick(event -> element("delete-dialog").setDisplay(false));
        button("activate-deck").setText(Component.translatable("duelcraft.collection.activate"));
        button("activate-deck").setOnClick(event -> lists.activate());
        button("list-activate").setOnClick(event -> lists.activate());
        button("list-clear-active").setOnClick(event -> lists.clearActive());
        button("list-refresh").setOnClick(event -> lists.refresh());
        element("lists-dialog").setDisplay(false);
        element("delete-dialog").setDisplay(false);
    }

    @SuppressWarnings("unchecked")
    private void lifecycleChanged() {
        if (disposed) return;
        var selector = (Selector<UUID>) element("list-picker");
        var ids = lists.summaries().stream().map(CollectionReply.Summary::id).toList();
        UIElementProvider<UUID> options = UIElementProvider.text(id -> {
            var summary = lists.summaries().stream().filter(value -> value.id().equals(id)).findFirst().orElse(null);
            return summary == null ? Component.translatable("duelcraft.collection.unsaved_list", lists.name())
                    : Component.literal(summary.name()).append(id.equals(lists.activeId())
                            ? Component.translatable("duelcraft.collection.active_suffix") : Component.empty());
        });
        selector.setCandidates(ids).setCandidateUIProvider(id -> options.apply(id).addClass("selector-option"));
        selector.setValue(lists.id(), false);
        ((TextField) element("list-name")).setText(lists.name(), false);
        element("list-name").setActive(!lists.pending());
        element("list-picker").setActive(!lists.pending() && lists.ready());
        for (String id : List.of("save-deck", "list-new", "list-rename", "list-duplicate", "close-save")) {
            element(id).setActive(!lists.pending() && lists.ready());
        }
        for (String id : List.of("close-discard", "close-cancel")) element(id).setActive(!lists.pending());
        element("list-delete").setActive(!lists.pending() && lists.ready() && lists.stored());
        element("list-clear-active").setActive(!lists.pending() && lists.activeId() != null);
        element("list-refresh").setActive(!lists.pending());
        element("close-dialog").setDisplay(lists.needsDecision());
        ((TextElement) element("editor-title")).setText(Component.literal(lists.name()));
        ((TextElement) element("list-active-status")).setText(Component.translatable("duelcraft.collection." +
                (lists.id().equals(lists.activeId()) ? "active" : "not_active")));
        if (!lists.status().isEmpty()) ((TextElement) element("editor-status")).setText(
                Component.translatable("duelcraft.collection." + lists.status(), failureDetail()));
        refreshEligibility();
        refreshDecks();
        refreshInspector(true);
        refreshResults(false);
    }

    private void refreshEligibility() {
        var report = lists.eligibility();
        element("eligibility-details").setDisplay(!report.eligible());
        if (report == displayedEligibility) return;
        displayedEligibility = report;
        var details = (ScrollerView) element("eligibility-scroll");
        details.clearAllScrollViewChildren();
        if (report.eligible()) {
            element("eligibility-dialog").setDisplay(false);
            return;
        }
        ((TextElement) element("eligibility-summary")).setText(Component.translatable("duelcraft.collection." +
                ("saved_active_cleared".equals(lists.status()) ? "saved_active_cleared" : "eligibility_rejected")));
        for (int index = 0; index < report.problems().size(); index++) {
            var issue = report.problems().get(index);
            var row = new Label().setText(Component.translatable(issue.key(), issue.code(), issue.actual(), issue.limit()));
            row.setId("eligibility-issue-" + index).addClass("wrap");
            details.addScrollViewChild(row);
        }
        for (int code : report.missing().keySet().stream().sorted().toList()) {
            var row = new Label().setText(Component.translatable("duelcraft.collection.missing_copies", code, report.missing().get(code)));
            row.setId("eligibility-missing-" + code).addClass("wrap");
            details.addScrollViewChild(row);
        }
        if (report.moreProblems()) {
            var row = new Label().setText(Component.translatable("duelcraft.collection.more_problems"));
            row.setId("eligibility-more").addClass("wrap");
            details.addScrollViewChild(row);
        }
        details.verticalScroller.setNormalizedValue(0);
        element("eligibility-dialog").setDisplay(true);
    }

    private Component failureDetail() {
        String detail = lists.detail();
        for (String code : List.of("STALE", "BUSY", "INELIGIBLE", "NOT_FOUND", "DATA_UNAVAILABLE", "INVALID")) {
            if (detail.startsWith(code)) return Component.translatable("duelcraft.collection.error_" +
                    code.toLowerCase(Locale.ROOT), detail.substring(code.length()));
        }
        return Component.literal(detail);
    }

    void initializeCollections() { lists.newDraft(); lists.refresh(); }

    void setCatalog(List<CardInfo> cards, boolean failed) {
        if (disposed) return;
        this.cards = List.copyOf(cards);
        byCode.clear();
        cards.forEach(card -> byCode.put(card.code(), card));
        catalogState = failed ? "catalog_failed" : cards.isEmpty() ? "catalog_empty" : "";
        lifecycleChanged();
    }

    void catalogLoading() {
        catalogState = "catalog_loading";
        refreshResults(false);
    }

    void dispose() {
        if (disposed) return;
        disposed = true;
        queryGeneration++;
        lists.dispose();
        search.close();
    }

    private UIElement element(String id) {
        return ui.selectId(id).findFirst().orElseThrow(() -> new IllegalStateException("Missing #" + id));
    }

    private Button button(String id) { return (Button) element(id); }

    private void text(String id, String value) {
        ((TextElement) element(id)).setText(Component.literal(value));
    }

    @SuppressWarnings("unchecked")
    private <T extends Enum<T>> void configureSelector(String id, List<T> values, T initial, Consumer<T> changed) {
        var selector = (Selector<T>) element(id);
        UIElementProvider<T> options = UIElementProvider.text(value -> Component.literal(title(value.name())));
        selector.setCandidates(values).setValue(initial, false).setCandidateUIProvider(value ->
                options.apply(value).addClass("selector-option"));
        selector.setValue(initial, false).setOnValueChanged(changed);
    }

    private static String title(String value) {
        return Character.toUpperCase(value.charAt(0)) + value.substring(1).toLowerCase(Locale.ROOT);
    }

    private void selectCard(int code) {
        selectedCode = code;
        refreshInspector(true);
        refreshSelection();
    }

    private void refreshSelection() {
        for (var section : DeckEditorModel.Section.values()) {
            var codes = sectionCodes(section);
            for (int index = 0; index < codes.size(); index++) {
                var tile = element(section.name().toLowerCase(Locale.ROOT) + "-card-" + index);
                if (codes.get(index) == selectedCode) tile.addClass("selected");
                else tile.removeClass("selected");
            }
        }
        collection.setPresentation(selectedCode, code -> model.owned().getOrDefault(code, 0L)
                + " / " + model.draft().requiredCopies().getOrDefault(code, 0));
    }

    private void refreshInspector(boolean changedCard) {
        var card = byCode.get(selectedCode);
        button("add-card").setActive(!lists.pending() && card != null);
        button("remove-card").setActive(!lists.pending() && canRemoveSelected());
        boolean selected = selectedCode != 0;
        element("inspector-empty").setDisplay(!selected);
        element("card-details-scroll").setDisplay(selected);
        element("card-edit-controls").setDisplay(selected);
        if (!selected) return;
        for (var mode : EditMode.values()) {
            var target = element("section-" + mode.name().toLowerCase(Locale.ROOT));
            if (mode == editMode) target.addClass("selected");
            else target.removeClass("selected");
        }
        if (card == null) {
            element("inspector-art").clearAllChildren();
            text("inspector-name", "Passcode " + selectedCode);
            text("inspector-stats", "");
            ((TextElement) element("inspector-description")).setText(Component.translatable("duelcraft.collection.unknown_card"));
        }
        if (card != null && changedCard) {
            element("inspector-art").clearAllChildren();
            element("inspector-art").addChild(new CollectionCardGrid.CardTile(card, art, () -> {})
                    .addClass("inspector-card"));
            text("inspector-name", card.name());
            text("inspector-stats", CardStringHelper.typeLine(card) + "\n" + CardStringHelper.atkDefLine(card));
            text("inspector-description", card.desc());
            ((ScrollerView) element("card-details-scroll")).verticalScroller.setNormalizedValue(0);
        }
        text("inspector-owned", "In collection: " + model.owned().getOrDefault(selectedCode, 0L));
        text("inspector-used", "In this list: " + model.draft().requiredCopies().getOrDefault(selectedCode, 0));
        text("inspector-missing", model.missing(selectedCode) > 0 ? "Missing " + model.missing(selectedCode) + " copies" : "All copies owned");
    }

    private List<Integer> sectionCodes(DeckEditorModel.Section section) {
        return switch (section) {
            case MAIN -> model.draft().main();
            case EXTRA -> model.draft().extra();
            case SIDE -> model.draft().side();
        };
    }

    private void edit(boolean add) {
        if (selectedCode == 0 || lists.pending()) return;
        var card = byCode.get(selectedCode);
        if (add) {
            if (card == null) return;
            if (editMode == EditMode.DECK) model.addToDeck(card.type(), selectedCode);
            else model.add(DeckEditorModel.Section.SIDE, selectedCode);
        } else if (editMode == EditMode.SIDE) {
            if (!model.remove(DeckEditorModel.Section.SIDE, selectedCode)) return;
        } else if (!model.removeFromDeck(card == null ? 0 : card.type(), selectedCode, selectedStoredSection)) {
            return;
        }
        if (editMode == EditMode.SIDE) sideOpen = true;
        refreshDecks();
        refreshPanes();
        refreshInspector(false);
        // Requery ownership filters, but keep the scroll position if membership/order is unchanged.
        refreshResults(false);
        text("editor-status", (add ? "Added one card to " : "Removed one card from ")
                + title(editMode.name()) + ". Draft only; ownership is unchanged.");
    }

    private boolean canRemoveSelected() {
        if (editMode == EditMode.SIDE) return model.draft().side().contains(selectedCode);
        if (selectedStoredSection == DeckEditorModel.Section.MAIN
                || selectedStoredSection == DeckEditorModel.Section.EXTRA) {
            return sectionCodes(selectedStoredSection).contains(selectedCode);
        }
        return model.draft().main().contains(selectedCode) || model.draft().extra().contains(selectedCode);
    }

    private void refreshDecks() {
        var seen = new HashMap<Integer, Integer>();
        for (var section : DeckEditorModel.Section.values()) {
            String id = section.name().toLowerCase(Locale.ROOT);
            var grid = (ScrollerView) element(id + "-grid");
            if (grid.isDisplayed()) {
                pendingScrollOffsets.put(grid, Math.max(0, -grid.viewContainer.getLayoutY()));
            }
            grid.clearAllScrollViewChildren();
            var codes = sectionCodes(section);
            for (int index = 0; index < codes.size(); index++) {
                int code = codes.get(index);
                var card = byCode.getOrDefault(code, new CardInfo(code, "Passcode " + code, "Metadata unavailable.", 0, 0, 0, 0, 0, 0));
                var tile = new CollectionCardGrid.CardTile(card, textures, () -> {
                    editMode = section == DeckEditorModel.Section.SIDE ? EditMode.SIDE : EditMode.DECK;
                    selectedStoredSection = section;
                    selectCard(code);
                });
                tile.setId(id + "-card-" + index);
                tile.addClass("deck-card");
                if (seen.merge(code, 1, Integer::sum) > model.owned().getOrDefault(code, 0L)) {
                    tile.addChild(new Label().setText("Missing").addClass("missing-copy"));
                }
                grid.addScrollViewChild(tile);
            }
            text(id + "-count", title(id) + " Deck  " + codes.size() + (section == DeckEditorModel.Section.MAIN ? " / 40–60" : " / 15"));
        }
        refreshSummary();
        refreshSelection();
    }

    private void refreshSummary() {
        var draft = model.draft();
        long missing = draft.requiredCopies().keySet().stream().mapToLong(model::missing).sum();
        var warnings = new ArrayList<String>();
        if (draft.main().size() < 40 || draft.main().size() > 60) warnings.add("Main needs 40–60");
        if (draft.extra().size() > 15) warnings.add("Extra exceeds 15");
        if (draft.side().size() > 15) warnings.add("Side exceeds 15");
        if (draft.requiredCopies().values().stream().anyMatch(count -> count > 3)) warnings.add("More than 3 copies");
        if (missing > 0) warnings.add(missing + " missing");
        text("deck-summary", lists.dirty() ? "Unsaved changes" : "Saved list");
        for (String id : List.of("activate-deck", "list-activate")) {
            element(id).setActive(!lists.pending() && lists.ready() && lists.stored() && !lists.dirty());
            element(id).getStyle().tooltips(Component.translatable("duelcraft.collection." +
                    (lists.dirty() ? "save_first" : "supported_checks")));
        }
        text("deck-warnings", warnings.isEmpty() ? "Drafts may be saved without owning every card." : String.join(" · ", warnings));
    }

    /** Runs once after LDLib has laid out rebuilt rows, including edits with unchanged row counts. */
    void afterLayout() {
        for (var entry : pendingScrollOffsets.entrySet()) {
            var grid = entry.getKey();
            if (!grid.isDisplayed()) continue;
            float extent = Math.max(0, grid.getContainerHeight() - grid.viewPort.getContentHeight());
            grid.verticalScroller.setNormalizedValue(extent == 0 ? 0 : Math.min(1, entry.getValue() / extent));
        }
        pendingScrollOffsets.clear();
    }

    @SuppressWarnings("unchecked")
    private void installSelectorAlignment() {
        for (String id : List.of("collection-ownership", "card-sort", "filter-measure", "list-picker")) {
            var selector = (Selector<Object>) element(id);
            // Pinned LDLib clamps root-hosted dialogs against the screen with unscaled layout coordinates.
            var listeners = selector.dialog.getBubbleListeners(UIEvents.LAYOUT_CHANGED);
            listeners.forEach(listener -> selector.dialog.removeEventListener(UIEvents.LAYOUT_CHANGED, listener));
            selector.dialog.addEventListener(UIEvents.LAYOUT_CHANGED, event -> alignSelectorDialog(selector));
        }
    }

    private void alignSelectorDialog(Selector<?> selector) {
        var dialog = selector.dialog;
        var root = ui.rootElement;
        float width = Math.max(selector.getSizeWidth(), 50);
        float left = Math.clamp(selector.getPositionX() - root.getPositionX(), 0,
                Math.max(0, root.getSizeWidth() - width));
        float top = Math.clamp(selector.getPositionY() + selector.getSizeHeight() - root.getPositionY(), 0,
                Math.max(0, root.getSizeHeight() - dialog.getSizeHeight()));
        if (Math.abs(dialog.getPositionX() - root.getPositionX() - left) < .1f
                && Math.abs(dialog.getPositionY() - root.getPositionY() - top) < .1f
                && Math.abs(dialog.getSizeWidth() - width) < .1f) return;
        dialog.layout(layout -> layout.left(left).top(top).width(width));
    }

    private void refreshPanes() {
        var deck = element("deck-sections");
        if (largeCards) deck.addClass("large");
        else deck.removeClass("large");
        element("extra-section").lss("height", largeCards ? "150" : "128");
        element("side-section").lss("height", sideOpen ? "150" : "32");
        element("side-grid").setDisplay(sideOpen);
        button("toggle-side").setText(sideOpen ? "Collapse" : "Expand");
        button("card-density").setText(largeCards ? "Cards: Large" : "Cards: Standard");
    }

    private void refreshResults(boolean reset) {
        long generation = ++queryGeneration;
        search.submit(cards, query, filters, model.owned(), model.draft(), found -> {
            if (disposed || generation != queryGeneration) return;
            boolean changed = !found.equals(results);
            if (changed) {
                results = found;
                collection.showCards(results);
            }
            collection.setPresentation(selectedCode, code -> model.owned().getOrDefault(code, 0L)
                    + " / " + model.draft().requiredCopies().getOrDefault(code, 0));
            if (reset || changed) collection.scrollToTop();
            text("result-count", results.size() + " cards · owned / in list");
            element("collection-empty").setDisplay(results.isEmpty());
            ((TextElement) element("collection-empty")).setText(Component.translatable("duelcraft.collection." +
                    (catalogState.isEmpty() ? "no_matches" : catalogState)));
        });
    }

    void requestClose() {
        lists.navigate(close);
    }

    private void showFilters(boolean open) {
        syncFilterForm();
        filtersOpen = open;
        element("filter-panel").setDisplay(open);
        element("browse-results").setDisplay(!open);
        button("toggle-filters").setText(open ? "Close filters" : "Filters");
    }

    private void applyOrdering() {
        filters = new CardSearch.Filters(filters.categoryAny(), filters.subtypeAny(), filters.requiredProperties(),
                filters.raceAny(), filters.attributeAny(), filters.measure(), filters.measureRange(), filters.atkRange(),
                filters.defRange(), filters.scaleRange(), ownership, sort, alternateFormats);
        refreshResults(true);
        refreshChips();
    }

    private void syncFilterForm() {
        masks.put("category", (long) filters.categoryAny());
        masks.put("subtype", (long) filters.subtypeAny());
        masks.put("property", (long) filters.requiredProperties());
        masks.put("race", filters.raceAny());
        masks.put("attribute", (long) filters.attributeAny());
        measure = filters.measure();
        resetSelector("filter-measure", measure);
        choices.values().forEach(choice -> {
            if ((mask(choice.group()) & choice.bit()) != 0) choice.button().addClass("selected");
            else choice.button().removeClass("selected");
        });
        for (String group : List.of("measure", "atk", "def", "scale")) {
            var value = appliedRange(group);
            String min = value == null ? "" : Integer.toString(value.min());
            String max = value == null || value.max() == Integer.MAX_VALUE ? "" : Integer.toString(value.max());
            ranges.put(group + "-min", min);
            ranges.put(group + "-max", max);
            ((TextField) element("filter-" + group + "-min")).setText(min, false);
            ((TextField) element("filter-" + group + "-max")).setText(max, false);
        }
    }

    private CardSearch.Range appliedRange(String group) {
        return switch (group) {
            case "measure" -> filters.measureRange();
            case "atk" -> filters.atkRange();
            case "def" -> filters.defRange();
            default -> filters.scaleRange();
        };
    }

    private boolean applyFilters() {
        try {
            filters = new CardSearch.Filters((int) mask("category"), (int) mask("subtype"),
                    (int) mask("property"), mask("race"), (int) mask("attribute"), measure,
                    range("measure"), range("atk"), range("def"), range("scale"), ownership, sort, alternateFormats);
            refreshResults(true);
            refreshChips();
            return true;
        } catch (IllegalArgumentException invalid) {
            text("editor-status", "Enter whole numbers with minimum no greater than maximum.");
            return false;
        }
    }

    private CardSearch.Range range(String group) {
        String min = ranges.getOrDefault(group + "-min", "");
        String max = ranges.getOrDefault(group + "-max", "");
        if (min.isBlank() && max.isBlank()) return null;
        int lower = min.isBlank() ? 0 : Integer.parseInt(min);
        int upper = max.isBlank() ? Integer.MAX_VALUE : Integer.parseInt(max);
        if (lower < 0 || lower > upper) throw new IllegalArgumentException("Invalid range");
        return new CardSearch.Range(lower, upper);
    }

    private long mask(String group) { return masks.getOrDefault(group, 0L); }

    @SuppressWarnings("unchecked")
    private void clearFilters() {
        masks.clear();
        ranges.clear();
        choices.values().forEach(choice -> choice.button().removeClass("selected"));
        query = "";
        filters = CardSearch.Filters.ALL;
        alternateFormats = false;
        ownership = CardSearch.Ownership.ALL;
        sort = CardSearch.Sort.NAME;
        measure = CardSearch.Measure.ANY;
        ((TextField) element("collection-search")).setText("", false);
        ((Selector<CardSearch.Ownership>) element("collection-ownership")).setValue(ownership, false);
        ((Selector<CardSearch.Sort>) element("card-sort")).setValue(sort, false);
        ((Selector<CardSearch.Measure>) element("filter-measure")).setValue(measure, false);
        for (String group : List.of("measure", "atk", "def", "scale")) {
            for (String bound : List.of("min", "max")) ((TextField) element("filter-" + group + "-" + bound)).setText("", false);
        }
        refreshResults(true);
        refreshChips();
        showFilters(false);
    }

    private void createFilters() {
        addChoices("category", "Card category · any", new String[]{"Monster", "Spell", "Trap"}, new long[]{TYPE_MONSTER, TYPE_SPELL, TYPE_TRAP});
        addChoices("attribute", "Attribute · any", new String[]{"Earth", "Water", "Fire", "Wind", "Light", "Dark", "Divine"},
                new long[]{ATTRIBUTE_EARTH, ATTRIBUTE_WATER, ATTRIBUTE_FIRE, ATTRIBUTE_WIND, ATTRIBUTE_LIGHT, ATTRIBUTE_DARK, ATTRIBUTE_DIVINE});
        addChoices("subtype", "Frame / subtype · any", new String[]{"Normal", "Fusion", "Ritual", "Synchro", "Xyz", "Link", "Quickplay", "Continuous", "Equip", "Field", "Counter"},
                new long[]{TYPE_NORMAL, TYPE_FUSION, TYPE_RITUAL, TYPE_SYNCHRO, TYPE_XYZ, TYPE_LINK, TYPE_QUICKPLAY, TYPE_CONTINUOUS, TYPE_EQUIP, TYPE_FIELD, TYPE_COUNTER});
        addChoices("property", "Properties · all required", new String[]{"Effect", "Tuner", "Pendulum", "Spirit", "Union", "Gemini", "Flip", "Toon"},
                new long[]{TYPE_EFFECT, TYPE_TUNER, TYPE_PENDULUM, TYPE_SPIRIT, TYPE_UNION, TYPE_GEMINI, TYPE_FLIP, TYPE_TOON});
        long[] races = {RACE_WARRIOR, RACE_SPELLCASTER, RACE_FAIRY, RACE_FIEND, RACE_ZOMBIE, RACE_MACHINE,
                RACE_AQUA, RACE_PYRO, RACE_ROCK, RACE_WINGEDBEAST, RACE_PLANT, RACE_INSECT, RACE_THUNDER,
                RACE_DRAGON, RACE_BEAST, RACE_BEASTWARRIOR, RACE_DINOSAUR, RACE_FISH, RACE_SEASERPENT,
                RACE_REPTILE, RACE_PSYCHIC, RACE_DIVINE, RACE_CREATORGOD, RACE_WYRM, RACE_CYBERSE,
                RACE_ILLUSION, RACE_CYBORG, RACE_MAGICALKNIGHT, RACE_HIGHDRAGON, RACE_OMEGAPSYCHIC,
                RACE_CELESTIALWARRIOR, RACE_GALAXY, RACE_YOKAI};
        String[] raceNames = new String[races.length];
        for (int index = 0; index < races.length; index++) raceNames[index] = CardStringHelper.raceName(races[index]);
        addChoices("race", "Monster type · any", raceNames, races);
        for (String group : List.of("measure", "atk", "def", "scale")) {
            for (String bound : List.of("min", "max")) {
                String key = group + "-" + bound;
                ((TextField) element("filter-" + key)).setTextResponder(value -> ranges.put(key, value));
            }
        }
    }

    private void addChoices(String group, String heading, String[] names, long[] bits) {
        var container = element("filter-groups");
        container.addChild(new Label().setText(Component.literal(heading)).addClass("filter-heading"));
        var row = new UIElement().addClass("filter-choices");
        for (int index = 0; index < names.length; index++) {
            String name = names[index];
            long bit = bits[index];
            String id = "filter-" + group + "-" + name.toLowerCase(Locale.ROOT).replace(' ', '-');
            var choice = new Button().setText(Component.literal(name));
            choice.setId(id).addClass("filter-choice");
            choice.setOnClick(event -> {
                long next = mask(group) ^ bit;
                masks.put(group, next);
                if ((next & bit) != 0) choice.addClass("selected");
                else choice.removeClass("selected");
            });
            choices.put(id, new FilterChoice(group, bit, choice));
            row.addChild(choice);
        }
        container.addChild(row);
    }

    private void refreshChips() {
        button("alternate-formats").setText("Alternate formats: " + (alternateFormats ? "On" : "Off"));
        if (alternateFormats) button("alternate-formats").addClass("selected");
        else button("alternate-formats").removeClass("selected");
        var chips = (ScrollerView) element("active-filters");
        chips.clearAllScrollViewChildren();
        if (!query.isBlank()) addChip(chips, "query", query, () -> {
            query = "";
            ((TextField) element("collection-search")).setText("", false);
        });
        for (var entry : choices.entrySet()) {
            var choice = entry.getValue();
            long applied = switch (choice.group()) {
                case "category" -> filters.categoryAny();
                case "subtype" -> filters.subtypeAny();
                case "property" -> filters.requiredProperties();
                case "race" -> filters.raceAny();
                case "attribute" -> filters.attributeAny();
                default -> 0;
            };
            if ((applied & choice.bit()) != 0) {
                addChip(chips, entry.getKey().substring("filter-".length()),
                        choice.button().text.getText().getString(), () -> {
                            masks.put(choice.group(), mask(choice.group()) & ~choice.bit());
                            choice.button().removeClass("selected");
                        });
            }
        }
        if (filters.ownership() != CardSearch.Ownership.ALL) addChip(chips, "ownership", title(filters.ownership().name()), () -> {
            ownership = CardSearch.Ownership.ALL;
            resetSelector("collection-ownership", ownership);
        });
        if (filters.measure() != CardSearch.Measure.ANY) addChip(chips, "measure", title(filters.measure().name()), () -> {
            measure = CardSearch.Measure.ANY;
            resetSelector("filter-measure", measure);
        });
        for (String group : List.of("measure", "atk", "def", "scale")) {
            CardSearch.Range range = appliedRange(group);
            if (range != null) addChip(chips, group.equals("measure") ? "number" : group,
                    title(group) + " " + range.min() + "–" + (range.max() == Integer.MAX_VALUE ? "any" : range.max()), () -> {
                        for (String bound : List.of("min", "max")) {
                            ranges.remove(group + "-" + bound);
                            ((TextField) element("filter-" + group + "-" + bound)).setText("", false);
                        }
                    });
        }
        chips.setDisplay(!chips.viewContainer.getChildren().isEmpty());
    }

    @SuppressWarnings("unchecked")
    private <T> void resetSelector(String id, T value) {
        ((Selector<T>) element(id)).setValue(value, false);
    }

    private void addChip(ScrollerView chips, String id, String label, Runnable remove) {
        var chip = new Button().setText(Component.literal(label + " ×"));
        chip.setId("chip-" + id).addClass("filter-chip");
        chip.setOnClick(event -> { syncFilterForm(); remove.run(); applyFilters(); });
        chips.addScrollViewChild(chip);
    }
}
