package com.haxerus.duelcraft.server.collection;

import com.haxerus.duelcraft.Duelcraft;
import com.haxerus.duelcraft.collection.*;
import com.haxerus.duelcraft.item.CardItem;
import com.haxerus.duelcraft.server.DuelManager;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import java.util.*;

/** Main-thread inventory/attachment transaction. No mutation occurs before policy evaluation succeeds. */
public final class CardTransferService {
    public interface Owner {
        UUID id();
        Optional<PlayerCollectionData> data();
        boolean busy();
        void persist(PlayerCollectionData data);
        ItemStack slot(int index);
        void slot(int index, ItemStack stack);
        void inventoryChanged();
    }

    private final CollectionService collections;
    private final Set<Integer> depositableCodes;

    public CardTransferService(CollectionService collections, Set<Integer> depositableCodes) {
        this.collections = collections;
        this.depositableCodes = Set.copyOf(depositableCodes);
    }

    public CollectionReply apply(ServerPlayer player, long expectedRevision, InventoryTransferPlan.Kind kind, int code, int amount) {
        return apply(owner(player), expectedRevision, kind, code, amount);
    }

    public CollectionReply apply(Owner owner, long expectedRevision, InventoryTransferPlan.Kind kind, int code, int amount) {
        var data = owner.data();
        if (data.isEmpty()) return rejected(CollectionError.DATA_UNAVAILABLE, 0);
        var before = data.orElseThrow();
        if (owner.busy()) return rejected(CollectionError.BUSY, before.revision());
        if (expectedRevision != before.revision()) return rejected(CollectionError.STALE, before.revision());
        var slots = snapshot(owner);
        var plan = InventoryTransferPlan.plan(slots, before.counts(), depositableCodes, kind, code, amount);
        if (plan.error() != CollectionError.NONE) return rejected(plan.error(), before.revision());
        int skipped = plan.skipped();
        if (kind == InventoryTransferPlan.Kind.DEPOSIT_ALL) {
            for (var slot : slots) {
                if (slot.code() == -1 && owner.slot(slot.index()).is(Duelcraft.CARD.get())) skipped += slot.count();
            }
        }
        var change = collections.replaceCounts(before, expectedRevision, false, owner.id(), plan.counts());
        if (!change.success()) return new CollectionReply.Rejected(change.error(), before.revision(), change.eligibility());
        // Prepare every replacement before either side is committed. Untouched stack objects are retained.
        var replacements = replacements(slots, plan.slots());
        replacements.forEach(owner::slot);
        owner.persist(change.data());
        owner.inventoryChanged();
        return new CollectionReply.Changed(change.data().revision(), change.data().activeDeckId(), null,
                plan.moved(), skipped, change.eligibility());
    }

    public static List<InventoryTransferPlan.Slot> snapshot(Owner owner) {
        var slots = new ArrayList<InventoryTransferPlan.Slot>(37);
        for (int i = 0; i <= 40; i++) {
            if (i >= 36 && i != 40) continue;
            var stack = owner.slot(i).copy();
            int code = stack.isEmpty() ? 0 : CardItem.isCanonical(stack) && stack.getCount() <= 64
                    ? CardItem.code(stack).orElseThrow() : -1;
            slots.add(new InventoryTransferPlan.Slot(i, code, stack.getCount()));
        }
        return List.copyOf(slots);
    }

    public static Map<Integer, ItemStack> replacements(List<InventoryTransferPlan.Slot> before,
                                                       List<InventoryTransferPlan.Slot> after) {
        var replacements = new LinkedHashMap<Integer, ItemStack>();
        for (int i = 0; i < before.size(); i++) {
            var slot = after.get(i);
            if (!slot.equals(before.get(i))) replacements.put(slot.index(),
                    slot.count() == 0 ? ItemStack.EMPTY : CardItem.stack(slot.code(), slot.count()));
        }
        return replacements;
    }

    public static Owner owner(ServerPlayer player) {
        if (!player.server.isSameThread()) throw new IllegalStateException("Card transfers require the server thread");
        return new Owner() {
            public UUID id() { return player.getUUID(); }
            public Optional<PlayerCollectionData> data() { return player.getData(CollectionAttachments.COLLECTION).data(); }
            public boolean busy() { return DuelManager.get() == null || DuelManager.get().isBusy(player); }
            public void persist(PlayerCollectionData data) { player.setData(CollectionAttachments.COLLECTION, CollectionAttachment.valid(data)); }
            public ItemStack slot(int index) { return player.getInventory().getItem(index); }
            public void slot(int index, ItemStack stack) { player.getInventory().setItem(index, stack); }
            public void inventoryChanged() {
                player.getInventory().setChanged();
                player.inventoryMenu.broadcastChanges();
                if (player.containerMenu != player.inventoryMenu) player.containerMenu.broadcastChanges();
            }
        };
    }

    private CollectionReply rejected(CollectionError error, long revision) {
        return new CollectionReply.Rejected(error, revision, collections.emptyReport());
    }
}
