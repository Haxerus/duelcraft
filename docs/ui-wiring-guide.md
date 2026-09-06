# Duel Screen UI Wiring Guide

This guide covers how to implement the remaining UI features in `LDLibDuelScreen.java`. The boilerplate is already in place: XML loading, element lookups, reactive bindings for LP/labels/pile counts, and the dirty-flag tick handler skeleton.

## What's Already Done

- `LDLibDuelScreen.open()` loads XML via `XmlUtils.loadXml()` and opens the screen
- `UIRefresher` caches all element references by ID in the constructor
- LP bars, title label, turn/phase label, and pile counts are bound via `SupplierDataSource` (auto-update every tick)
- `ClientDuelState` has `DirtyFlag` enum with flags marked in every `applyMessage` branch
- `onTick()` consumes dirty flags — just needs dispatch logic filled in
- Phase button state updates when `PROMPT` flag is dirty

## Quick Reference: LDLib2 API

```java
// Find element by ID
UIElement elem = modularUI.getElementById("plr-mon-0");

// CSS class manipulation
elem.addClass("target");
elem.removeClass("target");
elem.hasClass("hidden");

// Add/remove children
parent.addChild(child);
parent.removeChild(child);
parent.getChildren();               // List<UIElement>

// Create elements in code
var el = new UIElement();
el.addClasses("card-slot", "hand-card");
el.lss("background", "sdf(#1a3a6a, 3, 2)");

var label = new Label();
label.setText(Component.literal("text"));

var btn = new Button();
btn.setText(Component.literal("Click"));
btn.setOnClick(event -> { ... });
btn.setActive(false);               // disable

// Events
elem.addEventListener(UIEvents.CLICK, event -> { ... });
elem.addEventListener(UIEvents.MOUSE_ENTER, event -> { ... });
elem.addEventListener(UIEvents.MOUSE_LEAVE, event -> { ... });

// CSS selector queries on any element
elem.select(".card-image");          // Stream<UIElement>
elem.select(".zone-icon", Label.class); // Stream<Label>
```

## Phase 2: Implementing Dirty-Flag Dispatchers

### Fill in `onTick()`

The skeleton is:
```java
private void onTick(UIEvent event) {
    if (!state.isDirty()) return;
    var flags = state.consumeDirtyFlags();
    // TODO: dispatch here
}
```

Add the dispatch logic:
```java
int plr = state.localPlayer;
int opp = state.opponent();

if (flags.contains(DirtyFlag.HAND_0) || flags.contains(DirtyFlag.HAND_1)) {
    if (flags.contains(plr == 0 ? DirtyFlag.HAND_0 : DirtyFlag.HAND_1))
        rebuildHand(plrHandContainer, state.hand[plr], plr, true);
    if (flags.contains(opp == 0 ? DirtyFlag.HAND_0 : DirtyFlag.HAND_1))
        rebuildHand(oppHandContainer, state.hand[opp], opp, false);
}
if (flags.contains(plr == 0 ? DirtyFlag.MZONE_0 : DirtyFlag.MZONE_1))
    refreshMonsterZones(plr);
if (flags.contains(opp == 0 ? DirtyFlag.MZONE_0 : DirtyFlag.MZONE_1))
    refreshMonsterZones(opp);
if (flags.contains(plr == 0 ? DirtyFlag.SZONE_0 : DirtyFlag.SZONE_1))
    refreshSpellZones(plr);
if (flags.contains(opp == 0 ? DirtyFlag.SZONE_0 : DirtyFlag.SZONE_1))
    refreshSpellZones(opp);
if (flags.contains(DirtyFlag.PROMPT)) {
    rebuildPrompt();
    updatePhaseButtons();
}
if (flags.contains(DirtyFlag.CHAIN))
    updateStatusLabel();
if (flags.contains(DirtyFlag.WINNER))
    showWinOverlay();
```

### `rebuildHand()` — rebuild hand card children

Hands are dynamic (cards are drawn/discarded), so we clear and rebuild:

