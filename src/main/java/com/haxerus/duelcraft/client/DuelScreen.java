package com.haxerus.duelcraft.client;

import com.haxerus.duelcraft.duel.message.DuelMessage;
import com.haxerus.duelcraft.duel.message.MessageParser;
import com.haxerus.duelcraft.duel.message.ParsedEntry;
import com.haxerus.duelcraft.duel.response.ResponseBuilder;
import com.haxerus.duelcraft.server.DuelMessagePayload;
import com.haxerus.duelcraft.server.DuelResponsePayload;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

import java.util.List;

public class DuelScreen extends Screen {
    private final ClientDuelState state;

    public DuelScreen(int localPlayer, String opponentName) {
        super(Component.literal("Duel vs. " + opponentName));
        this.state = new ClientDuelState(localPlayer, opponentName);
    }

    public void applyMessage(DuelMessagePayload payload) {
        List<ParsedEntry> entries = MessageParser.parse(payload.data());
        for (ParsedEntry entry : entries) {
            state.applyMessage(entry.message());
        }
        // Rebuild prompt widgets when a new prompt arrives
        if (state.pendingPrompt != null) {
            rebuildPromptWidgets();
        }
    }

    // ---- Lifecycle ----

    @Override
    protected void init() {
        rebuildPromptWidgets();
    }

    @Override
    public void tick() {
        // Per-frame client logic (animations, timers) can go here
    }

    @Override
    public void onClose() {
        // Don't allow ESC to close during an active duel
        // TODO: show a forfeit confirmation dialog instead
    }

    @Override
    public void removed() {
        // Cleanup when the screen is actually removed (e.g., duel ended)
        super.removed();
    }

