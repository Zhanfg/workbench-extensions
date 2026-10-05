package mt269.refactor;

import java.util.Map;
import java.util.WeakHashMap;

/** Per-pane generation gate used by the APK compatibility layer. */
public final class PaneCommitGate {
    private static final Map<Object, Long> PANE_GENERATION = new WeakHashMap<>();
    private static final Map<Object, Ticket> LOAD_TICKETS = new WeakHashMap<>();

    private PaneCommitGate() {}

    public static synchronized void register(Object load, Object pane) {
        if (load == null || pane == null) return;
        long next = PANE_GENERATION.getOrDefault(pane, 0L) + 1L;
        PANE_GENERATION.put(pane, next);
        LOAD_TICKETS.put(load, new Ticket(pane, next));
    }

    public static synchronized boolean isCurrent(Object load, Object pane) {
        if (load == null || pane == null) return false;
        Ticket ticket = LOAD_TICKETS.get(load);
        if (ticket == null || ticket.pane != pane) return false;
        Long current = PANE_GENERATION.get(pane);
        return current != null && current.longValue() == ticket.generation;
    }

    public static synchronized long currentGeneration(Object pane) {
        Long value = PANE_GENERATION.get(pane);
        return value == null ? 0L : value.longValue();
    }

    private static final class Ticket {
        final Object pane;
        final long generation;
        Ticket(Object pane, long generation) {
            this.pane = pane;
            this.generation = generation;
        }
    }
}