```java
private void rebuildHand(UIElement container, List<Integer> codes, int player, boolean isLocal) {
    // Clear existing card children (scroller-view content)
    // Note: for scroller-view, children are inside the internal view container.
    // You may need to find the right child container. Try:
    container.getChildren().stream()
        .filter(c -> c.hasClass("hand-card"))
        .toList()
        .forEach(container::removeChild);

    for (int i = 0; i < codes.size(); i++) {
        int code = codes.get(i);
        int seq = i;
        var card = new UIElement();
        card.addClasses("card-slot", "hand-card");

        if (isLocal && code != 0) {
            // Placeholder: show card code. Replace with card art sprite later.
            var label = new Label();
            label.setText(Component.literal(String.valueOf(code)));
            label.lss("font-size", "6");
            label.lss("horizontal-align", "center");
            card.addChild(label);
        } else {
            // Opponent cards or unknown: show card back
            var back = new UIElement();
            back.addClass("card-back");
            card.addChild(back);
        }

        // Click handler (local player only)
        if (isLocal) {
            card.addEventListener(UIEvents.CLICK, e -> onCardClicked(player, LOCATION_HAND, seq));
        }

        // Hover -> card info
        card.addEventListener(UIEvents.MOUSE_ENTER, e -> showCardInfo(code));
        card.addEventListener(UIEvents.MOUSE_LEAVE, e -> hideCardInfo());

        // Highlight if has actions
        var loc = new ClientDuelState.CardLocation(player, LOCATION_HAND, seq);
        if (state.cardActions.containsKey(loc)) {
            card.addClass("target");
        }

        container.addChild(card);
    }
}
```

### `refreshMonsterZones()` — update zone contents in-place

Zone slots are fixed XML elements. We update their *contents*, not the elements themselves:

```java
private void refreshMonsterZones(int player) {
    for (int i = 0; i < 5; i++) {
        var slot = monsterSlots[player][i];
        if (slot == null) continue;
        refreshZoneSlot(slot, state.mzone[player][i], state.mzonePos[player][i],
                player, LOCATION_MZONE, i);
    }
    // EMZ slots (mzone indices 5 and 6)
    // EMZ mapping: emzSlots[0] = emz-left, emzSlots[1] = emz-right
    // In ygopro-core: mzone[p][5] and mzone[p][6] are the EMZ
    // Which EMZ belongs to which player depends on the duel setup.
    // For now, emz-left = mzone[0][5], emz-right = mzone[1][5] (simplified)
    refreshZoneSlot(emzSlots[0], state.mzone[0][5], state.mzonePos[0][5],
            0, LOCATION_MZONE, 5);
    refreshZoneSlot(emzSlots[1], state.mzone[1][5], state.mzonePos[1][5],
            1, LOCATION_MZONE, 5);
}

private void refreshSpellZones(int player) {
    for (int i = 0; i < 5; i++) {
        var slot = spellSlots[player][i];
        if (slot == null) continue;
        refreshZoneSlot(slot, state.szone[player][i], state.szonePos[player][i],
                player, LOCATION_SZONE, i);
    }
    // Field spell zone = szone[player][5]
    if (fieldSpellSlots[player] != null) {
        refreshZoneSlot(fieldSpellSlots[player], state.szone[player][5], state.szonePos[player][5],
                player, LOCATION_SZONE, 5);
    }
}

private void refreshZoneSlot(UIElement slot, int code, int position,
                              int player, int locationType, int sequence) {
    // Remove any existing card visual
    slot.getChildren().stream()
        .filter(c -> c.hasClass("card-image") || c.hasClass("card-back"))
        .toList()
        .forEach(slot::removeChild);

    if (code != 0) {
        boolean faceDown = (position & (POS_FACEDOWN_ATTACK | POS_FACEDOWN_DEFENSE)) != 0;
        boolean defense = (position & (POS_FACEUP_DEFENSE | POS_FACEDOWN_DEFENSE)) != 0;

        var cardVisual = new UIElement();
        if (faceDown) {
            cardVisual.addClass("card-back");
        } else {
            cardVisual.addClass("card-image");
            // Placeholder: code label. Replace with sprite later.
            var label = new Label();
            label.setText(Component.literal(String.valueOf(code)));
            label.lss("font-size", "6");
            cardVisual.addChild(label);
        }
        if (defense) {
            cardVisual.addClass("defense");
        }
        slot.addChild(cardVisual);

        // Hide zone icon when card is present
        slot.select(".zone-icon").forEach(icon -> icon.addClass("hidden"));
    } else {
        // Show zone icon when empty
        slot.select(".zone-icon").forEach(icon -> icon.removeClass("hidden"));
    }

    // Highlight if has actions
    var loc = new ClientDuelState.CardLocation(player, locationType, sequence);
    if (state.cardActions.containsKey(loc)) {
        slot.addClass("target");
    } else {
        slot.removeClass("target");
    }
}
```

