package mt269.refactor;

import java.util.*;

public final class RegressionSuite {
    private static final class R implements SortPolicy.Row {
        final String n; final boolean d;
        R(String n, boolean d) { this.n=n; this.d=d; }
        public String name(){ return n; }
        public boolean directory(){ return d; }
        public String toString(){ return n; }
    }

    public static void main(String[] args) {
        testPaneGenerations();
        testRuntimePaneCommitGate();
        testRootHandshake();
        testRootPolicy();
        testSorting();
        System.out.println("MT269 regression suite: PASS");
    }

    private static void testPaneGenerations() {
        PaneGenerationGate gate = new PaneGenerationGate();
        long l1 = gate.begin(PaneGenerationGate.Pane.LEFT);
        long r1 = gate.begin(PaneGenerationGate.Pane.RIGHT);
        long l2 = gate.begin(PaneGenerationGate.Pane.LEFT);
        check(!gate.mayCommit(PaneGenerationGate.Pane.LEFT, l1), "stale left load committed");
        check(gate.mayCommit(PaneGenerationGate.Pane.LEFT, l2), "latest left load rejected");
        check(gate.mayCommit(PaneGenerationGate.Pane.RIGHT, r1), "right pane invalidated by left pane");
    }

    private static void testRuntimePaneCommitGate() {
        Object left = new Object();
        Object right = new Object();
        Object leftOld = new Object();
        Object rightOnly = new Object();
        Object leftNew = new Object();
        PaneCommitGate.register(leftOld, left);
        PaneCommitGate.register(rightOnly, right);
        PaneCommitGate.register(leftNew, left);
        check(!PaneCommitGate.isCurrent(leftOld, left), "old left loader committed");
        check(PaneCommitGate.isCurrent(leftNew, left), "new left loader rejected");
        check(PaneCommitGate.isCurrent(rightOnly, right), "left navigation invalidated right pane");
        check(!PaneCommitGate.isCurrent(leftNew, right), "loader crossed pane ownership");
    }

    private static void testRootHandshake() {
        RootHandshakeStateMachine sm =
            new RootHandshakeStateMachine(RootHandshakeStateMachine.Policy.android17Default());
        sm.begin(1_000);
        sm.helperStarting();
        sm.helperStarted();
        check(sm.nextPollTimeoutMs(1_000) == 250, "poll quantum");
        check(sm.onPollTimeout(1_250), "early poll timeout became fatal");
        check(sm.state() == RootHandshakeStateMachine.State.WAITING_FOR_CONNECT, "wrong wait state");
        sm.connected();
        check(sm.state() == RootHandshakeStateMachine.State.CONNECTED, "connect failed");

        RootHandshakeStateMachine retry =
            new RootHandshakeStateMachine(new RootHandshakeStateMachine.Policy(2_000, 200, 2));
        retry.begin(0);
        retry.helperStarting();
        retry.helperStarted();
        check(!retry.onPollTimeout(2_000), "deadline should end attempt");
        check(retry.state() == RootHandshakeStateMachine.State.RETRYING, "first deadline should retry");
        retry.begin(2_001);
        retry.helperStarting();
        retry.helperStarted();
        check(!retry.onPollTimeout(4_001), "second deadline should end attempt");
        check(retry.state() == RootHandshakeStateMachine.State.FAILED, "second deadline should fail");
    }

    private static void testRootPolicy() {
        check(RootBridgePolicy.pollTimeoutMs() == 250, "root poll policy");
        check(RootBridgePolicy.maxPolls() == 40, "root poll count");
        check((long) RootBridgePolicy.pollTimeoutMs() * RootBridgePolicy.maxPolls() == 10_000L, "root accept budget");
        check(RootBridgePolicy.localJoinMs() < RootBridgePolicy.serverJoinMs(), "local fallback must be earlier than TCP deadline");
        check(RootBridgePolicy.serverJoinMs() > 10_000L, "parent wait must cover accept budget");
    }

    private static void testSorting() {
        List<R> timeOrdered = new ArrayList<>(List.of(
            new R("z.bin", false),
            new R("a.bin", false),
            new R("classes10.dex", false),
            new R("classes2.dex", false)
        ));
        List<R> before = new ArrayList<>(timeOrdered);
        timeOrdered.sort(SortPolicy.apkSecondary(SortPolicy.Mode.TIME));
        check(timeOrdered.equals(before), "TIME order was overridden by APK secondary sort");

        List<R> nameMode = new ArrayList<>(List.of(
            new R("classes10.dex", false),
            new R("z.bin", false),
            new R("classes2.dex", false),
            new R("resources.arsc", false),
            new R("AndroidManifest.xml", false)
        ));
        nameMode.sort(SortPolicy.apkSecondary(SortPolicy.Mode.NAME));
        check(nameMode.get(0).n.equals("AndroidManifest.xml"), "manifest priority");
        check(nameMode.get(1).n.equals("resources.arsc"), "arsc priority");
        check(nameMode.get(2).n.equals("classes2.dex"), "classes2 ordinal");
        check(nameMode.get(3).n.equals("classes10.dex"), "classes10 ordinal");
        check(nameMode.get(4).n.equals("z.bin"), "ordinary row order");
    }

    private static void check(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
    }
}
