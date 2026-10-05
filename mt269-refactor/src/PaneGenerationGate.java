package mt269.refactor;

import java.util.concurrent.atomic.AtomicLong;

public final class PaneGenerationGate {
    public enum Pane { LEFT, RIGHT }

    private final AtomicLong left = new AtomicLong();
    private final AtomicLong right = new AtomicLong();

    public long begin(Pane pane) {
        return counter(pane).incrementAndGet();
    }

    public boolean mayCommit(Pane pane, long generation) {
        return generation == counter(pane).get();
    }

    public long current(Pane pane) {
        return counter(pane).get();
    }

    private AtomicLong counter(Pane pane) {
        return pane == Pane.LEFT ? left : right;
    }
}
