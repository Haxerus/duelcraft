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

public class DuelScreen extends Screen {
    private final ClientDuelState state;

    // ---- Layout regions (recalculated in init) ----

    // Sizing units derived from screen dimensions
    private int zoneW, zoneH, cardW, cardH, gap, btnW, btnH;

    // Vertical region boundaries
    private int headerY;        // top info bar
    private int oppHandY;       // opponent hand region top
    private int oppFieldTop;    // opponent field rows start
    private int localFieldTop;  // local field rows start
    private int promptAreaY;    // prompt button area top
    private int localHandY;     // local hand region top
    private int footerY;        // bottom info bar

    // Horizontal
    private int fieldCenterX;
    private int fieldLeft;      // left edge of the 5-zone row
    private int sidebarLeftX;   // left sidebar (opponent piles)
    private int sidebarRightX;  // right sidebar (local piles)

    public DuelScreen(DuelStartPayload startInfo) {
        super(Component.literal("Duel vs. " + startInfo.opponentName()));
        this.state = new ClientDuelState(startInfo);
    }

    public void applyMessage(DuelMessage msg) {
        state.applyMessage(msg);
        if (state.pendingPrompt != null) {
            rebuildPromptWidgets();
        }
    }

    // ---- Lifecycle ----

    @Override
    protected void init() {
        calculateLayout();
        rebuildPromptWidgets();
    }

    /**
     * Calculate all layout positions based on current screen size.
     * Called from init() (which fires on open and on every resize).
     */
    private void calculateLayout() {
        // Base unit: scale everything from screen height
        // Target: zones ~1/14 of height, cards slightly taller (3:4 ratio)
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

        // Sidebars sit outside the field
        sidebarLeftX = Math.max(4, fieldLeft - 80);
        sidebarRightX = Math.min(width - 80, fieldLeft + fieldRowW + 10);

        // Vertical layout (top to bottom):
        //  [header]  [opp hand]  [opp mzone]  [opp szone]  --gap--  [local szone]  [local mzone]  [prompt area]  [local hand]  [footer]
        int lineH = font.lineHeight + 2;
        headerY = 4;
        oppHandY = headerY + lineH + 2;

        // Field starts below opponent hand area
        oppFieldTop = oppHandY + cardH + gap;
        // Opponent: mzone then szone
        // Local: szone then mzone (mirrored)
        int fieldHeight = 4 * zoneH + 5 * gap; // 4 rows of zones + gaps between them
        localFieldTop = oppFieldTop + 2 * (zoneH + gap) + gap; // after opp's 2 rows + center gap

        // Prompt area below local field
        promptAreaY = localFieldTop + 2 * (zoneH + gap) + gap;

        // Hand at the bottom, footer below it
        localHandY = height - cardH - lineH - 8;
        footerY = height - lineH - 4;

        // Clamp: if prompt area overlaps hand, push hand down (it'll clip but at least prompts are usable)
        if (promptAreaY + btnH * 3 > localHandY) {
            localHandY = promptAreaY + btnH * 3 + 4;
        }
    }

    @Override
    public void tick() {
    }

    @Override
    public void onClose() {
        // Block ESC during active duel
    }

    @Override
    public void removed() {
        super.removed();
    }

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

        // ---- Header ----
        String turnInfo = "Turn " + state.turnCount + " | " + state.phaseName()
                + (state.isLocalTurn() ? " (Your turn)" : " (Opponent's turn)");
        g.drawCenteredString(font, turnInfo, fieldCenterX, headerY, 0xFFCCCCCC);

        // LP displays on either side of the header
        g.drawString(font, state.opponentName + ": " + state.lp[opp] + " LP",
                sidebarLeftX, headerY, 0xFFFF8888, false);
        g.drawString(font, "You: " + state.lp[local] + " LP",
                sidebarRightX, headerY, 0xFF88FF88, false);

        // ---- Opponent hand (face-down) ----
        List<Integer> oppHand = state.hand[opp];
        if (!oppHand.isEmpty()) {
            int oppHandStartX = fieldCenterX - (oppHand.size() * (cardW + gap)) / 2;
            for (int i = 0; i < oppHand.size(); i++) {
                int x = oppHandStartX + i * (cardW + gap);
                g.fill(x, oppHandY, x + cardW, oppHandY + cardH, 0xFF443333);
                g.renderOutline(x, oppHandY, cardW, cardH, 0xFF665555);
            }
        }

        // ---- Opponent field (mzone then szone) ----
        int oppMzoneY = oppFieldTop;
        int oppSzoneY = oppFieldTop + zoneH + gap;
        drawZoneRow(g, state.mzone[opp], state.mzonePos[opp], fieldCenterX, oppMzoneY, 5, mouseX, mouseY, 0xFF1A2A1A);
        drawZoneRow(g, state.szone[opp], state.szonePos[opp], fieldCenterX, oppSzoneY, 5, mouseX, mouseY, 0xFF1A1A2A);