### `updateStatusLabel()`

```java
private void updateStatusLabel() {
    if (statusLabel == null) return;
    if (!state.chain.isEmpty()) {
        statusLabel.removeClass("hidden");
        if (statusLabel instanceof Label lbl)
            lbl.setText(Component.literal("Chain: " + state.chain.size()));
    } else if (state.pendingPrompt == null && !state.isLocalTurn()) {
        statusLabel.removeClass("hidden");
        if (statusLabel instanceof Label lbl)
            lbl.setText(Component.literal("Waiting..."));
    } else {
        statusLabel.addClass("hidden");
    }
}
```

### `showWinOverlay()`

```java
private void showWinOverlay() {
    if (promptOverlay == null) return;
    promptOverlay.removeClass("hidden");
    if (promptTitle instanceof Label title) {
        title.setText(Component.literal(
            state.winner == state.localPlayer ? "You Win!" : "You Lose!"));
    }
    if (promptBody != null) promptBody.getChildren().forEach(promptBody::removeChild);
    if (promptButtons != null) {
        promptButtons.getChildren().forEach(promptButtons::removeChild);
        var closeBtn = new Button();
        closeBtn.setText(Component.literal("Close"));
        closeBtn.addClasses("prompt-btn");
        closeBtn.setOnClick(e -> Minecraft.getInstance().setScreen(null));
        promptButtons.addChild(closeBtn);
    }
}
```

## Phase 3: Event Wiring

### Card click handler

```java
private void onCardClicked(int player, int location, int sequence) {
    // Check for card actions (from SelectIdleCmd/SelectBattleCmd)
    var loc = new ClientDuelState.CardLocation(player, location, sequence);
    var actions = state.cardActions.get(loc);

    if (actions != null && !actions.isEmpty()) {
        showActionMenu(actions);
        return;
    }

    // Check for card selection prompts
    if (state.pendingPrompt instanceof DuelMessage.SelectCard) {
        handleCardSelection(player, location, sequence);
    } else if (state.pendingPrompt instanceof DuelMessage.SelectPlace) {
        handlePlaceSelection(player, location, sequence);
    }
}
```

### Action menu (reuses prompt overlay)

Always shows a menu, even for a single action:

```java
private void showActionMenu(List<ClientDuelState.CardAction> actions) {
    promptOverlay.removeClass("hidden");
    if (promptTitle instanceof Label title)
        title.setText(Component.literal("Choose Action"));
    if (promptBody != null) promptBody.getChildren().forEach(promptBody::removeChild);
    if (promptButtons != null) {
        promptButtons.getChildren().forEach(promptButtons::removeChild);

        for (var action : actions) {
            var btn = new Button();
            btn.setText(Component.literal(action.label()));
            btn.addClasses("prompt-btn");
            btn.setOnClick(e -> {
                LDLibDuelScreen.sendResponse(state,
                    ResponseBuilder.selectCmd(action.actionType(), action.listIndex()));
            });
            promptButtons.addChild(btn);
        }

        // Cancel button
        var cancelBtn = new Button();
        cancelBtn.setText(Component.literal("Cancel"));
        cancelBtn.addClasses("prompt-btn");
        cancelBtn.setOnClick(e -> promptOverlay.addClass("hidden"));
        promptButtons.addChild(cancelBtn);
    }
}
```

