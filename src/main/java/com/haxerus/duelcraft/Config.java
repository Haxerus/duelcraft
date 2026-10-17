package com.haxerus.duelcraft;

import net.neoforged.neoforge.common.ModConfigSpec;

public class Config {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.ConfigValue<String> CARD_IMAGE_BASE_URL = BUILDER
            .comment("Base URL for card images (code.jpg appended)")
            .define("cardImageBaseUrl",
                    "https://images.ygoprodeck.com/images/cards_small/");

    public static final ModConfigSpec.ConfigValue<String> STRINGS_CONF_URL = BUILDER
            .comment("URL to download the strings.conf (system/counter/victory/archetype names). "
                    + "Downloaded each startup if reachable; cached copy is used as fallback.")
            .define("stringsConfUrl",
                    "https://raw.githubusercontent.com/ProjectIgnis/Distribution/master/config/strings.conf");

    static final ModConfigSpec SPEC = BUILDER.build();
}
