package com.haxerus.duelcraft;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.common.ModConfigSpec;

// An example config class. This is not required, but it's a good idea to have one to keep your config organized.
// Demonstrates how to use Neo's config APIs
public class Config {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.BooleanValue LOG_DIRT_BLOCK = BUILDER
            .comment("Whether to log the dirt block on common setup")
            .define("logDirtBlock", true);

    public static final ModConfigSpec.IntValue MAGIC_NUMBER = BUILDER
            .comment("A magic number")
            .defineInRange("magicNumber", 42, 0, Integer.MAX_VALUE);

    public static final ModConfigSpec.ConfigValue<String> MAGIC_NUMBER_INTRODUCTION = BUILDER
            .comment("What you want the introduction message to be for the magic number")
            .define("magicNumberIntroduction", "The magic number is... ");

    // a list of strings that are treated as resource locations for items
//    public static final ModConfigSpec.ConfigValue<List<? extends String>> ITEM_STRINGS = BUILDER
//            .comment("A list of items to log on common setup.")
//            .defineListAllowEmpty("items", List.of("minecraft:iron_ingot"), () -> "",);

    public static final ModConfigSpec.ConfigValue<List<? extends String>> CARD_DATABASE_PATHS = BUILDER
            .comment("Paths to your Yu-Gi-Oh! card database files")
            .defineList("dbPaths", List.of("C:/ProjectIgnis/expansions/cards.cdb"), () -> "",  Config::validateNonEmpty);

    public static final ModConfigSpec.ConfigValue<List<? extends String>> SCRIPT_SEARCH_PATHS = BUILDER
            .comment("Paths to your Yu-Gi-Oh! card script files")
            .defineList("scriptPaths", List.of("C:/ProjectIgnis/script", "C:/ProjectIgnis/script/official"), () -> "",  Config::validateNonEmpty);

    public static final ModConfigSpec.ConfigValue<String> CARD_DATABASE_URL = BUILDER
            .comment("URL to download the card database from (BabelCDB)")
            .define("cardDatabaseUrl",
                    "https://raw.githubusercontent.com/ProjectIgnis/BabelCDB/master/cards.cdb");

    public static final ModConfigSpec.ConfigValue<String> CARD_IMAGE_BASE_URL = BUILDER
            .comment("Base URL for card images (code.jpg appended)")
            .define("cardImageBaseUrl",
                    "https://images.ygoprodeck.com/images/cards_small/");

    static final ModConfigSpec SPEC = BUILDER.build();

    private static boolean validateNonEmpty(final Object obj) {
        return obj instanceof String path && !path.isEmpty();
    }
}
