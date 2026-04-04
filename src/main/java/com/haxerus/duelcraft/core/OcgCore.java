package com.haxerus.duelcraft.core;

public final class OcgCore {

    static {
        NativeLoader.load();
    }

    // Engine lifecycle
    public static native long nCreateEngine(String[] dbPaths, String[] scriptPaths);
    public static native void nDestroyEngine(long engine);
    //public static native void nSetLogHandler(long engine, LogHandler handler);

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
