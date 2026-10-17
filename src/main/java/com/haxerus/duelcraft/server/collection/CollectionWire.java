package com.haxerus.duelcraft.server.collection;

import com.haxerus.duelcraft.collection.*;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import java.util.*;
import java.util.function.Consumer;

/** Explicit record tags and allocation bounds shared by the two private envelopes. */
final class CollectionWire {
    private CollectionWire() {}

    static void encodeRequest(ByteBuf buffer, CollectionRequestPayload payload) {
        encode(buffer, buf -> {
            buf.writeUUID(payload.requestId());
            switch (payload.command()) {
                case CollectionCommand.Open ignored -> buf.writeByte(0);
                case CollectionCommand.Page page -> { buf.writeByte(1); buf.writeUUID(page.snapshotId()); buf.writeByte(page.kind().ordinal()); number(buf, page.index()); }
                case CollectionCommand.ReadDeck deck -> { buf.writeByte(2); buf.writeUUID(deck.id()); }
                case CollectionCommand.Save save -> { buf.writeByte(3); revision(buf, save.expectedRevision()); deck(buf, save.deck()); }
                case CollectionCommand.Delete delete -> { buf.writeByte(4); revision(buf, delete.expectedRevision()); buf.writeUUID(delete.id()); }
                case CollectionCommand.Activate activate -> { buf.writeByte(5); revision(buf, activate.expectedRevision()); buf.writeUUID(activate.id()); }
                case CollectionCommand.ClearActive clear -> { buf.writeByte(6); revision(buf, clear.expectedRevision()); }
                case CollectionCommand.Deposit deposit -> { buf.writeByte(7); revision(buf, deposit.expectedRevision()); positive(buf, deposit.code()); amount(buf, deposit.amount()); }
                case CollectionCommand.Withdraw withdraw -> { buf.writeByte(8); revision(buf, withdraw.expectedRevision()); positive(buf, withdraw.code()); amount(buf, withdraw.amount()); }
                case CollectionCommand.DepositAll all -> { buf.writeByte(9); revision(buf, all.expectedRevision()); }
            }
        });
    }

    static CollectionRequestPayload decodeRequest(ByteBuf buffer) {
        var buf = input(buffer); var id = buf.readUUID();
        CollectionCommand command = switch (buf.readUnsignedByte()) {
            case 0 -> new CollectionCommand.Open();
            case 1 -> new CollectionCommand.Page(buf.readUUID(), tag(buf, CollectionCommand.PageKind.values()), number(buf));
            case 2 -> new CollectionCommand.ReadDeck(buf.readUUID());
            case 3 -> new CollectionCommand.Save(revision(buf), deck(buf));
            case 4 -> new CollectionCommand.Delete(revision(buf), buf.readUUID());
            case 5 -> new CollectionCommand.Activate(revision(buf), buf.readUUID());
            case 6 -> new CollectionCommand.ClearActive(revision(buf));
            case 7 -> new CollectionCommand.Deposit(revision(buf), positive(buf), amount(buf));
            case 8 -> new CollectionCommand.Withdraw(revision(buf), positive(buf), amount(buf));
            case 9 -> new CollectionCommand.DepositAll(revision(buf));
            default -> throw invalid("Unknown collection command");
        };
        complete(buf); return new CollectionRequestPayload(id, command);
    }

