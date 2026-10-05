package mt269.refactor;

import java.util.Objects;

public final class RootHandshakeStateMachine {
    public enum State {
        IDLE,
        SERVER_BOUND,
        HELPER_STARTING,
        WAITING_FOR_CONNECT,
        CONNECTED,
        RETRYING,
        FAILED,
        CANCELLED,
        CLOSED
    }

    public enum Failure {
        NONE,
        SERVER_BIND,
        HELPER_START,
        ACCEPT_DEADLINE,
        HELPER_EXIT,
        IO,
        CANCELLED
    }

    public static final class Policy {
        public final long overallDeadlineMs;
        public final long pollQuantumMs;
        public final int maxAttempts;

        public Policy(long overallDeadlineMs, long pollQuantumMs, int maxAttempts) {
            if (overallDeadlineMs < 1000) throw new IllegalArgumentException("deadline too short");
            if (pollQuantumMs < 50 || pollQuantumMs > overallDeadlineMs) {
                throw new IllegalArgumentException("invalid poll quantum");
            }
            if (maxAttempts < 1 || maxAttempts > 4) throw new IllegalArgumentException("invalid attempts");
            this.overallDeadlineMs = overallDeadlineMs;
            this.pollQuantumMs = pollQuantumMs;
            this.maxAttempts = maxAttempts;
        }

        public static Policy android17Default() {
            return new Policy(10_000, 250, 2);
        }
    }

    private final Policy policy;
    private State state = State.IDLE;
    private Failure failure = Failure.NONE;
    private int attempt;
    private long startedAtMs;
    private long deadlineAtMs;

    public RootHandshakeStateMachine(Policy policy) {
        this.policy = Objects.requireNonNull(policy);
    }

    public synchronized void begin(long nowMs) {
        require(State.IDLE, State.RETRYING);
        attempt++;
        startedAtMs = nowMs;
        deadlineAtMs = nowMs + policy.overallDeadlineMs;
        failure = Failure.NONE;
        state = State.SERVER_BOUND;
    }

    public synchronized void helperStarting() {
        require(State.SERVER_BOUND);
        state = State.HELPER_STARTING;
    }

    public synchronized void helperStarted() {
        require(State.HELPER_STARTING);
        state = State.WAITING_FOR_CONNECT;
    }

    public synchronized void connected() {
        require(State.WAITING_FOR_CONNECT, State.HELPER_STARTING);
        failure = Failure.NONE;
        state = State.CONNECTED;
    }

    /**
     * Called after a short socket poll timeout. A poll timeout is not fatal while
     * the overall connection deadline has not expired.
     */
    public synchronized boolean onPollTimeout(long nowMs) {
        require(State.WAITING_FOR_CONNECT);
        if (nowMs < deadlineAtMs) return true;
        if (attempt < policy.maxAttempts) {
            state = State.RETRYING;
            failure = Failure.ACCEPT_DEADLINE;
            return false;
        }
        state = State.FAILED;
        failure = Failure.ACCEPT_DEADLINE;
        return false;
    }

    public synchronized void helperFailed() {
        if (terminal()) return;
        failure = Failure.HELPER_EXIT;
        state = attempt < policy.maxAttempts ? State.RETRYING : State.FAILED;
    }

    public synchronized void ioFailed(Failure reason) {
        if (terminal()) return;
        failure = reason == null ? Failure.IO : reason;
        state = attempt < policy.maxAttempts ? State.RETRYING : State.FAILED;
    }

    public synchronized void cancel() {
        if (terminal()) return;
        failure = Failure.CANCELLED;
        state = State.CANCELLED;
    }

    public synchronized void close() {
        state = State.CLOSED;
    }

    public synchronized long nextPollTimeoutMs(long nowMs) {
        if (state != State.WAITING_FOR_CONNECT) return 0;
        long left = deadlineAtMs - nowMs;
        if (left <= 0) return 0;
        return Math.min(policy.pollQuantumMs, left);
    }

    public synchronized State state() { return state; }
    public synchronized Failure failure() { return failure; }
    public synchronized int attempt() { return attempt; }
    public synchronized long startedAtMs() { return startedAtMs; }
    public synchronized long deadlineAtMs() { return deadlineAtMs; }

    public synchronized boolean terminal() {
        return state == State.CONNECTED || state == State.FAILED ||
               state == State.CANCELLED || state == State.CLOSED;
    }

    private void require(State... allowed) {
        for (State value : allowed) if (state == value) return;
        throw new IllegalStateException("state=" + state);
    }
}
