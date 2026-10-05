package mtx.v2;

import java.lang.ref.WeakReference;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Compatibility layer for MT269's two-pane async file loader.
 *
 * Invariants:
 *  - latest request wins per pane/controller;
 *  - stale success/error callbacks are ignored;
 *  - transient failures never erase an already-rendered pane;
 *  - ZIP/APK ordering is stable and does not replace the user's MT sort mode.
 */
public final class PanelBridge {
    private PanelBridge() {}

    private static final Object LOCK = new Object();
    private static final WeakHashMap<Object, WeakReference<Object>> LATEST = new WeakHashMap<>();
    private static final ConcurrentHashMap<Class<?>, Access> ACCESS = new ConcurrentHashMap<>();

    private static final class ZipPanelHolder {
        static final Class<?> TYPE = findZipPanelType();

        private static Class<?> findZipPanelType() {
            try {
                return Class.forName("l.ۢܳۜ", false, PanelBridge.class.getClassLoader());
            } catch (Throwable ignored) {
                return null;
            }
        }
    }

    private static final class Access {
        final Method name;
        final Method directory;

        Access(Class<?> c) throws ReflectiveOperationException {
            this.name = c.getMethod("getName");
            this.directory = c.getMethod("isDirectory");
        }

        String name(Object row) {
            try {
                Object v = name.invoke(row);
                return v instanceof String ? (String) v : null;
            } catch (Throwable ignored) {
                return null;
            }
        }

        boolean directory(Object row) {
            try {
                Object v = directory.invoke(row);
                return v instanceof Boolean && (Boolean) v;
            } catch (Throwable ignored) {
                return false;
            }
        }
    }

    public static void register(Object controller, Object task) {
        if (controller == null || task == null) return;
        synchronized (LOCK) {
            LATEST.put(controller, new WeakReference<>(task));
        }
    }

    public static boolean isCurrent(Object controller, Object task) {
        if (controller == null || task == null) return false;
        synchronized (LOCK) {
            WeakReference<Object> ref = LATEST.get(controller);
            return ref != null && ref.get() == task;
        }
    }

    /** Preserve the last good frame on a current-load failure. */
    public static void ignoreDestructiveClear(Object controller, Object emptyList, boolean animate) {
        // Intentionally no-op. The legacy callback still closes its loading/progress state.
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    public static List sortSnapshot(Object panel, List source) {
        if (source == null || source.size() < 2) return source;
        try {
            Class<?> zipPanel = ZipPanelHolder.TYPE;
            if (zipPanel == null || panel == null || !zipPanel.isInstance(panel)) return source;

            ArrayList copy = new ArrayList(source);
            Collections.sort(copy, STABLE_APK_ORDER);
            return copy;
        } catch (Throwable ignored) {
            return source;
        }
    }

    private static final Comparator<Object> STABLE_APK_ORDER = new Comparator<Object>() {
        @Override public int compare(Object a, Object b) {
            Row ra = row(a);
            Row rb = row(b);
            if (ra.parent != rb.parent) return ra.parent ? -1 : 1;

            int pa = rank(ra);
            int pb = rank(rb);
            if (pa != pb) return Integer.compare(pa, pb);

            if (pa == 2 && ra.dex != rb.dex) {
                return Integer.compare(ra.dex, rb.dex);
            }

            // Preserve MT's already-selected NAME/TIME/SIZE/TYPE ordering for peers.
            return 0;
        }
    };

    private static final class Row {
        final boolean parent;
        final boolean directory;
        final String name;
        final int dex;

        Row(boolean parent, boolean directory, String name, int dex) {
            this.parent = parent;
            this.directory = directory;
            this.name = name;
            this.dex = dex;
        }
    }

    private static Row row(Object value) {
        if (value == null) return new Row(false, false, null, Integer.MAX_VALUE);

        Access a = ACCESS.get(value.getClass());
        if (a == null) {
            try {
                a = new Access(value.getClass());
                Access old = ACCESS.putIfAbsent(value.getClass(), a);
                if (old != null) a = old;
            } catch (Throwable ignored) {
                return new Row(false, false, null, Integer.MAX_VALUE);
            }
        }

        String n = a.name(value);
        boolean d = a.directory(value);
        return new Row("..".equals(n), d, n, dexOrdinal(n));
    }

    private static int rank(Row row) {
        if (row.directory) return -1;
        if (row.name == null) return 4;

        String n = baseName(row.name).toLowerCase(Locale.US);
        if ("androidmanifest.xml".equals(n)) return 0;
        if ("resources.arsc".equals(n)) return 1;
        if (n.startsWith("classes") && n.endsWith(".dex")) return 2;
        return 3;
    }

    private static int dexOrdinal(String name) {
        if (name == null) return Integer.MAX_VALUE;
        String n = baseName(name).toLowerCase(Locale.US);
        if (!n.startsWith("classes") || !n.endsWith(".dex")) return Integer.MAX_VALUE;

        String mid = n.substring(7, n.length() - 4);
        if (mid.isEmpty()) return 1;

        try {
            int v = Integer.parseInt(mid);
            return v > 0 ? v : Integer.MAX_VALUE;
        } catch (NumberFormatException ignored) {
            return Integer.MAX_VALUE;
        }
    }

    private static String baseName(String name) {
        int slash = name.lastIndexOf('/');
        return slash >= 0 ? name.substring(slash + 1) : name;
    }
}