    static void encodeReply(ByteBuf buffer, CollectionReplyPayload payload) {
        encode(buffer, buf -> {
            buf.writeUUID(payload.requestId());
            switch (payload.reply()) {
                case CollectionReply.Opened opened -> {
                    buf.writeByte(0); buf.writeUUID(opened.snapshotId()); revision(buf, opened.revision());
                    number(buf, opened.countPages()); number(buf, opened.deckPages()); optionalId(buf, opened.activeId());
                    buf.writeBoolean(opened.ownershipRequired());
                    buf.writeBoolean(opened.clearedActivation() != null);
                    if (opened.clearedActivation() != null) report(buf, opened.clearedActivation());
                }
                case CollectionReply.Counts counts -> {
                    buf.writeByte(1); header(buf, counts.snapshotId(), counts.revision(), counts.index());
                    size(buf, counts.entries().size(), CollectionLimits.COUNT_PAGE);
                    for (var entry : counts.entries().entrySet()) { positive(buf, entry.getKey()); positiveLong(buf, entry.getValue()); }
                }
                case CollectionReply.Decks decks -> {
                    buf.writeByte(2); header(buf, decks.snapshotId(), decks.revision(), decks.index());
                    size(buf, decks.entries().size(), CollectionLimits.SUMMARY_PAGE); var ids = new HashSet<UUID>();
                    for (var entry : decks.entries()) {
                        if (!ids.add(entry.id())) throw invalid("Duplicate deck ID");
                        buf.writeUUID(entry.id()); name(buf, entry.name());
                        summarySizes(entry.main(), entry.extra(), entry.side()); number(buf, entry.main()); number(buf, entry.extra()); number(buf, entry.side());
                    }
                }
                case CollectionReply.Deck reply -> { buf.writeByte(3); revision(buf, reply.revision()); deck(buf, reply.deck()); }
                case CollectionReply.Changed changed -> {
                    buf.writeByte(4); revision(buf, changed.revision()); optionalId(buf, changed.activeId());
                    buf.writeBoolean(changed.saved() != null); if (changed.saved() != null) deck(buf, changed.saved());
                    size(buf, changed.transferred(), 4096); size(buf, changed.skipped(), 4096); report(buf, changed.eligibility());
                }
                case CollectionReply.Rejected rejected -> {
                    buf.writeByte(5); if (rejected.error() == CollectionError.NONE) throw invalid("Rejection requires an error");
                    buf.writeByte(rejected.error().ordinal()); revision(buf, rejected.revision()); report(buf, rejected.eligibility());
                }
            }
        });
    }

    static CollectionReplyPayload decodeReply(ByteBuf buffer) {
        var buf = input(buffer); var id = buf.readUUID();
        CollectionReply reply = switch (buf.readUnsignedByte()) {
            case 0 -> {
                var snapshotId = buf.readUUID(); long revision = revision(buf);
                int countPages = number(buf), deckPages = number(buf); var activeId = optionalId(buf);
                boolean ownershipRequired = buf.readBoolean();
                var clearedActivation = buf.readBoolean() ? report(buf) : null;
                yield new CollectionReply.Opened(snapshotId, revision, countPages, deckPages, activeId,
                        ownershipRequired, clearedActivation);
            }
            case 1 -> {
                var snapshot = buf.readUUID(); long revision = revision(buf); int index = number(buf);
                int size = size(buf, CollectionLimits.COUNT_PAGE); var entries = new LinkedHashMap<Integer, Long>();
                for (int i = 0; i < size; i++) if (entries.put(positive(buf), positiveLong(buf)) != null) throw invalid("Duplicate count key");
                yield new CollectionReply.Counts(snapshot, revision, index, entries);
            }
            case 2 -> {
                var snapshot = buf.readUUID(); long revision = revision(buf); int index = number(buf);
                int size = size(buf, CollectionLimits.SUMMARY_PAGE); var entries = new ArrayList<CollectionReply.Summary>(size); var ids = new HashSet<UUID>();
                for (int i = 0; i < size; i++) {
                    var deckId = buf.readUUID(); if (!ids.add(deckId)) throw invalid("Duplicate deck ID");
                    String name = name(buf); int main = number(buf), extra = number(buf), side = number(buf);
                    summarySizes(main, extra, side); entries.add(new CollectionReply.Summary(deckId, name, main, extra, side));
                }
                yield new CollectionReply.Decks(snapshot, revision, index, entries);
            }
            case 3 -> new CollectionReply.Deck(revision(buf), deck(buf));
            case 4 -> {
                long revision = revision(buf); var active = optionalId(buf); var saved = buf.readBoolean() ? deck(buf) : null;
                int transferred = size(buf, 4096), skipped = size(buf, 4096);
                yield new CollectionReply.Changed(revision, active, saved, transferred, skipped, report(buf));
            }
            case 5 -> {
                var error = tag(buf, CollectionError.values()); if (error == CollectionError.NONE) throw invalid("Rejection requires an error");
                yield new CollectionReply.Rejected(error, revision(buf), report(buf));
            }
            default -> throw invalid("Unknown collection reply");
        };
        complete(buf); return new CollectionReplyPayload(id, reply);
    }

