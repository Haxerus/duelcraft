package com.haxerus.duelcraft.client.uitest;

import com.lowdragmc.lowdraglib2.uitest.TestContext;

/** Keep release coordinates when a press immediately replaces the screen or opens a modal. */
public final class ManagementTestInput {
    private ManagementTestInput() {}
    public static void click(TestContext ctx, String selector) {
        var target = ctx.el(selector);
        ctx.require("visible click target " + selector, target.isVisible());
        var point = target.bounds().center();
        ctx.input().moveTo(point.x, point.y);
        ctx.input().mouseDown(point.x, point.y, 0);
        ctx.input().mouseUp(point.x, point.y, 0);
    }
}
