package com.haxerus.duelcraft.duel.message;

/**
 * A parsed duel message paired with its raw bytes from the message buffer.
 * The parsed {@link DuelMessage} is used for flow control and game logic.
 * The raw bytes are used for network forwarding to clients.
 */
public record ParsedEntry(DuelMessage message, byte[] raw) {}