### Prompt system — `rebuildPrompt()`

Dispatch on `state.pendingPrompt` type. Each category follows the same pattern: show overlay, set title, populate body/buttons, wire click handlers that call `sendResponse()`.

```java
private void rebuildPrompt() {
    if (state.pendingPrompt == null) {
        if (promptOverlay != null) promptOverlay.addClass("hidden");
        if (statusLabel != null) statusLabel.addClass("hidden");
        return;
    }

    switch (state.pendingPrompt) {
        // ── Idle/Battle: handled by card clicks + phase buttons ──
        case DuelMessage.SelectIdleCmd ignored -> {
            promptOverlay.addClass("hidden");
            refreshTargetHighlights();
        }
        case DuelMessage.SelectBattleCmd ignored -> {
            promptOverlay.addClass("hidden");
            refreshTargetHighlights();
        }

        // ── Yes/No ──
        case DuelMessage.SelectYesNo sel ->
            buildYesNoPrompt("Yes or No? (desc=" + sel.desc() + ")");
        case DuelMessage.SelectEffectYn sel ->
            buildYesNoPrompt("Activate effect? (code=" + sel.code() + ")");

        // ── Option selection ──
        case DuelMessage.SelectOption sel ->
            buildOptionPrompt("Choose Option",
                sel.options().stream().map(String::valueOf).toList(),
                i -> LDLibDuelScreen.sendResponse(state, ResponseBuilder.selectOption(i)));
        case DuelMessage.RockPaperScissors sel ->
            buildOptionPrompt("Rock Paper Scissors",
                List.of("Rock", "Paper", "Scissors"),
                i -> LDLibDuelScreen.sendResponse(state, ResponseBuilder.rockPaperScissors(i + 1)));

        // ── Chain ──
        case DuelMessage.SelectChain sel -> buildChainPrompt(sel);

        // ── Card selection ──
        case DuelMessage.SelectCard sel -> buildCardSelectionPrompt(sel);

        // ── Position ──
        case DuelMessage.SelectPosition sel -> buildPositionPrompt(sel);

        // ── Place selection (handled by zone clicks) ──
        case DuelMessage.SelectPlace sel -> {
            promptOverlay.addClass("hidden");
            // TODO: highlight valid zones based on sel.field() bitmask
        }

        // ── Fallback for unimplemented prompts ──
        default -> {
            promptOverlay.removeClass("hidden");
            if (promptTitle instanceof Label title)
                title.setText(Component.literal(state.pendingPrompt.getClass().getSimpleName()));
            // Clear body/buttons — user will need to check logs for now
        }
    }
}
```

### Yes/No prompt builder

```java
private void buildYesNoPrompt(String title) {
    promptOverlay.removeClass("hidden");
    if (promptTitle instanceof Label t) t.setText(Component.literal(title));
    clearPromptContent();

    var yesBtn = new Button();
    yesBtn.setText(Component.literal("Yes"));
    yesBtn.addClasses("prompt-btn");
    yesBtn.setOnClick(e -> LDLibDuelScreen.sendResponse(state, ResponseBuilder.selectYesNo(true)));

    var noBtn = new Button();
    noBtn.setText(Component.literal("No"));
    noBtn.addClasses("prompt-btn");
    noBtn.setOnClick(e -> LDLibDuelScreen.sendResponse(state, ResponseBuilder.selectYesNo(false)));

    promptButtons.addChild(yesBtn);
    promptButtons.addChild(noBtn);
}
```

### Option prompt builder