    private static void encode(ByteBuf buffer, Consumer<FriendlyByteBuf> writer) {
        var buf = new FriendlyByteBuf(Unpooled.buffer());
        try {
            writer.accept(buf);
            if (buf.readableBytes() > CollectionLimits.PACKET_BYTES) throw invalid("Collection packet exceeds 24 KiB");
            buffer.writeBytes(buf);
        } finally { buf.release(); }
    }
    private static FriendlyByteBuf input(ByteBuf buffer) {
        if (buffer.readableBytes() > CollectionLimits.PACKET_BYTES) throw invalid("Collection packet exceeds 24 KiB");
        return new FriendlyByteBuf(buffer);
    }
    private static void complete(FriendlyByteBuf buf) { if (buf.isReadable()) throw invalid("Trailing collection packet bytes"); }
    private static void header(FriendlyByteBuf buf, UUID id, long revision, int index) { buf.writeUUID(id); revision(buf, revision); number(buf, index); }
    private static void optionalId(FriendlyByteBuf buf, UUID id) { buf.writeBoolean(id != null); if (id != null) buf.writeUUID(id); }
    private static UUID optionalId(FriendlyByteBuf buf) { return buf.readBoolean() ? buf.readUUID() : null; }
    private static <T> T tag(FriendlyByteBuf buf, T[] values) { int tag = buf.readUnsignedByte(); if (tag >= values.length) throw invalid("Unknown enum tag"); return values[tag]; }
    private static int size(FriendlyByteBuf buf, int limit) { int size = number(buf); if (size > limit) throw invalid("Collection size exceeds limit"); return size; }
    private static void size(FriendlyByteBuf buf, int size, int limit) { if (size > limit) throw invalid("Collection size exceeds limit"); number(buf, size); }
    private static int number(FriendlyByteBuf buf) { int number = buf.readVarInt(); if (number < 0) throw invalid("Negative collection value"); return number; }
    private static void number(FriendlyByteBuf buf, int number) { if (number < 0) throw invalid("Negative collection value"); buf.writeVarInt(number); }
    private static long revision(FriendlyByteBuf buf) { long revision = buf.readVarLong(); if (revision < 0) throw invalid("Negative revision"); return revision; }
    private static void revision(FriendlyByteBuf buf, long revision) { if (revision < 0) throw invalid("Negative revision"); buf.writeVarLong(revision); }
    private static int positive(FriendlyByteBuf buf) { int number = number(buf); if (number == 0) throw invalid("Passcode/count must be positive"); return number; }
    private static int amount(FriendlyByteBuf buf) { int value = positive(buf); if (value > 4096) throw invalid("Transfer amount exceeds 4096"); return value; }
    private static void amount(FriendlyByteBuf buf, int value) { if (value > 4096) throw invalid("Transfer amount exceeds 4096"); positive(buf, value); }
    private static void positive(FriendlyByteBuf buf, int number) { if (number <= 0) throw invalid("Passcode/count must be positive"); buf.writeVarInt(number); }
    private static long positiveLong(FriendlyByteBuf buf) { long value = revision(buf); if (value == 0) throw invalid("Count must be positive"); return value; }
    private static void positiveLong(FriendlyByteBuf buf, long value) { if (value <= 0) throw invalid("Count must be positive"); buf.writeVarLong(value); }
    private static void name(FriendlyByteBuf buf, String name) { validateName(name); buf.writeUtf(name, CollectionLimits.NAME_LENGTH); }
    private static String name(FriendlyByteBuf buf) { String name = buf.readUtf(CollectionLimits.NAME_LENGTH); validateName(name); return name; }
    private static void validateName(String name) {
        if (name.isBlank() || name.length() > CollectionLimits.NAME_LENGTH
                || name.codePoints().anyMatch(code -> Character.isISOControl(code)
                        || Character.getType(code) == Character.FORMAT
                        || Character.getType(code) == Character.SURROGATE
                        || Character.getType(code) == Character.UNASSIGNED
                        || code == 0x2028 || code == 0x2029)) {
            throw invalid("Deck name must contain 1-128 printable characters");
        }
    }
    private static void summarySizes(int main, int extra, int side) {
        if (main < 0 || extra < 0 || side < 0 || (long) main + extra + side > CollectionLimits.DRAFT_CARDS) throw invalid("Draft exceeds 512 cards");
    }
    private static void deck(FriendlyByteBuf buf, SavedDeck deck) {
        buf.writeUUID(deck.id()); name(buf, deck.name());
        summarySizes(deck.cards().main().size(), deck.cards().extra().size(), deck.cards().side().size());
        for (var section : List.of(deck.cards().main(), deck.cards().extra(), deck.cards().side())) {
            size(buf, section.size(), CollectionLimits.DRAFT_CARDS); for (int code : section) positive(buf, code);
        }
    }
    private static SavedDeck deck(FriendlyByteBuf buf) {
        var id = buf.readUUID(); var name = name(buf); int remaining = CollectionLimits.DRAFT_CARDS;
        var sections = new ArrayList<List<Integer>>(3);
        for (int section = 0; section < 3; section++) {
            int size = size(buf, remaining); remaining -= size;
            var cards = new ArrayList<Integer>(size); for (int i = 0; i < size; i++) cards.add(positive(buf)); sections.add(cards);
        }
        return new SavedDeck(id, name, new DeckList(sections.get(0), sections.get(1), sections.get(2)));
    }
    private static void issueKey(String key) {
        if (key.isEmpty() || key.length() > CollectionLimits.ISSUE_KEY_LENGTH || key.chars().anyMatch(c -> c < 32 || c > 126)) throw invalid("Invalid issue key");
    }
    private static void report(FriendlyByteBuf buf, DeckEligibility.Report report) {
        size(buf, report.problems().size(), CollectionLimits.ISSUES);
        for (var issue : report.problems()) {
            issueKey(issue.key()); buf.writeUtf(issue.key(), CollectionLimits.ISSUE_KEY_LENGTH);
            buf.writeInt(issue.code()); buf.writeInt(issue.actual()); buf.writeInt(issue.limit());
        }
        size(buf, report.missing().size(), CollectionLimits.DRAFT_CARDS);
        for (var entry : report.missing().entrySet()) { positive(buf, entry.getKey()); positive(buf, entry.getValue()); }
        buf.writeBoolean(report.moreProblems());
        buf.writeBoolean(report.ownershipRequired());
        buf.writeBoolean(report.restrictionReason() != null);
        if (report.restrictionReason() != null) {
            buf.writeUtf(report.restrictionReason(), DeckUsePolicy.MAX_RESTRICTION_REASON_LENGTH);
        }
    }
    private static DeckEligibility.Report report(FriendlyByteBuf buf) {
        int size = size(buf, CollectionLimits.ISSUES); var issues = new ArrayList<DeckEligibility.Issue>(size);
        for (int i = 0; i < size; i++) {
            String key = buf.readUtf(CollectionLimits.ISSUE_KEY_LENGTH); issueKey(key);
            issues.add(new DeckEligibility.Issue(key, buf.readInt(), buf.readInt(), buf.readInt()));
        }
        int missingSize = size(buf, CollectionLimits.DRAFT_CARDS); var missing = new HashMap<Integer, Integer>();
        for (int i = 0; i < missingSize; i++) if (missing.put(positive(buf), positive(buf)) != null) throw invalid("Duplicate missing key");
        boolean moreProblems = buf.readBoolean();
        boolean ownershipRequired = buf.readBoolean();
        String restrictionReason = buf.readBoolean()
                ? buf.readUtf(DeckUsePolicy.MAX_RESTRICTION_REASON_LENGTH) : null;
        return new DeckEligibility.Report(issues, missing, moreProblems, ownershipRequired, restrictionReason);
    }
    private static IllegalArgumentException invalid(String message) { return new IllegalArgumentException(message); }
}
