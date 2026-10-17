package com.haxerus.duelcraft.client.uitest;

import com.haxerus.duelcraft.DuelcraftClient;
import com.haxerus.duelcraft.client.*;
import com.haxerus.duelcraft.client.carddata.CardImageManager;
import com.lowdragmc.lowdraglib2.gui.texture.SpriteTexture;
import com.lowdragmc.lowdraglib2.uitest.TestContext;
import net.minecraft.resources.ResourceLocation;

import java.lang.reflect.Proxy;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/** Local deterministic art and response capture for synthetic pointer scenarios only. */
public final class DuelVisibilityFixture implements AutoCloseable {
    public final List<byte[]> responses = new ArrayList<>();
    private final Object images = field(DuelcraftClient.class, null, "cardImageManager");
    private final CardImageManager fixtureImages = new CardImageManager("https://invalid.invalid/", Path.of("build/visibility-art")) {
        @Override public ResourceLocation getCardTexture(int code) { return code == 0 ? null : texture(code); }
        @Override public ResourceLocation getCardArt(int code) { return getCardTexture(code); }
    };

    public DuelVisibilityFixture() {
        set(DuelcraftClient.class, null, "cardImageManager", fixtureImages);
    }

    public static ResourceLocation texture(int code) {
        return ResourceLocation.parse("minecraft:textures/block/" + (code % 2 == 0 ? "gold_block" : "diamond_block") + ".png");
    }

    public static ClientDuelState state() { return (ClientDuelState) field(LDLibDuelScreen.class, null, "activeState"); }

    public void captureResponses() {
        Object refresher = field(LDLibDuelScreen.class, null, "refresher");
        var prompt = (PromptController) field(refresher.getClass(), refresher, "prompt");
        var originalPrompt = field(PromptController.class, prompt, "callbacks");
        var capture = Proxy.newProxyInstance(PromptController.class.getClassLoader(),
                new Class<?>[]{PromptController.Callbacks.class}, (proxy, method, args) -> {
                    if (method.getName().equals("sendResponse")) {
                        captureResponse((Supplier<byte[]>) args[0], prompt);
                        return null;
                    }
                    return method.invoke(originalPrompt, args);
                });
        set(PromptController.class, prompt, "callbacks", capture);

        var clicks = (ClickDispatcher) field(refresher.getClass(), refresher, "clicks");
        var originalClicks = field(ClickDispatcher.class, clicks, "callbacks");
        var clickCapture = Proxy.newProxyInstance(ClickDispatcher.class.getClassLoader(),
                new Class<?>[]{ClickDispatcher.Callbacks.class}, (proxy, method, args) -> {
                    if (method.getName().equals("sendResponse")) {
                        captureResponse((Supplier<byte[]>) args[0], prompt);
                        return null;
                    }
                    return method.invoke(originalClicks, args);
                });
        set(ClickDispatcher.class, clicks, "callbacks", clickCapture);
    }

    private void captureResponse(Supplier<byte[]> response, PromptController prompt) {
        byte[] bytes = response.get();
        if (state().markResponseSent()) {
            responses.add(bytes.clone());
            state().onResponseSent();
            prompt.onResponseSent();
        }
    }

    public static void artwork(TestContext ctx, int index, int code) {
        var element = ctx.all("#prompt-body .card").get(index).element();
        var texture = element.getStyle().backgroundTexture().getRawTexture();
        ResourceLocation expected = code == 0 ? ResourceLocation.parse("duelcraft:textures/card_back.png") : texture(code);
        ctx.check("candidate " + index + " artwork identity " + code,
                texture instanceof SpriteTexture sprite && sprite.getImageLocation().equals(expected), expected, texture);
    }

    public static void pointer(TestContext ctx, int index, boolean click) {
        var b = ctx.all("#prompt-body .card").get(index).bounds();
        ctx.input().moveTo(b.centerX(), b.centerY());
        if (click) {
            ctx.input().mouseDown(b.centerX(), b.centerY(), 0);
            ctx.input().mouseUp(b.centerX(), b.centerY(), 0);
        }
    }

    private static Object field(Class<?> type, Object owner, String name) {
        try { var f = type.getDeclaredField(name); f.setAccessible(true); return f.get(owner); }
        catch (ReflectiveOperationException e) { throw new AssertionError(e); }
    }
    private static void set(Class<?> type, Object owner, String name, Object value) {
        try { var f = type.getDeclaredField(name); f.setAccessible(true); f.set(owner, value); }
        catch (ReflectiveOperationException e) { throw new AssertionError(e); }
    }
    @Override public void close() {
        set(DuelcraftClient.class, null, "cardImageManager", images);
        fixtureImages.close();
    }
}