        // ---- Local field (szone then mzone — mirrored) ----
        int localSzoneY = localFieldTop;
        int localMzoneY = localFieldTop + zoneH + gap;
        drawZoneRow(g, state.szone[local], state.szonePos[local], fieldCenterX, localSzoneY, 5, mouseX, mouseY, 0xFF1A1A2A);
        drawZoneRow(g, state.mzone[local], state.mzonePos[local], fieldCenterX, localMzoneY, 5, mouseX, mouseY, 0xFF1A2A1A);

        // ---- Local hand ----
        List<Integer> hand = state.hand[local];
        if (!hand.isEmpty()) {
            int handStartX = fieldCenterX - (hand.size() * (cardW + gap)) / 2;
            for (int i = 0; i < hand.size(); i++) {
                int x = handStartX + i * (cardW + gap);
                int code = hand.get(i);
                boolean hovered = mouseX >= x && mouseX < x + cardW
                        && mouseY >= localHandY && mouseY < localHandY + cardH;

                g.fill(x, localHandY, x + cardW, localHandY + cardH,
                        hovered ? 0xFF556677 : 0xFF334455);
                g.renderOutline(x, localHandY, cardW, cardH, 0xFF888888);

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

        // ---- Pile counts (sidebars) ----
        int pileLineH = font.lineHeight + 1;

        // Opponent piles (left sidebar, below opp hand)
        int oppPileY = oppSzoneY;
        g.drawString(font, "Deck: " + state.deckCount[opp], sidebarLeftX, oppPileY, 0xFF777777, false);
        g.drawString(font, "Extra: " + state.extraCount[opp], sidebarLeftX, oppPileY + pileLineH, 0xFF777777, false);
        g.drawString(font, "Grave: " + state.graveCount[opp], sidebarLeftX, oppPileY + pileLineH * 2, 0xFF777777, false);
        g.drawString(font, "Banish: " + state.banishedCount[opp], sidebarLeftX, oppPileY + pileLineH * 3, 0xFF777777, false);

        // Local piles (right sidebar, next to local field)
        int localPileY = localSzoneY;
        g.drawString(font, "Deck: " + state.deckCount[local], sidebarRightX, localPileY, 0xFFAAAAAA, false);
        g.drawString(font, "Extra: " + state.extraCount[local], sidebarRightX, localPileY + pileLineH, 0xFFAAAAAA, false);
        g.drawString(font, "Grave: " + state.graveCount[local], sidebarRightX, localPileY + pileLineH * 2, 0xFFAAAAAA, false);
        g.drawString(font, "Banish: " + state.banishedCount[local], sidebarRightX, localPileY + pileLineH * 3, 0xFFAAAAAA, false);

        // ---- Chain display ----
        if (!state.chain.isEmpty()) {
            g.drawCenteredString(font, "Chain: " + state.chain.size() + " link(s)",
                    fieldCenterX, localFieldTop - font.lineHeight - 2, 0xFFFF8800);
        }

        // ---- Prompt label ----
        if (state.pendingPrompt != null) {
            String promptName = state.pendingPrompt.getClass().getSimpleName();
            g.drawCenteredString(font, promptName, fieldCenterX, promptAreaY - font.lineHeight - 1, 0xFFFFFF00);
        }

        // ---- Footer (LP bar) ----
        g.drawString(font, "You: " + state.lp[local] + " LP", 4, footerY, 0xFFFFFFFF, false);
        String oppFooter = state.opponentName + ": " + state.lp[opp] + " LP";
        g.drawString(font, oppFooter, width - font.width(oppFooter) - 4, footerY, 0xFFFFFFFF, false);

        // ---- Win overlay ----
        if (state.winner >= 0) {
            // Dim the field
            g.fill(0, 0, width, height, 0x88000000);
            String result = state.winner == state.localPlayer ? "YOU WIN!" : "YOU LOSE";
            g.drawCenteredString(font, result, fieldCenterX, height / 2 - 10, 0xFFFF4444);
            g.drawCenteredString(font, "Press ESC to close", fieldCenterX, height / 2 + 4, 0xFFAAAAAA);
        }
    }

    /**
     * Draw a row of 'count' zones centered at fieldCenterX.
     */
    private void drawZoneRow(GuiGraphics g, int[] codes, int[] positions,
                             int centerX, int y, int count,
                             int mouseX, int mouseY, int emptyColor) {
        int totalW = count * zoneW + (count - 1) * gap;
        int startX = centerX - totalW / 2;
        for (int i = 0; i < count; i++) {
            int x = startX + i * (zoneW + gap);
            int code = codes[i];
            boolean occupied = code != 0;
            boolean faceDown = occupied && (positions[i] & 0xA) != 0;
            boolean hovered = mouseX >= x && mouseX < x + zoneW && mouseY >= y && mouseY < y + zoneH;

            int bgColor;
            if (!occupied) bgColor = emptyColor;
            else if (faceDown) bgColor = 0xFF443322;
            else bgColor = hovered ? 0xFF446688 : 0xFF335577;

            g.fill(x, y, x + zoneW, y + zoneH, bgColor);
            g.renderOutline(x, y, zoneW, zoneH, 0xFF444444);

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

    // ---- Input ----

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean b) {
        if (super.mouseClicked(event, b)) return true;
        // TODO: handle clicks on cards/zones based on pendingPrompt
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

    /**
     * Rebuild the widget list based on the current pending prompt.
     * Buttons are placed in the prompt area between the field and hand.
     */
    private void rebuildPromptWidgets() {
        clearWidgets();

        if (state.pendingPrompt == null) return;

        int btnY = promptAreaY;
        int cx = fieldCenterX;

        switch (state.pendingPrompt) {
            case DuelMessage.SelectEffectYn sel -> {
                addPromptButton("Yes", cx - btnW - 2, btnY,
                        () -> sendResponse(ResponseBuilder.selectYesNo(true)));
                addPromptButton("No", cx + 2, btnY,
                        () -> sendResponse(ResponseBuilder.selectYesNo(false)));
            }
            case DuelMessage.SelectYesNo sel -> {
                addPromptButton("Yes", cx - btnW - 2, btnY,
                        () -> sendResponse(ResponseBuilder.selectYesNo(true)));
                addPromptButton("No", cx + 2, btnY,
                        () -> sendResponse(ResponseBuilder.selectYesNo(false)));
            }
            case DuelMessage.SelectPosition sel -> {
                int count = Integer.bitCount(sel.positions());
                int totalW = count * btnW + (count - 1) * 4;
                int x = cx - totalW / 2;
                if ((sel.positions() & 0x1) != 0) {
                    addPromptButton("ATK", x, btnY,
                            () -> sendResponse(ResponseBuilder.selectPosition(0x1)));
                    x += btnW + 4;
                }
                if ((sel.positions() & 0x4) != 0) {
                    addPromptButton("DEF", x, btnY,
                            () -> sendResponse(ResponseBuilder.selectPosition(0x4)));
                    x += btnW + 4;
                }
                if ((sel.positions() & 0x8) != 0) {
                    addPromptButton("Set", x, btnY,
                            () -> sendResponse(ResponseBuilder.selectPosition(0x8)));
                }
            }
            case DuelMessage.SelectChain sel -> {
                if (sel.count() > 0) {
                    addPromptButton("Activate", cx - btnW - 2, btnY,
                            () -> sendResponse(ResponseBuilder.selectChain(0)));
                }
                if (!sel.forced()) {
                    addPromptButton("Pass", cx + 2, btnY,
                            () -> sendResponse(ResponseBuilder.selectChain(-1)));
                }
            }
            case DuelMessage.SelectOption sel -> {
                int count = sel.options().size();
                int totalW = count * btnW + (count - 1) * 4;
                int x = cx - totalW / 2;
                for (int i = 0; i < count; i++) {
                    final int idx = i;
                    addPromptButton("Option " + (i + 1), x, btnY,
                            () -> sendResponse(ResponseBuilder.selectOption(idx)));
                    x += btnW + 4;
                }
            }
            case DuelMessage.SelectIdleCmd sel -> {
                addPromptButton("Battle Phase", cx - btnW - 2, btnY,
                        () -> sendResponse(ResponseBuilder.selectCmd(6, 0)));
                addPromptButton("End Turn", cx + 2, btnY,
                        () -> sendResponse(ResponseBuilder.selectCmd(7, 0)));
                // TODO: summon/set/activate buttons from rawBody
            }
            case DuelMessage.SelectBattleCmd sel -> {
                addPromptButton("End Battle", cx - btnW / 2, btnY,
                        () -> sendResponse(ResponseBuilder.selectCmd(3, 0)));
                // TODO: attack buttons from rawBody
            }
            case DuelMessage.SelectCard sel -> {
                addPromptButton("Select card(s)", cx - btnW / 2, btnY, () -> {
                    int[] indices = new int[sel.min()];
                    for (int i = 0; i < sel.min(); i++) indices[i] = i;
                    sendResponse(ResponseBuilder.selectCards(indices));
                });
            }
            case DuelMessage.SelectTribute sel -> {
                addPromptButton("Tribute", cx - btnW / 2, btnY, () -> {
                    int[] indices = new int[sel.min()];
                    for (int i = 0; i < sel.min(); i++) indices[i] = i;
                    sendResponse(ResponseBuilder.selectCards(indices));
                });
            }
            default -> {
                addPromptButton("Continue", cx - btnW / 2, btnY,
                        () -> sendResponse(ResponseBuilder.selectCmd(0, 0)));
            }
        }
    }

    private void addPromptButton(String label, int x, int y, Runnable action) {
        addRenderableWidget(Button.builder(Component.literal(label), btn -> action.run())
                .bounds(x, y, btnW, btnH).build());
    }

    // ---- Response sending ----

    private void sendResponse(byte[] response) {
        ClientPacketDistributor.sendToServer(new DuelResponsePayload(response));
        state.pendingPrompt = null;
        rebuildPromptWidgets();
    }
}
