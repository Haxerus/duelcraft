package com.haxerus.duelcraft.client;

import java.util.function.IntFunction;
import java.util.function.LongFunction;

/** Effect questions carry card/location arguments in addition to the description code. */
final class EffectPromptText {
    private EffectPromptText() { }

    static String question(long desc, String cardName, String location,
                           LongFunction<String> descriptions, IntFunction<String> systems) {
        boolean standard = desc == 0 || desc == 221;
        String text = standard ? systems.apply(desc == 0 ? 200 : 221) : descriptions.apply(desc);
        if (text == null) text = desc == 221
                ? "Activate the Trigger Effect of \"%ls\" from [%ls]?"
                : "Use the effect of \"%ls\" from [%ls]?";
        String question = standard ? substitute(text, cardName, location) : substitute(text, cardName);
        if (!standard && !text.contains("%ls")) question += "\n(" + cardName + ")";
        if (desc == 221) {
            String hint = systems.apply(223);
            if (hint != null && !hint.isBlank()) question += "\n" + hint;
        }
        return question;
    }

    private static String substitute(String template, String... values) {
        var result = new StringBuilder();
        int start = 0;
        for (String value : values) {
            int index = template.indexOf("%ls", start);
            if (index < 0) break;
            result.append(template, start, index).append(value);
            start = index + 3;
        }
        return result.append(template, start, template.length()).toString();
    }
}
