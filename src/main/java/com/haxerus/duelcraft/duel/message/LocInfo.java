package com.haxerus.duelcraft.duel.message;

public record LocInfo(
    int controller, // uint8 - 0 or 1
    int location,   // uint8 - DECK, HAND, etc
    int sequence,   // uint32 - index within location
    int position    // uint32 - FACEUP_ATTACK, etc
) {
    public static LocInfo read(BufferReader reader) {
        return new LocInfo(
            reader.readUint8(),
            reader.readUint8(),
            reader.readInt32(),
            reader.readInt32()
        );
    }
}
