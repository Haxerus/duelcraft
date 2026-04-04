package com.haxerus.duelcraft.duel.message;

public class BufferReader {
    private final byte[] data;
    private int pos;

    public BufferReader(byte[] data) {
        this.data = data;
        pos = 0;
    }

    public int readUint8() {
        return data[pos++];
    }
}
