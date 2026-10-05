package mt269.refactor;

/** Centralized Android 17 root-helper handshake policy. */
public final class RootBridgePolicy {
    private RootBridgePolicy() {}
    public static int pollTimeoutMs() { return 250; }
    public static int maxPolls() { return 40; }
    public static long localJoinMs() { return 2_000L; }
    public static long serverJoinMs() { return 12_000L; }
}
