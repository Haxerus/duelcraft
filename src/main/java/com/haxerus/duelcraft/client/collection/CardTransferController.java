package com.haxerus.duelcraft.client.collection;

import com.haxerus.duelcraft.collection.CollectionCommand;

/** Transfer form state; the shared list controller owns pending operations and authoritative refresh. */
public final class CardTransferController {
    private final SavedDeckController lists;
    private int amount = 1;

    public CardTransferController(SavedDeckController lists) { this.lists = lists; }

    public void amount(String value) {
        try { amount = Integer.parseInt(value); }
        catch (NumberFormatException exception) { amount = 0; }
    }
    public boolean valid() { return amount >= 1 && amount <= 4096; }
    public void deposit(int code) {
        if (valid() && code > 0) lists.transfer(new CollectionCommand.Deposit(lists.revision(), code, amount));
    }
    public void withdraw(int code) {
        if (valid() && code > 0) lists.transfer(new CollectionCommand.Withdraw(lists.revision(), code, amount));
    }
    public void depositAll() { lists.transfer(new CollectionCommand.DepositAll(lists.revision())); }
}
