package com.haxerus.duelcraft.core;

import java.util.List;

public class DuelEngine implements AutoCloseable {
    private final long handle;

    public DuelEngine(List<String> dbPaths, List<String> scriptPaths) {
        this.handle = OcgCore.nCreateEngine(
                dbPaths.toArray(String[]::new),
                scriptPaths.toArray(String[]::new)
        );
        if (this.handle == 0) {
            throw new IllegalStateException("Failed to initialize DuelEngine");
        }
    }

    public long getHandle() { return handle; }

    @Override
    public void close() throws Exception {
        OcgCore.nDestroyEngine(handle);
    }
}
