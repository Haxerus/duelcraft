package com.haxerus.duelcraft.client.carddata;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.function.LongBinaryOperator;
import java.util.function.LongUnaryOperator;

import static com.haxerus.duelcraft.core.OcgConstants.*;

/**
 * Client-side port of ygopro-core's {@code is_declarable}
 * ({@code native/ygopro-core/playerop.cpp:1004-1069}, mirrored by edopro's
 * {@code client_field.cpp:1252-1315}): the postfix stack machine that decides which cards
 * an {@code MSG_ANNOUNCE_CARD} prompt will accept.
 *
 * <p>Kept free of Minecraft and database types so it can be unit tested directly.
 */
public final class DeclarableFilter {

    // card.h:398-399 — the engine declares these two declarable whatever the alias/token rules say.
    private static final int CARD_MARINE_DOLPHIN = 78734254;
    private static final int CARD_TWINKLE_MOSS = 13857930;

    /**
     * The part of a {@code datas} row the machine reads.
     *
     * @param setcodes up to four 16-bit archetype codes packed into one 64-bit column
     */
    public record CardFacts(int code, int alias, long setcodes, int type, long race, int attribute) {}

    private DeclarableFilter() {}

    /**
     * Evaluate an {@code MSG_ANNOUNCE_CARD} opcode list against one card.
     * An empty or unbalanced list matches nothing — the engine requires exactly one
     * truthy value left on the stack.
     */
    public static boolean matches(List<Long> opcodes, CardFacts card) {
        Deque<Long> stack = new ArrayDeque<>();
        boolean allowAliases = false;
        boolean allowTokens = false;

        for (long opcode : opcodes) {
            if (opcode == OPCODE_ADD) binary(stack, (l, r) -> l + r);
            else if (opcode == OPCODE_SUB) binary(stack, (l, r) -> l - r);
            else if (opcode == OPCODE_MUL) binary(stack, (l, r) -> l * r);
            // The engine's `lhs / rhs` is undefined for rhs == 0; yield 0 instead of throwing.
            else if (opcode == OPCODE_DIV) binary(stack, (l, r) -> r == 0 ? 0 : l / r);
            else if (opcode == OPCODE_AND) binary(stack, (l, r) -> bool(l != 0 && r != 0));
            else if (opcode == OPCODE_OR) binary(stack, (l, r) -> bool(l != 0 || r != 0));
            else if (opcode == OPCODE_BAND) binary(stack, (l, r) -> l & r);
            else if (opcode == OPCODE_BOR) binary(stack, (l, r) -> l | r);
            else if (opcode == OPCODE_BXOR) binary(stack, (l, r) -> l ^ r);
            else if (opcode == OPCODE_LSHIFT) binary(stack, (l, r) -> l << r);
            else if (opcode == OPCODE_RSHIFT) binary(stack, (l, r) -> l >> r);
            else if (opcode == OPCODE_NEG) unary(stack, v -> -v);
            else if (opcode == OPCODE_NOT) unary(stack, v -> bool(v == 0));
            else if (opcode == OPCODE_BNOT) unary(stack, v -> ~v);
            else if (opcode == OPCODE_ISCODE) unary(stack, v -> bool(card.code() == (int) v));
            else if (opcode == OPCODE_ISSETCARD) unary(stack, v -> bool(isSetCard(card.setcodes(), (int) v)));
            else if (opcode == OPCODE_ISTYPE) unary(stack, v -> unsigned(card.type()) & v);
            else if (opcode == OPCODE_ISRACE) unary(stack, v -> card.race() & v);
            else if (opcode == OPCODE_ISATTRIBUTE) unary(stack, v -> unsigned(card.attribute()) & v);
            else if (opcode == OPCODE_GETCODE) stack.push(unsigned(card.code()));
            else if (opcode == OPCODE_GETTYPE) stack.push(unsigned(card.type()));
            else if (opcode == OPCODE_GETRACE) stack.push(card.race());
            else if (opcode == OPCODE_GETATTRIBUTE) stack.push(unsigned(card.attribute()));
            // OPCODE_GETSETCARD is commented out in the engine (playerop.cpp:1035), so it falls
            // through to the default and is pushed as a plain operand.
            else if (opcode == OPCODE_ALLOW_ALIASES) allowAliases = true;
            else if (opcode == OPCODE_ALLOW_TOKENS) allowTokens = true;
            else stack.push(opcode);
        }

        if (stack.size() != 1 || stack.peek() == 0L) return false;
        return card.code() == CARD_MARINE_DOLPHIN || card.code() == CARD_TWINKLE_MOSS
                || ((allowAliases || card.alias() == 0)
                    && (allowTokens || (card.type() & (TYPE_MONSTER | TYPE_TOKEN)) != (TYPE_MONSTER | TYPE_TOKEN)));
    }

    /**
     * The engine's {@code OPCODE_ISSETCARD} rule: the low 12 bits (the archetype) must match
     * exactly, and the requested sub-archetype nibble must be a subset of the card's.
     */
    private static boolean isSetCard(long packedSetcodes, int wanted) {
        int type = wanted & 0xfff;
        int subtype = wanted & 0xf000;
        for (int slot = 0; slot < 4; slot++) {
            int setcode = (int) ((packedSetcodes >>> (slot * 16)) & 0xffff);
            if (setcode == 0) continue;
            if ((setcode & 0xfff) == type && (setcode & 0xf000 & subtype) == subtype) return true;
        }
        return false;
    }

    private static void binary(Deque<Long> stack, LongBinaryOperator op) {
        if (stack.size() < 2) return;
        long rhs = stack.pop();
        long lhs = stack.pop();
        stack.push(op.applyAsLong(lhs, rhs));
    }

    private static void unary(Deque<Long> stack, LongUnaryOperator op) {
        if (stack.isEmpty()) return;
        stack.push(op.applyAsLong(stack.pop()));
    }

    /** Widen a C++ {@code uint32_t} field to the machine's signed 64-bit stack. */
    private static long unsigned(int value) {
        return value & 0xFFFFFFFFL;
    }

    private static long bool(boolean value) {
        return value ? 1 : 0;
    }
}
