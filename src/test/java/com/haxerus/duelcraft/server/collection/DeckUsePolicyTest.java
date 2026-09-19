package com.haxerus.duelcraft.server.collection;

import com.haxerus.duelcraft.api.DeckUseCheckEvent;
import com.haxerus.duelcraft.collection.DeckList;
import com.haxerus.duelcraft.core.DuelRule;
import com.haxerus.duelcraft.core.OcgConstants;
import com.haxerus.duelcraft.core.data.CardCatalog;
import net.neoforged.bus.api.BusBuilder;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;

class DeckUsePolicyTest {
    private static final UUID OWNER = UUID.randomUUID();
    private static final DeckList LEGAL = new DeckList(IntStream.rangeClosed(1, 40).boxed().toList(), List.of(), List.of());
    private static final Map<Integer, CardCatalog.Facts> FACTS = IntStream.rangeClosed(1, 41).boxed().collect(
            java.util.stream.Collectors.toUnmodifiableMap(code -> code,
                    code -> new CardCatalog.Facts(code, OcgConstants.TYPE_MONSTER)));

    private static DeckUsePolicy.Context context(DeckList list, Map<Integer, Long> counts) {
        return new DeckUsePolicy.Context(OWNER, list, counts, DuelRule.MR5);
    }

    @Test void optionalOwnershipPermitsAValidUnownedListAndStillReportsEveryShortage() {
        var report = new DeckUsePolicy(false, null).check(context(LEGAL, Map.of()), FACTS);
        assertTrue(report.eligible());
        assertFalse(report.ownershipRequired());
        assertEquals(IntStream.rangeClosed(1, 40).boxed().collect(
                java.util.stream.Collectors.toMap(code -> code, code -> 1)), report.missing());
    }

    @Test void requiredOwnershipDeniesAValidUnownedList() {
        var report = new DeckUsePolicy(true, null).check(context(LEGAL, Map.of()), FACTS);
        assertFalse(report.eligible());
        assertTrue(report.ownershipRequired());
        assertEquals(40, report.missing().size());
    }

    @Test void restrictionDeniesUnderEitherOwnershipSetting() {
        DeckUsePolicy.Restriction restriction = ignored -> "Era locked";
        var optional = new DeckUsePolicy(false, restriction).check(context(LEGAL, Map.of()), FACTS);
        var required = new DeckUsePolicy(true, restriction).check(context(LEGAL, CollectionTestData.owned()), FACTS);
        assertFalse(optional.eligible());
        assertEquals("Era locked", optional.restrictionReason());
        assertFalse(required.eligible());
        assertEquals("Era locked", required.restrictionReason());
    }

    @Test void directRestrictionUsesFallbackWhenNonblankReasonIsBlankAfterBounding() {
        var reason = " ".repeat(DeckUsePolicy.MAX_RESTRICTION_REASON_LENGTH) + "denied";
        var report = new DeckUsePolicy(false, ignored -> reason)
                .check(context(LEGAL, Map.of()), FACTS);
        assertEquals(DeckUsePolicy.INVALID_REASON_FALLBACK, report.restrictionReason());
    }

    @Test void approvingRestrictionCannotRescueCoreOrOwnershipFailures() {
        var policy = new DeckUsePolicy(true, ignored -> null);
        var cases = new ArrayList<DeckList>();
        cases.add(new DeckList(IntStream.rangeClosed(1, 39).boxed().toList(), List.of(), List.of()));
        cases.add(new DeckList(Collections.nCopies(40, 1), List.of(), List.of()));
        cases.add(new DeckList(IntStream.rangeClosed(1, 39).boxed().toList(), List.of(1), List.of()));
        cases.add(new DeckList(IntStream.rangeClosed(1, 39).boxed().toList(), List.of(), List.of(99)));
        var tokenFacts = new HashMap<>(FACTS);
        tokenFacts.put(1, new CardCatalog.Facts(1, OcgConstants.TYPE_TOKEN));
        assertFalse(policy.check(context(LEGAL, CollectionTestData.owned()), tokenFacts).eligible());
        for (var list : cases) assertFalse(policy.check(context(list, Map.of()), FACTS).eligible());
        assertFalse(policy.check(context(LEGAL, Map.of()), FACTS).eligible());
    }

    @Test void contextCopiesCandidateCounts() {
        var counts = new HashMap<Integer, Long>();
        counts.put(1, 1L);
        var context = context(LEGAL, counts);
        counts.clear();
        assertEquals(Map.of(1, 1L), context.counts());
        assertThrows(UnsupportedOperationException.class, () -> context.counts().clear());
    }

    @Test void eventKeepsFirstDenialAndBoundsListenerReasons() {
        assertNull(DeckUseCheckEvent.restriction(BusBuilder.builder().build())
                .denial(context(LEGAL, Map.of())));
        var bus = BusBuilder.builder().build();
        bus.addListener(DeckUseCheckEvent.class, event -> event.deny("Era locked"));
        bus.addListener(DeckUseCheckEvent.class, event -> event.deny("Replacement"));
        var restriction = DeckUseCheckEvent.restriction(bus);
        assertEquals("Era locked", restriction.denial(context(LEGAL, Map.of())));

        var longBus = BusBuilder.builder().build();
        longBus.addListener(DeckUseCheckEvent.class, event -> event.deny("x".repeat(300)));
        assertEquals(DeckUsePolicy.MAX_RESTRICTION_REASON_LENGTH,
                DeckUseCheckEvent.restriction(longBus).denial(context(LEGAL, Map.of())).length());

        var invalidBus = BusBuilder.builder().build();
        invalidBus.addListener(DeckUseCheckEvent.class, event -> event.deny("  "));
        assertEquals(DeckUseCheckEvent.INVALID_REASON_FALLBACK,
                DeckUseCheckEvent.restriction(invalidBus).denial(context(LEGAL, Map.of())));

        var boundedBlankBus = BusBuilder.builder().build();
        boundedBlankBus.addListener(DeckUseCheckEvent.class,
                event -> event.deny(" ".repeat(DeckUsePolicy.MAX_RESTRICTION_REASON_LENGTH) + "denied"));
        assertEquals(DeckUseCheckEvent.INVALID_REASON_FALLBACK,
                DeckUseCheckEvent.restriction(boundedBlankBus).denial(context(LEGAL, Map.of())));
    }

    @Test void eventListenerFailureBecomesAPolicyEvaluationFailure() {
        var bus = BusBuilder.builder().build();
        bus.addListener(DeckUseCheckEvent.class, event -> {
            throw new IllegalStateException("broken listener");
        });
        var policy = new DeckUsePolicy(false, DeckUseCheckEvent.restriction(bus));
        assertThrows(DeckUsePolicy.EvaluationException.class,
                () -> policy.check(context(LEGAL, Map.of()), FACTS));
    }
}
