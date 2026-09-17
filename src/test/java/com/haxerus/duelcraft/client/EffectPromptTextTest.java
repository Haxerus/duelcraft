package com.haxerus.duelcraft.client;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class EffectPromptTextTest {
    private static final Map<Integer, String> SYSTEMS = Map.of(
            200, "Use the effect of \"%ls\" from [%ls]?",
            221, "Activate the Trigger Effect of \"%ls\" from [%ls]?",
            223, "Respond now.");

    @Test
    void triggerQuestionFillsCardAndLocationAndRetainsTriggerHint() {
        assertEquals("Activate the Trigger Effect of \"Dracotail Lukias\" from [Hand]?\nRespond now.",
                EffectPromptText.question(221, "Dracotail Lukias", "Hand",
                        desc -> SYSTEMS.get((int) desc), SYSTEMS::get));
    }

    @Test
    void zeroDescriptionUsesDefaultEffectQuestion() {
        assertEquals("Use the effect of \"Mystical Space Typhoon\" from [Spell/Trap Zone]?",
                EffectPromptText.question(0, "Mystical Space Typhoon", "Spell/Trap Zone",
                        desc -> "(no description)", SYSTEMS::get));
    }

    @Test
    void otherDescriptionsReceiveCardNameWithoutReparsingInsertedTokens() {
        assertEquals("Pay LP with the effect of \"A %ls card\", instead?",
                EffectPromptText.question(218, "A %ls card", "Hand",
                        desc -> "Pay LP with the effect of \"%ls\", instead?", SYSTEMS::get));
    }

    @Test
    void ordinaryEffectTextKeepsItsCardContextAndLiteralPercent() {
        assertEquals("Gain 50% of its ATK.\n(Example card)",
                EffectPromptText.question(123456789, "Example card", "Hand",
                        desc -> "Gain 50% of its ATK.", SYSTEMS::get));
    }

    @Test
    void defaultQuestionIsReadableWithoutDownloadedStrings() {
        assertEquals("Use the effect of \"Example card\" from [Hand]?",
                EffectPromptText.question(0, "Example card", "Hand", desc -> "(no description)",
                        code -> null));
    }
}
