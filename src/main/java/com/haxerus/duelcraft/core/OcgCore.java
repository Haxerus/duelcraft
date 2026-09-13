package com.haxerus.duelcraft.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.Consumer;

public final class OcgCore {

    private static final Logger LOGGER = LoggerFactory.getLogger("duelcraft-native");

    /** The bridge's own log channel for recoverable data problems (see java_log.h). */
    private static final int LOG_TYPE_BRIDGE_WARN = 4;

    private static volatile String currentDuel;
    private static volatile Consumer<String> logListener;

    static {
        NativeLoader.load();
    }

    /**
     * Called by the native bridge for every ygopro-core log message and for the bridge's own
     * diagnostics. {@code type} is an {@code OCG_LogTypes} value, or {@link #LOG_TYPE_BRIDGE_WARN}.
     */
    public static void onNativeLog(int type, String message) {
        String duel = currentDuel;
        String line = duel == null ? message : "[" + duel + "] " + message;
        Consumer<String> listener = logListener;
        if (listener != null) {
            listener.accept(line);
        }
        switch (type) {
            case 0 -> LOGGER.error(line);               // OCG_LOG_TYPE_ERROR
            case 1 -> LOGGER.warn("[script] " + line);  // OCG_LOG_TYPE_FROM_SCRIPT
            case 2 -> LOGGER.debug(line);               // OCG_LOG_TYPE_FOR_DEBUG
            case LOG_TYPE_BRIDGE_WARN -> LOGGER.warn(line);
            default -> LOGGER.info(line);
        }
    }

    /** Tags native log lines with the duel that produced them. {@code null} clears the tag. */
    public static void setCurrentDuel(String duel) {
        currentDuel = duel;
    }

    /** Test hook: receives every formatted native log line. {@code null} removes the listener. */
    public static void setLogListener(Consumer<String> listener) {
        logListener = listener;
    }

    // Engine lifecycle
    public static native long nCreateEngine(String[] dbPaths, String[] scriptPaths);
    public static native void nDestroyEngine(long engine);

    // Duel lifecycle
    public static native long nCreateDuel(long engine, long[] seed, long flags,
                                          int team1LP, int team1StartHand, int team1DrawPerTurn,
                                          int team2LP, int team2StartHand, int team2DrawPerTurn);
    public static native void nDestroyDuel(long engine, long duel);
    public static native void nDuelNewCard(long engine, long duel,
                                           int team, int duelist, int code, int controller,
                                           int location, int sequence, int position);
    public static native void nStartDuel(long engine, long duel);

    // Processing
    public static native int nDuelProcess(long engine, long duel);
    public static native byte[] nDuelGetMessage(long engine, long duel);
    public static native void nDuelSetResponse(long engine, long duel, byte[] response);

    // Querying
    public static native int nDuelQueryCount(long engine, long duel, int team, int location);
    public static native byte[] nDuelQuery(long engine, long duel,
                                           int flags, int controller, int location, int sequence, int overlaySequence);
    public static native byte[] nDuelQueryLocation(long engine, long duel,
                                                   int flags, int controller, int location);
    public static native byte[] nDuelQueryField(long engine, long duel);

    // Info
    public static native int[] nGetVersion();

}