    // ---- Rendering ----

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderTransparentBackground(graphics);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick); // renders background + widgets

        int local = state.localPlayer;
        int opp = state.opponent();

        // ---- Header: LP and turn info ----
        String localLabel = "You: " + state.lp[local] + " LP";
        String oppLabel = state.opponentName + ": " + state.lp[opp] + " LP";
        graphics.drawString(font, localLabel, 10, height - 20, 0xFFFFFFFF);
        graphics.drawString(font, oppLabel, 10, 10, 0xFFFFFFFF);

        String turnInfo = "Turn " + state.turnCount + " | " + state.phaseName()
                + (state.isLocalTurn() ? " (Your turn)" : " (Opponent's turn)");
        graphics.drawCenteredString(font, turnInfo, width / 2, 10, 0xFFCCCCCC);

        // ---- Monster Zones ----
        int zoneW = 36, zoneH = 36, gap = 4;
        int fieldCenterX = width / 2;

        // Opponent monster zones (top)
        int oppMzoneY = 40;
        drawZoneRow(graphics, opp, state.mzone[opp], state.mzonePos[opp],
                fieldCenterX, oppMzoneY, zoneW, zoneH, gap, 5, mouseX, mouseY);

        // Local monster zones (bottom)
        int localMzoneY = oppMzoneY + zoneH + gap + zoneH + gap; // skip S/T row
        drawZoneRow(graphics, local, state.mzone[local], state.mzonePos[local],
                fieldCenterX, localMzoneY, zoneW, zoneH, gap, 5, mouseX, mouseY);

        // ---- Spell/Trap Zones ----
        int oppSzoneY = oppMzoneY + zoneH + gap;
        drawZoneRow(graphics, opp, state.szone[opp], state.szonePos[opp],
                fieldCenterX, oppSzoneY, zoneW, zoneH, gap, 5, mouseX, mouseY);

        int localSzoneY = localMzoneY + zoneH + gap;
        drawZoneRow(graphics, local, state.szone[local], state.szonePos[local],
                fieldCenterX, localSzoneY, zoneW, zoneH, gap, 5, mouseX, mouseY);

        // ---- Hand cards ----
        List<Integer> hand = state.hand[local];
        int cardW = 34, cardH = 48, cardGap = 3;
        int handStartX = (width - hand.size() * (cardW + cardGap)) / 2;
        int handY = height - cardH - 30;
        for (int i = 0; i < hand.size(); i++) {
            int x = handStartX + i * (cardW + cardGap);
            int code = hand.get(i);
            boolean hovered = mouseX >= x && mouseX < x + cardW && mouseY >= handY && mouseY < handY + cardH;
            graphics.fill(x, handY, x + cardW, handY + cardH, hovered ? 0xFF556677 : 0xFF334455);
            graphics.renderOutline(x, handY, cardW, cardH, 0xFF888888);
            // Show card code (truncated to fit)
            String label = String.valueOf(code);
            if (label.length() > 5) label = label.substring(0, 5) + "..";
            graphics.drawString(font, label, x + 2, handY + 2, 0xFFFFFFFF, false);
        }

        // Opponent hand (face-down)
        List<Integer> oppHand = state.hand[opp];
        int oppHandStartX = (width - oppHand.size() * (cardW + cardGap)) / 2;
        int oppHandY = 26;
        // Just show card backs as count
        if (!oppHand.isEmpty()) {
            graphics.drawCenteredString(font, "Opponent hand: " + oppHand.size() + " cards",
                    width / 2, oppHandY - 12, 0xFF999999);
        }

        // ---- Pile counts ----
        int pileX = width - 90;
        graphics.drawString(font, "Deck: " + state.deckCount[local], pileX, height - 80, 0xFFAAAAAA, false);
        graphics.drawString(font, "Extra: " + state.extraCount[local], pileX, height - 68, 0xFFAAAAAA, false);
        graphics.drawString(font, "Grave: " + state.graveCount[local], pileX, height - 56, 0xFFAAAAAA, false);
        graphics.drawString(font, "Banish: " + state.banishedCount[local], pileX, height - 44, 0xFFAAAAAA, false);

        int oppPileX = 10;
        graphics.drawString(font, "Deck: " + state.deckCount[opp], oppPileX, 26, 0xFF777777, false);
        graphics.drawString(font, "Extra: " + state.extraCount[opp], oppPileX, 38, 0xFF777777, false);
        graphics.drawString(font, "Grave: " + state.graveCount[opp], oppPileX, 50, 0xFF777777, false);
        graphics.drawString(font, "Banish: " + state.banishedCount[opp], oppPileX, 62, 0xFF777777, false);

        // ---- Chain display ----
        if (!state.chain.isEmpty()) {
            graphics.drawString(font, "Chain: " + state.chain.size() + " link(s)",
                    fieldCenterX - 40, localSzoneY + zoneH + gap, 0xFFFF8800, false);
        }

        // ---- Prompt indicator ----
        if (state.pendingPrompt != null) {
            String promptName = state.pendingPrompt.getClass().getSimpleName();
            graphics.drawCenteredString(font, "Waiting: " + promptName,
                    width / 2, height / 2, 0xFFFFFF00);
        }

        // ---- Win overlay ----
        if (state.winner >= 0) {
            String result = state.winner == state.localPlayer ? "YOU WIN!" : "YOU LOSE";
            graphics.drawCenteredString(font, result, width / 2, height / 2 - 20, 0xFFFF4444);
            graphics.drawCenteredString(font, "Press ESC to close", width / 2, height / 2, 0xFFAAAAAA);
        }
    }

    private void drawZoneRow(GuiGraphics graphics, int player, int[] codes, int[] positions,
                             int centerX, int y, int w, int h, int gap,
                             int count, int mouseX, int mouseY) {
        int totalW = count * w + (count - 1) * gap;
        int startX = centerX - totalW / 2;
        for (int i = 0; i < count; i++) {
            int x = startX + i * (w + gap);
            int code = codes[i];
            boolean occupied = code != 0;
            boolean faceDown = occupied && (positions[i] & 0xA) != 0; // FACEDOWN bits
            boolean hovered = mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;

            int bgColor;
            if (!occupied) bgColor = 0xFF222222; // empty
            else if (faceDown) bgColor = 0xFF443322; // face-down brown
            else bgColor = hovered ? 0xFF446688 : 0xFF335577; // face-up

            graphics.fill(x, y, x + w, y + h, bgColor);
            graphics.renderOutline(x, y, w, h, 0xFF666666);

            if (occupied && !faceDown) {
                String label = String.valueOf(code);
                if (label.length() > 4) label = label.substring(0, 4);
                graphics.drawString(font, label, x + 2, y + 2, 0xFFFFFFFF, false);
            }

            // Tooltip on hover
            if (hovered && occupied) {
                String tip = "Card: " + code + (faceDown ? " (face-down)" : "");
                graphics.setTooltipForNextFrame(font,
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
        // Allow ESC only when the duel has ended
        if (event.key() == 256 && state.winner >= 0) { // 256 = GLFW_KEY_ESCAPE
            this.minecraft.setScreen(null);
            return true;
        }
        // Block ESC during active duel
        if (event.key() == 256) return true;

        return super.keyPressed(event);
    }

    // ---- Prompt UI ----

    /**
     * Rebuild the widget list based on the current pending prompt.
     * Called when a new prompt arrives or on init/resize.
     */
    private void rebuildPromptWidgets() {
        clearWidgets();

        if (state.pendingPrompt == null) return;

        int btnY = height / 2 + 20;
        int btnW = 100;
        int btnH = 20;
        int centerX = width / 2;

        switch (state.pendingPrompt) {
            case DuelMessage.SelectEffectYn sel -> {
                addRenderableWidget(Button.builder(Component.literal("Yes"), btn -> {
                    sendResponse(ResponseBuilder.selectYesNo(true));
                }).bounds(centerX - btnW - 5, btnY, btnW, btnH).build());
                addRenderableWidget(Button.builder(Component.literal("No"), btn -> {
                    sendResponse(ResponseBuilder.selectYesNo(false));
                }).bounds(centerX + 5, btnY, btnW, btnH).build());
            }
            case DuelMessage.SelectYesNo sel -> {
                addRenderableWidget(Button.builder(Component.literal("Yes"), btn -> {
                    sendResponse(ResponseBuilder.selectYesNo(true));
                }).bounds(centerX - btnW - 5, btnY, btnW, btnH).build());
                addRenderableWidget(Button.builder(Component.literal("No"), btn -> {
                    sendResponse(ResponseBuilder.selectYesNo(false));
                }).bounds(centerX + 5, btnY, btnW, btnH).build());
            }
            case DuelMessage.SelectPosition sel -> {
                int x = centerX - (btnW + 5) * 2;
                if ((sel.positions() & 0x1) != 0) {
                    addRenderableWidget(Button.builder(Component.literal("ATK"), btn -> {
                        sendResponse(ResponseBuilder.selectPosition(0x1));
                    }).bounds(x, btnY, btnW, btnH).build());
                    x += btnW + 5;
                }
                if ((sel.positions() & 0x4) != 0) {
                    addRenderableWidget(Button.builder(Component.literal("DEF"), btn -> {
                        sendResponse(ResponseBuilder.selectPosition(0x4));
                    }).bounds(x, btnY, btnW, btnH).build());
                    x += btnW + 5;
                }
                if ((sel.positions() & 0x8) != 0) {
                    addRenderableWidget(Button.builder(Component.literal("Set"), btn -> {
                        sendResponse(ResponseBuilder.selectPosition(0x8));
                    }).bounds(x, btnY, btnW, btnH).build());
                }
            }
            case DuelMessage.SelectChain sel -> {
                if (!sel.forced()) {
                    addRenderableWidget(Button.builder(Component.literal("Pass"), btn -> {
                        sendResponse(ResponseBuilder.selectChain(-1));
                    }).bounds(centerX - btnW / 2, btnY, btnW, btnH).build());
                }
                if (sel.count() > 0) {
                    addRenderableWidget(Button.builder(Component.literal("Activate"), btn -> {
                        sendResponse(ResponseBuilder.selectChain(0));
                    }).bounds(centerX - btnW / 2, btnY + btnH + 5, btnW, btnH).build());
                }
            }
            case DuelMessage.SelectOption sel -> {
                for (int i = 0; i < sel.options().size(); i++) {
                    final int idx = i;
                    addRenderableWidget(Button.builder(
                            Component.literal("Option " + (i + 1)),
                            btn -> sendResponse(ResponseBuilder.selectOption(idx))
                    ).bounds(centerX - btnW / 2, btnY + i * (btnH + 3), btnW, btnH).build());
                }
            }
            case DuelMessage.SelectIdleCmd sel -> {
                // Basic idle command buttons
                addRenderableWidget(Button.builder(Component.literal("End Turn"), btn -> {
                    sendResponse(ResponseBuilder.selectCmd(7, 0));
                }).bounds(centerX + 60, btnY, 80, btnH).build());
                addRenderableWidget(Button.builder(Component.literal("Battle"), btn -> {
                    sendResponse(ResponseBuilder.selectCmd(6, 0));
                }).bounds(centerX - 40, btnY, 80, btnH).build());
                // TODO: summon/set/activate buttons based on rawBody parsing
            }
            case DuelMessage.SelectBattleCmd sel -> {
                addRenderableWidget(Button.builder(Component.literal("End Battle"), btn -> {
                    sendResponse(ResponseBuilder.selectCmd(3, 0));
                }).bounds(centerX - btnW / 2, btnY, btnW, btnH).build());
                // TODO: attack buttons based on rawBody parsing
            }
            case DuelMessage.SelectCard sel -> {
                // Simple: select first min cards (temporary until proper card selection UI)
                addRenderableWidget(Button.builder(
                        Component.literal("Select card(s)"),
                        btn -> {
                            int[] indices = new int[sel.min()];
                            for (int i = 0; i < sel.min(); i++) indices[i] = i;
                            sendResponse(ResponseBuilder.selectCards(indices));
                        }
                ).bounds(centerX - btnW / 2, btnY, btnW, btnH).build());
            }
            case DuelMessage.SelectTribute sel -> {
                addRenderableWidget(Button.builder(
                        Component.literal("Tribute"),
                        btn -> {
                            int[] indices = new int[sel.min()];
                            for (int i = 0; i < sel.min(); i++) indices[i] = i;
                            sendResponse(ResponseBuilder.selectCards(indices));
                        }
                ).bounds(centerX - btnW / 2, btnY, btnW, btnH).build());
            }
            default -> {
                // Fallback for unhandled prompts — auto-respond with a safe default
                // This prevents the duel from getting stuck
                addRenderableWidget(Button.builder(
                        Component.literal("Continue"),
                        btn -> sendResponse(ResponseBuilder.selectCmd(0, 0))
                ).bounds(centerX - btnW / 2, btnY, btnW, btnH).build());
            }
        }
    }

    // ---- Response sending ----

    private void sendResponse(byte[] response) {
        ClientPacketDistributor.sendToServer(new DuelResponsePayload(response));
        state.pendingPrompt = null;
        rebuildPromptWidgets(); // clear prompt buttons
    }
}
