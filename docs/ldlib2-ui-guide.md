# LDLib2 Duel UI Implementation Guide

This guide walks you through building the Duelcraft duel interface with LDLib2. The skeleton in `LDLibDuelScreen.java` compiles and runs — this guide explains how to fill it in.

## Quick Reference: Package Names

The docs say `com.lowdragmc.ldlib2.*` but the actual packages are `com.lowdragmc.lowdraglib2.*`:

| What | Import |
|------|--------|
| UIElement | `com.lowdragmc.lowdraglib2.gui.ui.UIElement` |
| Label | `com.lowdragmc.lowdraglib2.gui.ui.elements.Label` |
| Button | `com.lowdragmc.lowdraglib2.gui.ui.elements.Button` |
| ModularUI | `com.lowdragmc.lowdraglib2.gui.ui.ModularUI` |
| UI | `com.lowdragmc.lowdraglib2.gui.ui.UI` |
| ModularUIScreen | `com.lowdragmc.lowdraglib2.gui.holder.ModularUIScreen` |
| StylesheetManager | `com.lowdragmc.lowdraglib2.gui.ui.style.StylesheetManager` |
| Stylesheet | `com.lowdragmc.lowdraglib2.gui.ui.style.Stylesheet` |
| SupplierDataSource | `com.lowdragmc.lowdraglib2.gui.sync.bindings.impl.SupplierDataSource` |
| ProgressBar | `com.lowdragmc.lowdraglib2.gui.ui.elements.ProgressBar` |
| ScrollerView | `com.lowdragmc.lowdraglib2.gui.ui.elements.ScrollerView` |
| Toggle | `com.lowdragmc.lowdraglib2.gui.ui.elements.Toggle` |
| TextField | `com.lowdragmc.lowdraglib2.gui.ui.elements.TextField` |
| UIEvents | `com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents` |

## 1. How LDLib2 UI Works

### The Core Pattern
```java
// 1. Build a tree of UIElements
var root = new UIElement();
root.addChildren(child1, child2, child3);

// 2. Wrap in UI with stylesheets
var ui = UI.of(root, StylesheetManager.INSTANCE.getStylesheetSafe(StylesheetManager.MC));

// 3. Wrap in ModularUI (manages lifecycle, rendering, events)
var mui = ModularUI.of(ui);

// 4. Display it
Minecraft.getInstance().setScreen(new ModularUIScreen(mui, Component.literal("Title")));
```

### Layout: CSS Flexbox via LSS Strings

LDLib2 uses a Taffy/Yoga-based flexbox engine. You set layout via `.lss()` calls with CSS-like property names:

```java
var container = new UIElement();
container.lss("flex-direction", "row");       // row | column
container.lss("justify-content", "center");   // flex-start | center | flex-end | space-between | space-around
container.lss("align-items", "center");       // flex-start | center | flex-end | stretch
container.lss("flex-grow", "1");              // take remaining space
container.lss("width", "100%");               // pixels or percent
container.lss("height", "40");                // pixels
container.lss("padding-all", "4");            // uniform padding
container.lss("gap-all", "2");                // gap between children
```

You can also use the programmatic API:
```java
container.layout(l -> l
    .widthPercent(100)
    .height(40)
    .paddingAll(4)
    .gapAll(2)
    .flexGrow(1)
);
```

### Data Binding: Reactive Labels

Labels can auto-update when game state changes using `SupplierDataSource`:

```java
var lpLabel = new Label();
lpLabel.bindDataSource(SupplierDataSource.of(() ->
    Component.literal("LP: " + state.lp[state.localPlayer])));
// This lambda runs each tick — when LP changes, the label updates automatically
```

### Events: Button Clicks

```java
var btn = new Button();
btn.setText(Component.literal("Attack"));
btn.setOnClick(event -> {
    // Handle click — send a response, update state, etc.
    sendResponse(state, ResponseBuilder.selectCmd(1, 0));
});
```

### Styling: Background, Colors, Tooltips

```java
element.style(s -> s
    .background(new ColorRectTexture(0xFF1A2A1A))  // solid color bg
    .tooltips("This is a tooltip")
);

// Or via LSS:
element.lss("background", "color(#1A2A1A)");
element.lss("tooltips", "This is a tooltip");
```

For textures: `import com.lowdragmc.lowdraglib2.gui.texture.ColorRectTexture`

## 2. Activating the LDLib2 Screen

To switch from the old DuelScreen to the LDLib2 version, edit `ClientPayloadHandler.java`:

```java
// In handleStart():
// OLD:
Minecraft.getInstance().setScreen(new DuelScreen(payload));
// NEW:
LDLibDuelScreen.open(payload);

// In handleMessage():
// OLD:
if (Minecraft.getInstance().screen instanceof DuelScreen screen) {
    screen.applyMessage(payload.message());
}
// NEW:
LDLibDuelScreen.applyMessage(payload.message());

// In handleEnd():
// Keep as-is (just closes the screen)
```

## 3. Building the Duel Field

### Zone Row Pattern

Each row of zones (monster zones, spell/trap zones) follows the same pattern:

```java
private static UIElement createZoneRow(ClientDuelState state, int player, int locationType,
                                        int[] codes, int[] positions, int count) {
    var row = new UIElement();
    row.lss("flex-direction", "row");
    row.lss("justify-content", "center");
    row.lss("gap-all", "2");

    for (int i = 0; i < count; i++) {
        row.addChildren(createZone(state, player, locationType, codes, positions, i));
    }
    return row;
}

private static UIElement createZone(ClientDuelState state, int player, int locationType,
                                     int[] codes, int[] positions, int index) {
    var zone = new UIElement();
    zone.lss("width", "40");
    zone.lss("height", "40");

    // Reactive background color based on card state
    // You'll need to update this when state changes
    int code = codes[index];
    boolean occupied = code != 0;
    boolean faceDown = occupied && (positions[index] & 0xA) != 0;

    if (occupied && !faceDown) {
        zone.style(s -> s.background(new ColorRectTexture(0xFF335577)));
        zone.lss("tooltips", "Card: " + code);
    } else if (faceDown) {
        zone.style(s -> s.background(new ColorRectTexture(0xFF443322)));
    } else {
        zone.style(s -> s.background(new ColorRectTexture(0xFF1A2A1A)));
    }

    // Click handler for card interactions
    zone.addEventListener(UIEvents.CLICK, event -> {
        handleZoneClick(state, player, locationType, index);
    });

    return zone;
}
```

### Hand Display

```java
private static UIElement createHand(ClientDuelState state, int player) {
    var hand = new UIElement();
    hand.lss("flex-direction", "row");
    hand.lss("justify-content", "center");
    hand.lss("gap-all", "2");

    // Rebuild hand when cards change
    // For reactive updates, you might track hand size and rebuild children
    for (int i = 0; i < state.hand[player].size(); i++) {
        int code = state.hand[player].get(i);
        var card = new UIElement();
        card.lss("width", "30");
        card.lss("height", "42");

        if (code != 0) {
            card.style(s -> s.background(new ColorRectTexture(0xFF334455)));
            card.lss("tooltips", "Card: " + code);
        } else {
            card.style(s -> s.background(new ColorRectTexture(0xFF443333)));
        }

        hand.addChildren(card);
    }
    return hand;
}
```

## 4. Dynamic Prompt Rebuilding

The biggest challenge is rebuilding the prompt area when `pendingPrompt` changes. Options:

### Option A: Clear and rebuild children
```java
// When a new prompt arrives:
promptArea.clearChildren();  // if this method exists
// Then add new buttons based on state.pendingPrompt type
switch (state.pendingPrompt) {
    case DuelMessage.SelectYesNo sel -> {
        promptArea.addChildren(yesButton, noButton);
    }
    case DuelMessage.SelectPosition sel -> {
        // Add position buttons
    }
    // etc.
}
```

### Option B: Use visibility toggling
Create all possible prompt UIs upfront, hide/show based on prompt type:
```java
yesNoPanel.lss("display", "none");    // hidden
yesNoPanel.lss("display", "flex");    // visible
```

### Option C: Recreate the entire UI
Simple but heavy — create a fresh `ModularUI` each time the prompt changes and call `setScreen()` again.

## 5. Stylesheet Customization

### Built-in Themes
```java
StylesheetManager.MC       // Minecraft-themed (dark stone textures)
StylesheetManager.GDP      // Clean, modern (default LDLib2 look)
StylesheetManager.MODERN   // Contemporary design
```

### Custom LSS Stylesheet
```java
var customLss = """
    #root {
        padding-all: 8;
        gap-all: 4;
    }
    .zone {
        width: 40;
        height: 40;
        background: color(#1A2A1A);
    }
    .zone:hover {
        background: color(#336633);
    }
    .hand-card {
        width: 30;
        height: 42;
    }
    """;
var stylesheet = Stylesheet.parse(customLss);
var ui = UI.of(root, stylesheet);
```

You can apply classes to elements:
```java
zone.addClass("zone");
card.addClass("hand-card");
```

### Hover States
LSS supports `:hover` pseudoclass automatically:
```java
element.addClass("zone");
// In LSS:
// .zone:hover { background: color(#446688); }
```

## 6. Debugging

Press **F3** while a ModularUI screen is open to see the debug overlay — it shows element bounds, IDs, and layout info in real-time.

## 7. Useful Patterns

### ProgressBar for LP
```java
var lpBar = new ProgressBar();
lpBar.lss("width", "100");
lpBar.lss("height", "10");
lpBar.bindDataSource(SupplierDataSource.of(() ->
    (double) state.lp[state.localPlayer] / 8000.0));
```

### ScrollerView for card lists
```java
var scroller = new ScrollerView();
scroller.lss("height", "200");
scroller.lss("width", "100%");
// Add children that exceed the scroll area height
for (...) {
    scroller.addChildren(cardElement);
}
```

### Grid layout for card selection
```java
var grid = new UIElement();
grid.lss("flex-direction", "row");
grid.lss("flex-wrap", "wrap");
grid.lss("gap-all", "4");
// Children will wrap to next row when they exceed width
```

## 8. Architecture Recommendation

```
LDLibDuelScreen.java     — Static open/applyMessage entry points
DuelUIBuilder.java       — Builds the full UI tree (createUI, all section builders)
DuelFieldRenderer.java   — Zone row/card rendering helpers
DuelPromptBuilder.java   — Dynamic prompt area construction per message type
ClientDuelState.java     — Game state (unchanged, already works)
```

Keep `DuelScreen.java` around as reference until you're confident the LDLib2 version handles all prompt types.

## Next Steps

1. **Switch the payload handler** to use `LDLibDuelScreen.open()` and test that the skeleton loads
2. **Build out the field layout** using the zone row pattern above
3. **Implement dynamic prompts** for each `DuelMessage` selection type
4. **Add an LSS stylesheet** for consistent theming and hover effects
5. **Add card tooltips** with card code/name info
6. **Polish** with LP bars, chain display, win overlay, etc.