```java
private void buildOptionPrompt(String title, List<String> options,
                                java.util.function.IntConsumer onSelect) {
    promptOverlay.removeClass("hidden");
    if (promptTitle instanceof Label t) t.setText(Component.literal(title));
    clearPromptContent();

    for (int i = 0; i < options.size(); i++) {
        int idx = i;
        var btn = new Button();
        btn.setText(Component.literal(options.get(i)));
        btn.addClasses("prompt-btn");
        btn.setOnClick(e -> onSelect.accept(idx));
        promptButtons.addChild(btn);
    }
}
```

### Chain prompt builder

```java
private void buildChainPrompt(DuelMessage.SelectChain sel) {
    // Auto-pass if not forced and no options
    if (!sel.forced() && sel.chains().isEmpty()) {
        LDLibDuelScreen.sendResponse(state, ResponseBuilder.selectChain(-1));
        return;
    }

    promptOverlay.removeClass("hidden");
    if (promptTitle instanceof Label t) t.setText(Component.literal("Activate Chain?"));
    clearPromptContent();

    for (int i = 0; i < sel.chains().size(); i++) {
        int idx = i;
        var chain = sel.chains().get(i);
        var btn = new Button();
        btn.setText(Component.literal("Card " + chain.code()));
        btn.addClasses("prompt-btn");
        btn.setOnClick(e -> LDLibDuelScreen.sendResponse(state, ResponseBuilder.selectChain(idx)));
        promptButtons.addChild(btn);
    }

    // Pass button (if not forced)
    if (!sel.forced()) {
        var passBtn = new Button();
        passBtn.setText(Component.literal("Pass"));
        passBtn.addClasses("prompt-btn");
        passBtn.setOnClick(e -> LDLibDuelScreen.sendResponse(state, ResponseBuilder.selectChain(-1)));
        promptButtons.addChild(passBtn);
    }
}
```

### Card selection prompt

This is the most complex prompt — multiple cards must be selected:

```java
private final List<Integer> selectedIndices = new ArrayList<>();

private void buildCardSelectionPrompt(DuelMessage.SelectCard sel) {
    selectedIndices.clear();
    promptOverlay.removeClass("hidden");
    if (promptTitle instanceof Label t)
        t.setText(Component.literal("Select " + sel.min() + "-" + sel.max() + " card(s)"));
    clearPromptContent();

    // Show selectable cards in prompt body
    for (int i = 0; i < sel.cards().size(); i++) {
        int idx = i;
        var card = sel.cards().get(i);
        var entry = new Button();
        entry.setText(Component.literal("Card " + card.code()));
        entry.addClasses("prompt-btn");
        entry.setOnClick(e -> {
            if (selectedIndices.contains(idx)) {
                selectedIndices.remove(Integer.valueOf(idx));
                entry.removeClass("target");
            } else if (selectedIndices.size() < sel.max()) {
                selectedIndices.add(idx);
                entry.addClass("target");
            }
        });
        promptBody.addChild(entry);
    }

    // Confirm button
    var confirmBtn = new Button();
    confirmBtn.setText(Component.literal("Confirm"));
    confirmBtn.addClasses("prompt-btn");
    confirmBtn.setOnClick(e -> {
        if (selectedIndices.size() >= sel.min()) {
            LDLibDuelScreen.sendResponse(state,
                ResponseBuilder.selectCards(selectedIndices.stream().mapToInt(Integer::intValue).toArray()));
        }
    });
    promptButtons.addChild(confirmBtn);

    // Cancel button (if cancelable)
    if (sel.cancelable() != 0) {
        var cancelBtn = new Button();
        cancelBtn.setText(Component.literal("Cancel"));
        cancelBtn.addClasses("prompt-btn");
        cancelBtn.setOnClick(e ->
            LDLibDuelScreen.sendResponse(state, ResponseBuilder.selectCards()));
        promptButtons.addChild(cancelBtn);
    }
}
```

### Position prompt

