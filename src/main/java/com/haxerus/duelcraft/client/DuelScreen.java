package com.haxerus.duelcraft.client;

import com.haxerus.duelcraft.duel.message.DuelMessage;
import com.haxerus.duelcraft.duel.response.ResponseBuilder;
import com.haxerus.duelcraft.server.DuelResponsePayload;
import com.haxerus.duelcraft.server.DuelStartPayload;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

import java.util.List;

import static com.haxerus.duelcraft.core.OcgConstants.*;

public class DuelScreen extends Screen {
    private final ClientDuelState state;

    // ---- Layout regions ----
    private int zoneW, zoneH, cardW, cardH, gap, btnW, btnH;
    private int headerY, footerY;
    private int oppHandY, localHandY;
    private int oppMzoneY, oppSzoneY, localSzoneY, localMzoneY;
    private int promptAreaY;
    private int fieldCenterX, fieldLeft;
    private int sidebarLeftX, sidebarRightX;

    // ---- Context menu state ----
    private boolean contextMenuOpen;
    private int contextMenuX, contextMenuY;           // click position
    private int contextMenuRenderX, contextMenuRenderY; // clamped render position
    private List<ClientDuelState.CardAction> contextMenuActions;

    public DuelScreen(DuelStartPayload startInfo) {
        super(Component.literal("Duel vs. " + startInfo.opponentName()));
        this.state = new ClientDuelState(startInfo);
    }

    public void applyMessage(DuelMessage msg) {
        state.applyMessage(msg);
        if (state.pendingPrompt != null) {
            contextMenuOpen = false;
            rebuildPromptWidgets();
        }
    }

    // ---- Lifecycle ----

    @Override
    protected void init() {
        calculateLayout();
        rebuildPromptWidgets();
    }

    private void calculateLayout() {
        zoneW = Math.max(20, height / 14);
        zoneH = zoneW;
        cardW = Math.max(18, (int)(zoneW * 0.85));
        cardH = Math.max(24, (int)(cardW * 1.4));
        gap = Math.max(2, zoneW / 8);
        btnW = Math.max(60, width / 8);
        btnH = 20;

        fieldCenterX = width / 2;
        int fieldRowW = 5 * zoneW + 4 * gap;
        fieldLeft = fieldCenterX - fieldRowW / 2;

        sidebarLeftX = Math.max(4, fieldLeft - 80);
        sidebarRightX = Math.min(width - 80, fieldLeft + fieldRowW + 10);

        int lineH = font.lineHeight + 2;
        headerY = 4;
        oppHandY = headerY + lineH + 2;

        oppMzoneY = oppHandY + cardH + gap;
        oppSzoneY = oppMzoneY + zoneH + gap;
        localSzoneY = oppSzoneY + zoneH + gap + gap; // center gap
        localMzoneY = localSzoneY + zoneH + gap;

        promptAreaY = localMzoneY + zoneH + gap + gap;
        localHandY = height - cardH - lineH - 8;
        footerY = height - lineH - 4;

        if (promptAreaY + btnH * 3 > localHandY) {
            localHandY = promptAreaY + btnH * 3 + 4;
        }
    }

    @Override
    public void tick() {}

    @Override
    public void onClose() {}

    @Override
    public void removed() { super.removed(); }

