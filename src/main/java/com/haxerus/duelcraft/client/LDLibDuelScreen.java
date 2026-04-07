package com.haxerus.duelcraft.client;

import com.haxerus.duelcraft.duel.message.DuelMessage;
import com.haxerus.duelcraft.duel.response.ResponseBuilder;
import com.haxerus.duelcraft.server.DuelResponsePayload;
import com.haxerus.duelcraft.server.DuelStartPayload;
import com.lowdragmc.lowdraglib2.gui.holder.ModularUIScreen;
import com.lowdragmc.lowdraglib2.gui.sync.bindings.impl.SupplierDataSource;
import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import com.lowdragmc.lowdraglib2.gui.ui.UI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;
import com.lowdragmc.lowdraglib2.gui.ui.style.Stylesheet;
import com.lowdragmc.lowdraglib2.gui.ui.style.StylesheetManager;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * LDLib2-based duel screen skeleton.
 * <p>
 * Demonstrates:
 * - Building a UI tree with UIElement, Label, Button
 * - Flexbox layout via LSS (CSS-like) strings
 * - Reactive labels via SupplierDataSource
 * - Button click handlers that send duel responses
 * - Applying a built-in stylesheet theme
 * <p>
 * The old DuelScreen.java still exists for reference. Once you're ready,
 * switch ClientPayloadHandler to call LDLibDuelScreen.open() instead.
 */
public class LDLibDuelScreen {

    /** The screen instance, so ClientPayloadHandler can route messages to it. */
    private static ClientDuelState activeState;

    /**
     * Creates and opens the LDLib2 duel screen.
     * Call from ClientPayloadHandler.handleStart() instead of new DuelScreen().
     */
    public static void open(DuelStartPayload startInfo) {
        activeState = new ClientDuelState(startInfo);
        var ui = createUI(activeState);
        var screen = new ModularUIScreen(ui, Component.literal("Duel vs. " + startInfo.opponentName()));
        Minecraft.getInstance().setScreen(screen);
    }

    /** Apply a message to the active state (called from ClientPayloadHandler). */
    public static void applyMessage(DuelMessage msg) {
        if (activeState != null) {
            activeState.applyMessage(msg);
            // SupplierDataSource labels auto-update on next tick — no manual refresh needed
        }
    }

    // ─── UI Construction ──────────────────────────────────────

    private static ModularUI createUI(ClientDuelState state) {
        // Root: vertical column filling the screen
        var root = new UIElement();
        root.setId("root");
        root.lss("flex-direction", "column");
        root.lss("width", "100%");
        root.lss("height", "100%");
        root.lss("padding-all", "8");
        root.lss("gap-all", "4");

        root.addChildren(
                createHeader(state),
                createFieldArea(state),
                createPromptArea(state),
                createFooter(state)
        );

        // Apply built-in MC theme stylesheet
        var stylesheet = StylesheetManager.INSTANCE.getStylesheetSafe(StylesheetManager.MC);
        return ModularUI.of(UI.of(root, stylesheet));
    }

    // ─── Header: opponent LP | turn info | your LP ────────────

    private static UIElement createHeader(ClientDuelState state) {
        var header = new UIElement();
        header.setId("header");
        header.lss("flex-direction", "row");
        header.lss("justify-content", "space-between");
        header.lss("width", "100%");

        var oppLabel = new Label();
        oppLabel.bindDataSource(SupplierDataSource.of(() ->
                Component.literal(state.opponentName + ": " + state.lp[state.opponent()] + " LP")));

        var turnLabel = new Label();
        turnLabel.bindDataSource(SupplierDataSource.of(() ->
                Component.literal("Turn " + state.turnCount + " | " + state.phaseName()
                        + (state.isLocalTurn() ? " (Your turn)" : ""))));

        var yourLabel = new Label();
        yourLabel.bindDataSource(SupplierDataSource.of(() ->
                Component.literal("You: " + state.lp[state.localPlayer] + " LP")));

        header.addChildren(oppLabel, turnLabel, yourLabel);
        return header;
    }

    // ─── Field area (placeholder for board layout) ────────────

    private static UIElement createFieldArea(ClientDuelState state) {
        var field = new UIElement();
        field.setId("field-area");
        field.lss("flex-direction", "column");
        field.lss("flex-grow", "1");
        field.lss("gap-all", "2");

        // TODO: Build the actual duel field here.
        // This is where you'll add zone rows for:
        //   - Opponent monster zones (5 main + 2 EMZ)
        //   - Opponent spell/trap zones
        //   - Your spell/trap zones
        //   - Your monster zones
        //   - Hands, pile counts, chain display, etc.
        //
        // See the implementation guide for patterns on building zone rows.

        var placeholder = new Label();
        placeholder.setValue(Component.literal("[Duel field — see implementation guide]"));
        placeholder.lss("horizontal-align", "center");

        field.addChildren(placeholder);
        return field;
    }

    // ─── Prompt area: dynamic buttons based on pending prompt ─

    private static UIElement createPromptArea(ClientDuelState state) {
        var area = new UIElement();
        area.setId("prompt-area");
        area.lss("flex-direction", "row");
        area.lss("justify-content", "center");
        area.lss("gap-all", "8");
        area.lss("width", "100%");
        area.lss("height", "30");

        // TODO: Dynamically rebuild buttons when state.pendingPrompt changes.
        // For now, static Yes/No as a starting example.
        var yesBtn = new Button();
        yesBtn.setText(Component.literal("Yes"));
        yesBtn.setOnClick(event -> sendResponse(state, ResponseBuilder.selectYesNo(true)));

        var noBtn = new Button();
        noBtn.setText(Component.literal("No"));
        noBtn.setOnClick(event -> sendResponse(state, ResponseBuilder.selectYesNo(false)));

        area.addChildren(yesBtn, noBtn);
        return area;
    }

    // ─── Footer: LP summary ───────────────────────────────────

    private static UIElement createFooter(ClientDuelState state) {
        var footer = new UIElement();
        footer.setId("footer");
        footer.lss("flex-direction", "row");
        footer.lss("justify-content", "space-between");
        footer.lss("width", "100%");

        var leftLabel = new Label();
        leftLabel.bindDataSource(SupplierDataSource.of(() ->
                Component.literal("You: " + state.lp[state.localPlayer] + " LP")));

        var rightLabel = new Label();
        rightLabel.bindDataSource(SupplierDataSource.of(() ->
                Component.literal(state.opponentName + ": " + state.lp[state.opponent()] + " LP")));

        footer.addChildren(leftLabel, rightLabel);
        return footer;
    }

    // ─── Helpers ──────────────────────────────────────────────

    private static void sendResponse(ClientDuelState state, byte[] response) {
        PacketDistributor.sendToServer(new DuelResponsePayload(response));
        state.pendingPrompt = null;
        state.clearCardActions();
    }
}
