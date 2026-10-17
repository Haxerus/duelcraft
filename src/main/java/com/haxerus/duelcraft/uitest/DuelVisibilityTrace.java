package com.haxerus.duelcraft.uitest;

import com.haxerus.duelcraft.duel.message.*;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import java.nio.file.*;
import java.util.*;

/** Encoded snapshots keep mutable query records out of retained runtime evidence. */
public final class DuelVisibilityTrace {
    private final List<byte[]> packets = new ArrayList<>();
    public void add(DuelMessage message) {
        var buf = new FriendlyByteBuf(Unpooled.buffer());
        try { DuelMessageCodec.encode(buf, message); var bytes = new byte[buf.readableBytes()]; buf.readBytes(bytes); packets.add(bytes); }
        finally { buf.release(); }
    }
    public List<DuelMessage> messages() {
        return packets.stream().map(bytes -> {
            var buf = new FriendlyByteBuf(Unpooled.wrappedBuffer(bytes));
            try { return DuelMessageCodec.decode(buf); } finally { buf.release(); }
        }).toList();
    }
    public void save(String name) {
        try {
            var path = Path.of("build/evidence/visibility-runtime/" + name + ".txt");
            Files.createDirectories(path.getParent());
            var messages = messages(); var lines = new ArrayList<String>();
            for (int i = 0; i < packets.size(); i++) lines.add(i + " " + messages.get(i).getClass().getSimpleName()
                    + " " + HexFormat.of().formatHex(packets.get(i)));
            Files.write(path, lines);
        } catch (java.io.IOException e) { throw new IllegalStateException(e); }
    }
}