    // ---- Rendering ----

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        this.renderTransparentBackground(g);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);

        int local = state.localPlayer;
        int opp = state.opponent();

        // Header
        String turnInfo = "Turn " + state.turnCount + " | " + state.phaseName()
                + (state.isLocalTurn() ? " (Your turn)" : " (Opponent's turn)");
        g.drawCenteredString(font, turnInfo, fieldCenterX, headerY, 0xFFCCCCCC);
        g.drawString(font, state.opponentName + ": " + state.lp[opp] + " LP",
                sidebarLeftX, headerY, 0xFFFF8888, false);
        g.drawString(font, "You: " + state.lp[local] + " LP",
                sidebarRightX, headerY, 0xFF88FF88, false);

        // Opponent hand
        List<Integer> oppHand = state.hand[opp];
        if (!oppHand.isEmpty()) {
            int startX = fieldCenterX - (oppHand.size() * (cardW + gap)) / 2;
            for (int i = 0; i < oppHand.size(); i++) {
                int x = startX + i * (cardW + gap);
                g.fill(x, oppHandY, x + cardW, oppHandY + cardH, 0xFF443333);
                g.renderOutline(x, oppHandY, cardW, cardH, 0xFF665555);
            }
        }

        // Field zones
        drawZoneRow(g, opp, LOCATION_MZONE, state.mzone[opp], state.mzonePos[opp],
                oppMzoneY, 5, mouseX, mouseY, 0xFF1A2A1A);
        drawZoneRow(g, opp, LOCATION_SZONE, state.szone[opp], state.szonePos[opp],
                oppSzoneY, 5, mouseX, mouseY, 0xFF1A1A2A);
        drawZoneRow(g, local, LOCATION_MZONE, state.mzone[local], state.mzonePos[local],
                localSzoneY, 5, mouseX, mouseY, 0xFF1A2A1A);
        drawZoneRow(g, local, LOCATION_SZONE, state.szone[local], state.szonePos[local],
                localMzoneY, 5, mouseX, mouseY, 0xFF1A1A2A);

        // Local hand
        List<Integer> hand = state.hand[local];
        if (!hand.isEmpty()) {
            int startX = fieldCenterX - (hand.size() * (cardW + gap)) / 2;
            for (int i = 0; i < hand.size(); i++) {
                int x = startX + i * (cardW + gap);
                int code = hand.get(i);
                boolean hovered = mouseX >= x && mouseX < x + cardW
                        && mouseY >= localHandY && mouseY < localHandY + cardH;
                boolean hasActions = !state.cardActions.isEmpty() &&
                        state.cardActions.containsKey(
                                new ClientDuelState.CardLocation(local, LOCATION_HAND, i));

                int bg = hovered ? 0xFF556677 : (hasActions ? 0xFF3A5555 : 0xFF334455);
                g.fill(x, localHandY, x + cardW, localHandY + cardH, bg);
                g.renderOutline(x, localHandY, cardW, cardH,
                        hasActions ? 0xFF44FF44 : 0xFF888888);

                String label = String.valueOf(code);
                if (font.width(label) > cardW - 4) {
                    label = label.substring(0, Math.min(label.length(), 4)) + "..";
                }
                g.drawString(font, label, x + 2, localHandY + 2, 0xFFFFFFFF, false);

                if (hovered) {
                    g.setTooltipForNextFrame(font,
                            List.of(Component.literal("Card: " + code).getVisualOrderText()),
                            mouseX, mouseY);
                }
            }
        }

        // Pile counts
        int pileLineH = font.lineHeight + 1;
        int oppPileY = oppSzoneY;
        g.drawString(font, "Deck: " + state.deckCount[opp], sidebarLeftX, oppPileY, 0xFF777777, false);
        g.drawString(font, "Extra: " + state.extraCount[opp], sidebarLeftX, oppPileY + pileLineH, 0xFF777777, false);
        g.drawString(font, "Grave: " + state.graveCount[opp], sidebarLeftX, oppPileY + pileLineH * 2, 0xFF777777, false);
        g.drawString(font, "Banish: " + state.banishedCount[opp], sidebarLeftX, oppPileY + pileLineH * 3, 0xFF777777, false);

        int localPileY = localSzoneY;
        g.drawString(font, "Deck: " + state.deckCount[local], sidebarRightX, localPileY, 0xFFAAAAAA, false);
        g.drawString(font, "Extra: " + state.extraCount[local], sidebarRightX, localPileY + pileLineH, 0xFFAAAAAA, false);
        g.drawString(font, "Grave: " + state.graveCount[local], sidebarRightX, localPileY + pileLineH * 2, 0xFFAAAAAA, false);
        g.drawString(font, "Banish: " + state.banishedCount[local], sidebarRightX, localPileY + pileLineH * 3, 0xFFAAAAAA, false);

        // Chain
        if (!state.chain.isEmpty()) {
            g.drawCenteredString(font, "Chain: " + state.chain.size() + " link(s)",
                    fieldCenterX, localSzoneY - font.lineHeight - 2, 0xFFFF8800);
        }

        // Prompt label
        if (state.pendingPrompt != null && !contextMenuOpen) {
            String promptName = state.pendingPrompt.getClass().getSimpleName();
            g.drawCenteredString(font, promptName, fieldCenterX, promptAreaY - font.lineHeight - 1, 0xFFFFFF00);
        }

        // Context menu (drawn on top of everything)
        if (contextMenuOpen && contextMenuActions != null) {
            int itemSize = 18;
            int menuGap = 2;
            int menuW = contextMenuActions.size() * (itemSize + menuGap) - menuGap + 6;
            int menuH = itemSize + 6;
            int mx = contextMenuX - menuW / 2;
            int my = contextMenuY - menuH - 4;
            // Clamp to screen
            mx = Math.max(2, Math.min(mx, width - menuW - 2));
            my = Math.max(2, Math.min(my, height - menuH - 2));
            contextMenuRenderX = mx;
            contextMenuRenderY = my;

            // Background
            g.fill(mx, my, mx + menuW, my + menuH, 0xEE1A1A1A);
            g.renderOutline(mx, my, menuW, menuH, 0xFF888888);

            // Items as icon squares
            int ix = mx + 3;
            int iy = my + 3;
            for (var action : contextMenuActions) {
                boolean hovered = mouseX >= ix && mouseX < ix + itemSize
                        && mouseY >= iy && mouseY < iy + itemSize;
                int bgColor = hovered ? 0xFF445566 : 0xFF2A2A3A;
                g.fill(ix, iy, ix + itemSize, iy + itemSize, bgColor);
                g.renderOutline(ix, iy, itemSize, itemSize, actionColor(action.actionType()));

                // Single letter icon
                String icon = actionIcon(action.actionType());
                int textX = ix + (itemSize - font.width(icon)) / 2;
                int textY = iy + (itemSize - font.lineHeight) / 2;
                g.drawString(font, icon, textX, textY, actionColor(action.actionType()), false);

                // Tooltip
                if (hovered) {
                    g.setTooltipForNextFrame(font,
                            List.of(Component.literal(action.label()).getVisualOrderText()),
                            mouseX, mouseY);
                }

                ix += itemSize + menuGap;
            }
        }

        // Footer
        g.drawString(font, "You: " + state.lp[local] + " LP", 4, footerY, 0xFFFFFFFF, false);
        String oppFooter = state.opponentName + ": " + state.lp[opp] + " LP";
        g.drawString(font, oppFooter, width - font.width(oppFooter) - 4, footerY, 0xFFFFFFFF, false);

        // Win overlay
        if (state.winner >= 0) {
            g.fill(0, 0, width, height, 0x88000000);
            String result = state.winner == state.localPlayer ? "YOU WIN!" : "YOU LOSE";
            g.drawCenteredString(font, result, fieldCenterX, height / 2 - 10, 0xFFFF4444);
            g.drawCenteredString(font, "Press ESC to close", fieldCenterX, height / 2 + 4, 0xFFAAAAAA);
        }
    }

    private void drawZoneRow(GuiGraphics g, int player, int locationType,
                             int[] codes, int[] positions,
                             int y, int count, int mouseX, int mouseY, int emptyColor) {
        int totalW = count * zoneW + (count - 1) * gap;
        int startX = fieldCenterX - totalW / 2;
        for (int i = 0; i < count; i++) {
            int x = startX + i * (zoneW + gap);
            int code = codes[i];
            boolean occupied = code != 0;
            boolean faceDown = occupied && (positions[i] & 0xA) != 0;
            boolean hovered = mouseX >= x && mouseX < x + zoneW && mouseY >= y && mouseY < y + zoneH;

            boolean hasActions = !state.cardActions.isEmpty() &&
                    state.cardActions.containsKey(
                            new ClientDuelState.CardLocation(player, locationType, i));

            // SelectPlace highlight
            boolean placeSelectable = false;
            if (state.pendingPrompt instanceof DuelMessage.SelectPlace sel) {
                int bit = zoneToBit(player, sel.player(), locationType, i);
                if (bit >= 0 && (sel.field() & (1 << bit)) == 0) {
                    placeSelectable = true;
                }
            }

            int bgColor;
            if (placeSelectable) bgColor = hovered ? 0xFF336633 : 0xFF224422;
            else if (!occupied) bgColor = emptyColor;
            else if (faceDown) bgColor = 0xFF443322;
            else bgColor = hovered ? 0xFF446688 : 0xFF335577;

            g.fill(x, y, x + zoneW, y + zoneH, bgColor);
            int outlineColor = hasActions ? 0xFF44FF44 : (placeSelectable ? 0xFF44AA44 : 0xFF444444);
            g.renderOutline(x, y, zoneW, zoneH, outlineColor);

            if (occupied && !faceDown) {
                String label = String.valueOf(code);
                if (font.width(label) > zoneW - 4) {
                    label = label.substring(0, Math.min(label.length(), 4));
                }
                g.drawString(font, label, x + 2, y + 2, 0xFFFFFFFF, false);
            }

            if (hovered && occupied) {
                String tip = "Card: " + code + (faceDown ? " (face-down)" : "");
                g.setTooltipForNextFrame(font,
                        List.of(Component.literal(tip).getVisualOrderText()),
                        mouseX, mouseY);
            }
        }
    }

    private static int zoneToBit(int player, int promptPlayer, int location, int sequence) {
        int offset = (player != promptPlayer) ? 16 : 0;
        if (location == LOCATION_MZONE) {
            if (sequence >= 0 && sequence < 7) return offset + sequence;
        } else if (location == LOCATION_SZONE) {
            if (sequence >= 0 && sequence < 5) return offset + 8 + sequence;
            if (sequence == 5) return offset + 13;
        }
        return -1;
    }

    // ---- Hit testing ----

    record ClickTarget(int controller, int location, int sequence) {}

    private ClickTarget hitTest(double mx, double my) {
        int local = state.localPlayer;
        int opp = state.opponent();

        // Local hand
        List<Integer> hand = state.hand[local];
        if (!hand.isEmpty()) {
            int startX = fieldCenterX - (hand.size() * (cardW + gap)) / 2;
            for (int i = 0; i < hand.size(); i++) {
                int x = startX + i * (cardW + gap);
                if (mx >= x && mx < x + cardW && my >= localHandY && my < localHandY + cardH) {
                    return new ClickTarget(local, LOCATION_HAND, i);
                }
            }
        }

        // Local monster zones (rendered at localSzoneY — upper row)
        ClickTarget t = hitTestRow(mx, my, local, LOCATION_MZONE, localSzoneY, 5);
        if (t != null) return t;

        // Local spell zones (rendered at localMzoneY — lower row)
        t = hitTestRow(mx, my, local, LOCATION_SZONE, localMzoneY, 5);
        if (t != null) return t;

        // Opponent monster zones
        t = hitTestRow(mx, my, opp, LOCATION_MZONE, oppMzoneY, 5);
        if (t != null) return t;

        // Opponent spell zones
        t = hitTestRow(mx, my, opp, LOCATION_SZONE, oppSzoneY, 5);
        if (t != null) return t;

        return null;
    }

    private ClickTarget hitTestRow(double mx, double my, int controller, int location, int y, int count) {
        if (my < y || my >= y + zoneH) return null;
        int totalW = count * zoneW + (count - 1) * gap;
        int startX = fieldCenterX - totalW / 2;
        for (int i = 0; i < count; i++) {
            int x = startX + i * (zoneW + gap);
            if (mx >= x && mx < x + zoneW) {
                return new ClickTarget(controller, location, i);
            }
        }
        return null;
    }

    // ---- Input ----

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean b) {
        if (super.mouseClicked(event, b)) return true;

        double mx = event.x();
        double my = event.y();

        // Handle context menu clicks
        if (contextMenuOpen && contextMenuActions != null) {
            int itemSize = 18;
            int menuGap = 2;
            int ix = contextMenuRenderX + 3;
            int iy = contextMenuRenderY + 3;
            for (var action : contextMenuActions) {
                if (mx >= ix && mx < ix + itemSize && my >= iy && my < iy + itemSize) {
                    contextMenuOpen = false;
                    sendResponse(ResponseBuilder.selectCmd(action.actionType(), action.listIndex()));
                    return true;
                }
                ix += itemSize + menuGap;
            }
            // Click outside menu — close it
            contextMenuOpen = false;
            rebuildPromptWidgets();
            return true;
        }

        if (state.pendingPrompt == null) return false;

        ClickTarget target = hitTest(mx, my);
        if (target == null) return false;

        switch (state.pendingPrompt) {
            case DuelMessage.SelectIdleCmd sel -> {
                return handleCardClick(target, mx, my);
            }
            case DuelMessage.SelectBattleCmd sel -> {
                return handleCardClick(target, mx, my);
            }
            case DuelMessage.SelectPlace sel -> {
                int bit = zoneToBit(target.controller(), sel.player(),
                        target.location(), target.sequence());
                if (bit >= 0 && (sel.field() & (1 << bit)) == 0) {
                    sendResponse(ResponseBuilder.selectPlace(
                            target.controller(), target.location(), target.sequence()));
                    return true;
                }
            }
            case DuelMessage.SelectCard sel -> {
                for (int i = 0; i < sel.cards().size(); i++) {
                    var c = sel.cards().get(i);
                    if (c.controller() == target.controller()
                            && c.location() == target.location()
                            && c.sequence() == target.sequence()) {
                        sendResponse(ResponseBuilder.selectCards(i));
                        return true;
                    }
                }
            }
            case DuelMessage.SelectTribute sel -> {
                for (int i = 0; i < sel.cards().size(); i++) {
                    var c = sel.cards().get(i);
                    if (c.controller() == target.controller()
                            && c.location() == target.location()
                            && c.sequence() == target.sequence()) {
                        sendResponse(ResponseBuilder.selectCards(i));
                        return true;
                    }
                }
            }
            default -> {}
        }
        return false;
    }

    private boolean handleCardClick(ClickTarget target, double mx, double my) {
        var key = new ClientDuelState.CardLocation(
                target.controller(), target.location(), target.sequence());
        var actions = state.cardActions.get(key);
        if (actions != null && !actions.isEmpty()) {
            if (actions.size() == 1) {
                sendResponse(ResponseBuilder.selectCmd(
                        actions.getFirst().actionType(), actions.getFirst().listIndex()));
            } else {
                contextMenuOpen = true;
                contextMenuX = (int) mx;
                contextMenuY = (int) my;
                contextMenuActions = actions;
                rebuildPromptWidgets();
            }
            return true;
        }
        return false;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.key() == 256 && state.winner >= 0) {
            this.minecraft.setScreen(null);
            return true;
        }
        if (event.key() == 256) return true;
        return super.keyPressed(event);
    }

    // ---- Prompt UI ----

    private void rebuildPromptWidgets() {
        clearWidgets();

        if (state.pendingPrompt == null) return;

        int y = promptAreaY;
        int cx = fieldCenterX;

        // Context menu is custom-rendered in render(), not as widgets
        if (contextMenuOpen) return;

        switch (state.pendingPrompt) {
            case DuelMessage.SelectIdleCmd sel -> {
                // Only phase buttons — card actions are via click
                int phaseX = cx - btnW - 2;
                if (sel.canBattle()) {
                    addPromptButton("Battle Phase", phaseX, y,
                            () -> sendResponse(ResponseBuilder.selectCmd(6, 0)));
                    phaseX += btnW + 4;
                }
                if (sel.canEnd()) {
                    addPromptButton("End Turn", phaseX, y,
                            () -> sendResponse(ResponseBuilder.selectCmd(7, 0)));
                }
            }
            case DuelMessage.SelectBattleCmd sel -> {
                // Battle cmd actions: 0=activate, 1=attack, 2=main phase 2, 3=end phase
                int phaseX = cx - btnW - 2;
                if (sel.canMain2()) {
                    addPromptButton("Main Phase 2", phaseX, y,
                            () -> sendResponse(ResponseBuilder.selectCmd(2, 0)));
                    phaseX += btnW + 4;
                }
                if (sel.canEnd()) {
                    addPromptButton("End Battle", phaseX, y,
                            () -> sendResponse(ResponseBuilder.selectCmd(3, 0)));
                }
            }
            case DuelMessage.SelectEffectYn sel -> {
                addPromptButton("Yes", cx - btnW - 2, y,
                        () -> sendResponse(ResponseBuilder.selectYesNo(true)));
                addPromptButton("No", cx + 2, y,
                        () -> sendResponse(ResponseBuilder.selectYesNo(false)));
            }
            case DuelMessage.SelectYesNo sel -> {
                addPromptButton("Yes", cx - btnW - 2, y,
                        () -> sendResponse(ResponseBuilder.selectYesNo(true)));
                addPromptButton("No", cx + 2, y,
                        () -> sendResponse(ResponseBuilder.selectYesNo(false)));
            }
            case DuelMessage.SelectPosition sel -> {
                int x = cx - (Integer.bitCount(sel.positions()) * (btnW + 4)) / 2;
                if ((sel.positions() & 0x1) != 0) {
                    addPromptButton("ATK", x, y,
                            () -> sendResponse(ResponseBuilder.selectPosition(0x1)));
                    x += btnW + 4;
                }
                if ((sel.positions() & 0x4) != 0) {
                    addPromptButton("DEF", x, y,
                            () -> sendResponse(ResponseBuilder.selectPosition(0x4)));
                    x += btnW + 4;
                }
                if ((sel.positions() & 0x8) != 0) {
                    addPromptButton("Set", x, y,
                            () -> sendResponse(ResponseBuilder.selectPosition(0x8)));
                }
            }
            case DuelMessage.SelectChain sel -> {
                for (int i = 0; i < sel.chains().size(); i++) {
                    final int idx = i;
                    var c = sel.chains().get(i);
                    addPromptButton("Chain " + c.code(), cx - btnW * 2, y + i * (btnH + 2),
                            () -> sendResponse(ResponseBuilder.selectChain(idx)));
                }
                if (!sel.forced()) {
                    addPromptButton("Pass", cx + btnW, y,
                            () -> sendResponse(ResponseBuilder.selectChain(-1)));
                }
            }
            case DuelMessage.SelectOption sel -> {
                for (int i = 0; i < sel.options().size(); i++) {
                    final int idx = i;
                    addPromptButton("Option " + (i + 1),
                            cx - btnW / 2, y + i * (btnH + 2),
                            () -> sendResponse(ResponseBuilder.selectOption(idx)));
                }
            }
            case DuelMessage.SelectCard sel -> {
                // Show cards as buttons for cards not on the visible field (graveyard, deck, etc.)
                // Field cards can be clicked directly
                int col = 0;
                int row = 0;
                for (int i = 0; i < sel.cards().size(); i++) {
                    final int idx = i;
                    var c = sel.cards().get(i);
                    addPromptButton("" + c.code(),
                            cx - btnW * 3 / 2 + col * (btnW + 4), y + row * (btnH + 2),
                            () -> sendResponse(ResponseBuilder.selectCards(idx)));
                    col++;
                    if (col >= 3) { col = 0; row++; }
                }
            }
            case DuelMessage.SelectTribute sel -> {
                int col = 0;
                int row = 0;
                for (int i = 0; i < sel.cards().size(); i++) {
                    final int idx = i;
                    var c = sel.cards().get(i);
                    addPromptButton("Tribute " + c.code(),
                            cx - btnW * 3 / 2 + col * (btnW + 4), y + row * (btnH + 2),
                            () -> sendResponse(ResponseBuilder.selectCards(idx)));
                    col++;
                    if (col >= 3) { col = 0; row++; }
                }
            }
            case DuelMessage.SelectPlace sel -> {
                // Zones are clickable directly — highlighted in render()
            }
            case DuelMessage.Retry ignored -> {}
            default -> {
                addPromptButton("Continue", cx - btnW / 2, y,
                        () -> sendResponse(ResponseBuilder.selectCmd(0, 0)));
            }
        }
    }

    /** Single-letter icon for each action type. */
    private static String actionIcon(int actionType) {
        return switch (actionType) {
            case 0 -> "S";  // Summon
            case 1 -> "P";  // Sp. Summon (idle) / Attack (battle)
            case 2 -> "R";  // Reposition (idle) / Activate (battle)
            case 3 -> "M";  // Set Monster
            case 4 -> "T";  // Set Spell/Trap
            case 5 -> "A";  // Activate
            default -> "?";
        };
    }

    /** Color for each action type. */
    private static int actionColor(int actionType) {
        return switch (actionType) {
            case 0 -> 0xFF44CC44;  // Summon: green
            case 1 -> 0xFFFF6644;  // Sp. Summon / Attack: orange-red
            case 2 -> 0xFF44AAFF;  // Reposition / Activate (battle): blue
            case 3 -> 0xFFAAAA44;  // Set Monster: yellow
            case 4 -> 0xFFAA44AA;  // Set S/T: purple
            case 5 -> 0xFF44DDDD;  // Activate: cyan
            default -> 0xFFAAAAAA;
        };
    }

    private void addPromptButton(String label, int x, int y, Runnable action) {
        addRenderableWidget(Button.builder(Component.literal(label), btn -> action.run())
                .bounds(x, y, btnW, btnH).build());
    }

    private void sendResponse(byte[] response) {
        ClientPacketDistributor.sendToServer(new DuelResponsePayload(response));
        state.pendingPrompt = null;
        state.clearCardActions();
        contextMenuOpen = false;
        rebuildPromptWidgets();
    }
}
