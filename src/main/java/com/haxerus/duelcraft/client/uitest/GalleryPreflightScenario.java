package com.haxerus.duelcraft.client.uitest;

import com.lowdragmc.lowdraglib2.registry.RegistrationEnvironment;
import com.lowdragmc.lowdraglib2.registry.annotation.LDLRegisterClient;
import com.lowdragmc.lowdraglib2.uitest.*;
import com.lowdragmc.lowdraglib2.utils.XmlUtils;
import net.minecraft.resources.ResourceLocation;
import java.util.Set;

@LDLRegisterClient(name = "gallery_preflight", group = "duelcraft_gallery", registry = UIScenario.REGISTRY,
        environment = RegistrationEnvironment.DEV_ONLY)
public final class GalleryPreflightScenario implements UIScenario {
    static final ResourceLocation SPECIMEN = ResourceLocation.fromNamespaceAndPath("duelcraft", "ui/uitest/gallery_specimen.xml");
    static final Set<String> REQUIRED = Set.of("gallery-root", "gallery-action", "gallery-name", "gallery-selector",
            "gallery-save", "gallery-feedback", "gallery-modal", "gallery-dismiss");
    @Override public void configure(ScenarioOptions options) { options.requiresWorld(false); }
    @Override public void define(ScenarioBuilder s) {
        s.step("resource preflight", ctx -> {
            var result = GalleryPreflight.check(SPECIMEN, REQUIRED);
            ctx.attach("specimen diagnostics", result.toString());
            ctx.check("specimen has zero errors", result.errors().isEmpty());
            ctx.attach("unchecked owner", "GallerySpecimenScenario runtime text/bounds/state assertions and agent visual review own all unchecked fixture values");
            var duplicate = check("duplicate", "<root id='same'><element id='same'/></root>", Set.of());
            ctx.check("duplicate ID diagnostic", contains(duplicate.errors(), "memory:duplicate", "same"));
            var missing = check("required", "<root/>", Set.of("needed"));
            ctx.check("required ID diagnostic", contains(missing.errors(), "memory:required", "needed"));
            var linked = check("linked", "<stylesheet location='duelcraft:lss/missing-gallery.lss'/><root/>", Set.of());
            ctx.check("missing stylesheet diagnostic", contains(linked.errors(), "memory:linked", "duelcraft:lss/missing-gallery.lss"));
            var font = check("font", "<root id='bad-font' style='font-family: missing-gallery-font;'/>", Set.of());
            ctx.check("unknown font-family diagnostic", contains(font.errors(), "memory:font", "bad-font", "font-family"));
            var numeric = check("numeric", "<root id='bad-number' style='width: twelve; height: 2rem;'/>", Set.of());
            ctx.check("malformed numeric remains unchecked", contains(numeric.uncheckedValues(), "memory:numeric", "bad-number", "width"));
            ctx.check("unsupported unit remains unchecked", contains(numeric.uncheckedValues(), "memory:numeric", "bad-number", "height"));
            var embedded = check("embedded", "<style>#bad { typo-property: 2; }</style><root id='bad'/>", Set.of());
            ctx.check("embedded declaration diagnostic", contains(embedded.errors(), "memory:embedded", "#bad", "typo-property"));
            for (var construct : new String[]{"@media test { width: 2; }", "#bad { --color: red; }", "#bad { width: var(--width); }"}) {
                var browser = check("browser", "<style>" + construct + "</style><root id='bad'/>", Set.of());
                String token = construct.contains("@media") ? "@media" : construct.contains("var(") ? "var(" : "--color";
                ctx.check("browser construct rejected: " + construct, contains(browser.errors(), "memory:browser", token));
            }
            ctx.attach("negative numeric diagnostics", numeric.toString());
            ctx.attach("negative unchecked owner", "Intentionally invalid in-memory values: this scenario asserts detection only; never accepted as fixture values");
            ctx.attach("negative font diagnostic", font.toString());
            ctx.check("ID helper gathers unique declarations", GalleryPreflight.ids(XmlUtils.loadXml("<ldlib2-ui><root id='same'><element id='same'/><element id=' '/></root></ldlib2-ui>")).equals(Set.of("same")));
        });
    }
    private static GalleryPreflight.Result check(String source, String content, Set<String> required) {
        return GalleryPreflight.check(XmlUtils.loadXml("<ldlib2-ui>" + content + "</ldlib2-ui>"), "memory:" + source, required);
    }
    private static boolean contains(java.util.List<String> messages, String... terms) {
        return messages.stream().anyMatch(message -> java.util.Arrays.stream(terms).allMatch(message::contains));
    }
}
