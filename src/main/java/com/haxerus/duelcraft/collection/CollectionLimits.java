package com.haxerus.duelcraft.collection;

public final class CollectionLimits {
    public static final int SCHEMA_VERSION = 1;
    public static final int NAME_LENGTH = 128;
    public static final int DRAFT_CARDS = 512;
    public static final int COUNT_PAGE = 256;
    public static final int SUMMARY_PAGE = 32;
    public static final int PACKET_BYTES = 24576;
    public static final int ISSUES = 64;
    public static final int ISSUE_KEY_LENGTH = 128;
    public static final long SNAPSHOT_IDLE_MS = 30000;

    private CollectionLimits() {}
}