```java
private void buildPositionPrompt(DuelMessage.SelectPosition sel) {
    promptOverlay.removeClass("hidden");
    if (promptTitle instanceof Label t)
        t.setText(Component.literal("Choose Position"));
    clearPromptContent();

    if ((sel.positions() & POS_FACEUP_ATTACK) != 0) {
        addPositionButton("Face-up ATK", POS_FACEUP_ATTACK);
    }
    if ((sel.positions() & POS_FACEDOWN_ATTACK) != 0) {
        addPositionButton("Face-down ATK", POS_FACEDOWN_ATTACK);
    }
    if ((sel.positions() & POS_FACEUP_DEFENSE) != 0) {
        addPositionButton("Face-up DEF", POS_FACEUP_DEFENSE);
    }
    if ((sel.positions() & POS_FACEDOWN_DEFENSE) != 0) {
        addPositionButton("Face-down DEF", POS_FACEDOWN_DEFENSE);
    }
}

private void addPositionButton(String label, int position) {
    var btn = new Button();
    btn.setText(Component.literal(label));
    btn.addClasses("prompt-btn");
    btn.setOnClick(e -> LDLibDuelScreen.sendResponse(state, ResponseBuilder.selectPosition(position)));
    promptButtons.addChild(btn);
}
```

### Helper: clear prompt content

```java
private void clearPromptContent() {
    if (promptBody != null) {
        new ArrayList<>(promptBody.getChildren()).forEach(promptBody::removeChild);
    }
    if (promptButtons != null) {
        new ArrayList<>(promptButtons.getChildren()).forEach(promptButtons::removeChild);
    }
}
```

### Helper: refresh target highlights

Called when SelectIdleCmd/SelectBattleCmd arrives to highlight clickable cards:

```java
private void refreshTargetHighlights() {
    // Remove all existing highlights
    ui.ui.rootElement.select(".target").forEach(el -> el.removeClass("target"));

    // Add highlights for cards with actions
    for (var entry : state.cardActions.entrySet()) {
        var loc = entry.getKey();
        UIElement slot = findSlotForLocation(loc);
        if (slot != null) slot.addClass("target");
    }
}

private UIElement findSlotForLocation(ClientDuelState.CardLocation loc) {
    return switch (loc.location()) {
        case LOCATION_HAND -> null; // hands are rebuilt, highlights added during rebuild
        case LOCATION_MZONE -> {
            if (loc.sequence() < 5) yield monsterSlots[loc.controller()][loc.sequence()];
            else if (loc.sequence() == 5) yield emzSlots[loc.controller()];
            else yield null;
        }
        case LOCATION_SZONE -> {
            if (loc.sequence() < 5) yield spellSlots[loc.controller()][loc.sequence()];
            else yield fieldSpellSlots[loc.controller()];
        }
        default -> null;
    };
}
```

### Wire zone slot click handlers

In the `UIRefresher` constructor, wire click events on all zone slots:

```java
// Wire zone clicks
int plrIdx = state.localPlayer;
for (int i = 0; i < 5; i++) {
    int seq = i;
    if (monsterSlots[plrIdx][i] != null)
        monsterSlots[plrIdx][i].addEventListener(UIEvents.CLICK,
            e -> onCardClicked(plrIdx, LOCATION_MZONE, seq));
    if (spellSlots[plrIdx][i] != null)
        spellSlots[plrIdx][i].addEventListener(UIEvents.CLICK,
            e -> onCardClicked(plrIdx, LOCATION_SZONE, seq));
}
```

### Wire pile clicks (zone inspector)

