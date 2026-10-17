package com.haxerus.duelcraft.client.uitest;

import com.haxerus.duelcraft.DuelcraftClient;
import com.haxerus.duelcraft.collection.*;
import com.haxerus.duelcraft.item.CardItem;
import com.haxerus.duelcraft.server.collection.CollectionReplyPayload;
import com.lowdragmc.lowdraglib2.uitest.*;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/** Client bodies kept separate from dedicated scenario registration. */
public final class CollectionTransferClient {
    public static void exchange(ScenarioBuilder s, CollectionCommand command, CollectionError error, int carried) {
        s.step("send authenticated transfer", ctx -> ctx.put("reply", DuelcraftClient.getCollectionClient().request(command).toCompletableFuture()))
         .waitUntil("private transfer reply", ctx -> ctx.<CompletableFuture<CollectionReply>>get("reply").isDone())
         .check("expected authoritative result", ctx -> {
             var reply = ctx.<CompletableFuture<CollectionReply>>get("reply").join();
             return error == CollectionError.NONE ? reply instanceof CollectionReply.Changed
                     : reply instanceof CollectionReply.Rejected r && r.error() == error;
         }).waitUntil("authoritative inventory sync", ctx -> {
             int count = 0;
             for (int i = 0; i <= 40; i++) {
                 if (i >= 36 && i != 40) continue;
                 var stack = ctx.mc().player.getInventory().getItem(i);
                 if (CardItem.isCanonical(stack) && CardItem.code(stack).orElse(0) == 89631139) count += stack.getCount();
             }
             return count == carried;
         });
    }
    public static void mark(ScenarioBuilder s) {
        s.step("mark idle receive boundary", ctx -> ctx.put("receiptMark", ctx.<List<CollectionReplyPayload>>get("receipts").size()));
    }
    public static void unchanged(ScenarioBuilder s) {
        s.check("idle player receives no foreign collection packet", ctx ->
                ctx.<List<CollectionReplyPayload>>get("receipts").size() == ctx.<Integer>get("receiptMark"));
    }
}
