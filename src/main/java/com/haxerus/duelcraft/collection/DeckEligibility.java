package com.haxerus.duelcraft.collection;

import com.haxerus.duelcraft.core.DeckValidator;
import com.haxerus.duelcraft.core.DuelRule;
import com.haxerus.duelcraft.core.data.CardCatalog;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import static com.haxerus.duelcraft.core.OcgConstants.*;

/** Supported host checks; rule-specific forbidden types and banlists are not implemented. */
public final class DeckEligibility {
    public record Issue(String key, int code, int actual, int limit) {}

    public record Report(List<Issue> problems, Map<Integer, Integer> missing, boolean moreProblems) {
        public Report {
            problems = List.copyOf(problems);
            missing = Map.copyOf(missing);
        }

        public boolean eligible() {
            return problems.isEmpty() && missing.isEmpty() && !moreProblems;
        }
    }

    private static final int EXTRA_TYPES = TYPE_FUSION | TYPE_SYNCHRO | TYPE_XYZ | TYPE_LINK;

    private DeckEligibility() {}

    public static Report check(DeckList list, Map<Integer, Long> counts,
                               Map<Integer, CardCatalog.Facts> facts, DuelRule rule) {
        var issues = new ArrayList<Issue>();
        var missing = new HashMap<Integer, Integer>();
        int main = list.main().size();
        if (main < DeckValidator.MAIN_MIN || main > DeckValidator.MAIN_MAX) {
            issues.add(issue("main_size", 0, main, main < DeckValidator.MAIN_MIN
                    ? DeckValidator.MAIN_MIN : DeckValidator.MAIN_MAX));
        }
        if (list.extra().size() > DeckValidator.EXTRA_MAX) {
            issues.add(issue("extra_size", 0, list.extra().size(), DeckValidator.EXTRA_MAX));
        }
        if (list.side().size() > DeckValidator.EXTRA_MAX) {
            issues.add(issue("side_size", 0, list.side().size(), DeckValidator.EXTRA_MAX));
        }
        var mainCodes = new HashSet<>(list.main());
        var extraCodes = new HashSet<>(list.extra());
        var required = list.requiredCopies();
        for (int code : required.keySet().stream().sorted().toList()) {
            int copies = required.get(code);
            if (copies > DeckValidator.MAX_COPIES) issues.add(issue("copies", code, copies, DeckValidator.MAX_COPIES));
            long owned = counts.getOrDefault(code, 0L);
            if (owned < copies) missing.put(code, Math.toIntExact(copies - owned));
            var card = facts.get(code);
            if (card == null) {
                issues.add(issue("unknown", code, 0, 0));
                continue;
            }
            int type = card.type();
            if ((type & TYPE_TOKEN) != 0 || (type & (TYPE_MONSTER | TYPE_SPELL | TYPE_TRAP)) == 0) {
                issues.add(issue("unplayable", code, type, 0));
                continue;
            }
            boolean extraMonster = (type & TYPE_MONSTER) != 0 && (type & EXTRA_TYPES) != 0;
            if (mainCodes.contains(code) && (type & EXTRA_TYPES) != 0) issues.add(issue("main_placement", code, type, 0));
            if (extraCodes.contains(code) && !extraMonster) issues.add(issue("extra_placement", code, type, 0));
        }
        boolean more = issues.size() > CollectionLimits.ISSUES;
        return new Report(issues.subList(0, Math.min(issues.size(), CollectionLimits.ISSUES)), missing, more);
    }

    private static Issue issue(String key, int code, int actual, int limit) {
        return new Issue("duelcraft.collection.issue." + key, code, actual, limit);
    }
}