```java
private void wirePileClicks() {
    wirePileClick("plr-graveyard", "Your Graveyard", state.localPlayer, LOCATION_GRAVE);
    wirePileClick("opp-graveyard", "Opp Graveyard", state.opponent(), LOCATION_GRAVE);
    wirePileClick("plr-banished", "Your Banished", state.localPlayer, LOCATION_REMOVED);
    wirePileClick("opp-banished", "Opp Banished", state.opponent(), LOCATION_REMOVED);

    var closeBtn = ui.getElementById("zone-inspector-close");
    if (closeBtn instanceof Button btn) {
        btn.setOnClick(e -> zoneInspector.addClass("hidden"));
    }
}

private void wirePileClick(String elementId, String title, int player, int location) {
    var elem = ui.getElementById(elementId);
    if (elem != null) {
        elem.addEventListener(UIEvents.CLICK, e -> {
            zoneInspector.removeClass("hidden");
            var titleLabel = ui.getElementById("zone-inspector-title");
            if (titleLabel instanceof Label lbl) lbl.setText(Component.literal(title));
            // TODO: populate zone-inspector-list with card entries
        });
    }
}
```

### Wire card info banner (hover)

```java
private void showCardInfo(int code) {
    if (code == 0 || cardInfoBanner == null) return;
    cardInfoBanner.removeClass("hidden");
    var nameLabel = ui.getElementById("card-name-label");
    if (nameLabel instanceof Label lbl) lbl.setText(Component.literal("Card #" + code));
    // TODO: look up actual card name from database
}

private void hideCardInfo() {
    if (cardInfoBanner != null) cardInfoBanner.addClass("hidden");
}
```

## Phase 4: Testing Checklist

Run `/duel test` to start a solo duel against the AI.

### Basic rendering
- [ ] Screen opens without errors
- [ ] All zones visible in correct positions
- [ ] HUD bar shows LP bars and labels
- [ ] Phase buttons visible in center row

### Data binding
- [ ] LP bars update when damage is dealt
- [ ] Pile counts update (deck decreases on draw, GY increases on destroy)
- [ ] Turn/phase label updates on new turn/phase

### Hand management
- [ ] Player hand shows cards when drawn
- [ ] Opponent hand shows face-down cards
- [ ] Hand updates when cards are played (removed from hand)

### Zone management
- [ ] Monster zone shows card when summoned
- [ ] Defense position cards appear rotated (`.defense` class)
- [ ] Face-down cards show `.card-back` style
- [ ] Zone icon reappears when card leaves zone

### Card interactions
- [ ] Cards with actions get `.target` highlight during SelectIdleCmd
- [ ] Clicking a highlighted card shows action menu
- [ ] Action menu always shows even for single action
- [ ] Selecting "Summon" sends correct response and card appears on field
- [ ] "Cancel" button dismisses the menu

### Phase flow
- [ ] BP button activates during Main Phase 1 when canBattle is true
- [ ] EP button activates when canEnd is true
- [ ] Clicking BP transitions to Battle Phase
- [ ] M2 button appears during Battle Phase when canMain2 is true

### Prompts
- [ ] Yes/No prompt appears for SelectYesNo
- [ ] Chain prompt shows options + Pass button
- [ ] Card selection prompt allows multi-select + confirm
- [ ] Position prompt shows valid position options
- [ ] Auto-pass works for non-forced empty chains

### Status
- [ ] "Waiting..." appears when it's opponent's turn
- [ ] "Chain: N" appears during chain resolution
- [ ] Status hides when it's your turn with a prompt

### End of duel
- [ ] Win/lose overlay appears
- [ ] Close button returns to game

## Notes

- **Card art**: Currently shows card codes as text. Once you have downscaled card images, replace the `Label` placeholder in `refreshZoneSlot()` and `rebuildHand()` with `element.lss("background", "sprite(duelcraft:textures/cards/" + code + ".png)")`.
- **Card names**: The card info banner currently shows "Card #12345". To show real names, you'll need to query the card database (SQLite via JNI, or a prebuilt lookup map).
- **Zone inspector**: The list population is left as TODO. When a pile is clicked, you'll need to fetch the pile contents from `ClientDuelState` (GY/banished are tracked by count only — you may need to track actual card codes in those piles for browsing).
- **EMZ mapping**: The current code maps `emz-left` to `mzone[0][5]` and `emz-right` to `mzone[1][5]`. Verify this matches ygopro-core's EMZ assignment for your duel configuration.
