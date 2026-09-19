package com.haxerus.duelcraft.collection;

import java.util.UUID;

/** Whitelisted requests; clients never supply replacement counts or inventory contents. */
public sealed interface CollectionCommand {
    enum PageKind { COUNTS, DECKS }
    record Open() implements CollectionCommand {}
    record Page(UUID snapshotId, PageKind kind, int index) implements CollectionCommand {}
    record ReadDeck(UUID id) implements CollectionCommand {}
    record Save(long expectedRevision, SavedDeck deck) implements CollectionCommand {}
    record Delete(long expectedRevision, UUID id) implements CollectionCommand {}
    record Activate(long expectedRevision, UUID id) implements CollectionCommand {}
    record ClearActive(long expectedRevision) implements CollectionCommand {}
    record Deposit(long expectedRevision, int code, int amount) implements CollectionCommand {}
    record Withdraw(long expectedRevision, int code, int amount) implements CollectionCommand {}
    record DepositAll(long expectedRevision) implements CollectionCommand {}
}
