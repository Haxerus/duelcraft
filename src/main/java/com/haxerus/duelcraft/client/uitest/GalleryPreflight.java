package com.haxerus.duelcraft.client.uitest;

import com.lowdragmc.lowdraglib2.gui.ui.UI;
import com.lowdragmc.lowdraglib2.gui.ui.style.PropertyRegistry;
import com.lowdragmc.lowdraglib2.gui.ui.style.Stylesheet;
import com.lowdragmc.lowdraglib2.utils.XmlUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Fixture diagnostics, not a general LSS validator. Selectors and lazy values still need runtime proof. */
public final class GalleryPreflight {
    private GalleryPreflight() {}
    public record Result(List<String> errors, List<String> uncheckedValues) {}

    public static Result check(ResourceLocation layout, Set<String> requiredIds) {
        var document = XmlUtils.loadXml(layout);
        if (document == null) return new Result(List.of(layout + ": missing or unreadable layout"), List.of());
        return check(document, layout.toString(), requiredIds);
    }

    public static Set<String> ids(Document document) {
        var ids = new LinkedHashSet<String>();
        var nodes = document.getElementsByTagName("*");
        for (int i = 0; i < nodes.getLength(); i++) {
            var id = ((Element) nodes.item(i)).getAttribute("id");
            if (!id.isBlank()) ids.add(id);
        }
        return Set.copyOf(ids);
    }

    public static Result check(Document document, String source, Set<String> requiredIds) {
        var errors = new ArrayList<String>();
        var unchecked = new ArrayList<String>();
        var seen = new HashSet<String>();
        var nodes = document.getElementsByTagName("*");
        for (int i = 0; i < nodes.getLength(); i++) {
            var element = (Element) nodes.item(i);
            var id = element.getAttribute("id");
            var context = source + " <" + element.getTagName() + (id.isBlank() ? "" : " #" + id) + ">";
            if (!id.isBlank() && !seen.add(id)) errors.add(context + ": duplicate id '" + id + "'");
            if (element.hasAttribute("style")) declarations(element.getAttribute("style"), context, errors, unchecked);
            if (element.getTagName().equals("style")) stylesheet(element.getTextContent(), context, errors, unchecked);
            if (element.getTagName().equals("stylesheet")) {
                var literal = element.getAttribute("location");
                var location = ResourceLocation.tryParse(literal);
                if (location == null) {
                    errors.add(context + ": invalid literal stylesheet location '" + literal + "'");
                    continue;
                }
                var resource = Minecraft.getInstance().getResourceManager().getResource(location);
                if (resource.isEmpty()) {
                    errors.add(context + ": missing stylesheet " + location);
                } else {
                    try (var input = resource.get().open()) {
                        stylesheet(new String(input.readAllBytes(), StandardCharsets.UTF_8), source + " -> " + location, errors, unchecked);
                    } catch (Exception exception) {
                        errors.add(context + ": cannot read stylesheet " + location + ": " + exception.getMessage());
                    }
                }
            }
        }
        for (var required : requiredIds) if (!seen.contains(required)) errors.add(source + ": missing required id '" + required + "'");
        try {
            UI.of(document);
        } catch (Exception exception) {
            errors.add(source + ": UI.of construction failed: " + exception.getMessage());
        }
        return new Result(List.copyOf(errors), List.copyOf(unchecked));
    }

    private static String uncomment(String text) {
        // Same comment removal as Stylesheet.parse before using its declaration patterns.
        return text.replaceAll("//.*", "").replaceAll("/\\*[^*]*\\*+(?:[^/*][^*]*\\*+)*/", "");
    }

    private static void stylesheet(String text, String source, List<String> errors, List<String> unchecked) {
        text = uncomment(text);
        if (text.contains("@media")) errors.add(source + ": unsupported browser construct @media");
        var rules = Stylesheet.RULE.matcher(text);
        while (rules.find()) declarations(rules.group(2), source + " selector '" + rules.group(1).trim() + "'", errors, unchecked);
    }

    private static void declarations(String text, String context, List<String> errors, List<String> unchecked) {
        var declarations = Stylesheet.DECL.matcher(uncomment(text));
        while (declarations.find()) {
            var name = declarations.group(1).trim();
            var raw = declarations.group(2).trim();
            var diagnostic = context + " property '" + name + "' value '" + raw + "'";
            if (name.startsWith("--") || raw.contains("var(")) {
                errors.add(diagnostic + ": unsupported CSS custom property / var(...)");
                continue;
            }
            var property = PropertyRegistry.byName(name);
            if (property == null) {
                errors.add(diagnostic + ": unknown property in pinned LDLib2");
                continue;
            }
            try {
                var parsed = property.valueParser.parse(raw);
                if (parsed == null || parsed.compute() == null)
                    unchecked.add(diagnostic + ": parser returned null; not verified; requires runtime assertion or visual review");
            } catch (Exception exception) {
                errors.add(diagnostic + ": parser rejected value: " + exception.getMessage());
            }
        }
    }
}
