package com.haxerus.duelcraft.duel.message;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class BufferReader {
    private final ByteBuffer buf;
    private int pos;

    public BufferReader(byte[] data) {
        this.buf = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);
    }

    public int readUint8() { return Byte.toUnsignedInt(buf.get()); }
    public int readUint16() { return Short.toUnsignedInt(buf.getShort()); }
    public int readInt32() { return buf.getInt(); }
    public long readUint32() { return Integer.toUnsignedLong(buf.getInt()); }
    public long readInt64() { return buf.getLong(); }
    public int remaining() { return buf.remaining(); }
    public void skip(int n) { buf.position(buf.position() + n); }
}
