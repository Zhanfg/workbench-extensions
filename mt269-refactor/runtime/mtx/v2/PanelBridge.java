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
 * Architectural invariants:
 *  - request ownership is isolated by (controller,pane), never globally;
 *  - latest request wins inside one pane only;
 *  - stale success/error callbacks are ignored;
 *  - transient failures never erase an already-rendered pane;
 *  - background tasks never mutate the list currently owned by the UI;
 *  - ZIP/APK ordering is stable and never re-sorts peer rows by name.
 */
public final class PanelBridge {
    private PanelBridge() {}

    private static final Object LOCK = new Object();

    private static final WeakHashMap<Object, WeakHashMap<Object, WeakReference<Object>>> LATEST =
            new WeakHashMap<Object, WeakHashMap<Object, WeakReference<Object>>>();

    private static final WeakHashMap<Object, Registration> REGISTRATIONS =
            new WeakHashMap<Object, Registration>();

    private static final ConcurrentHashMap<Class<?>, Access> ACCESS =
            new ConcurrentHashMap<Class<?>, Access>();

    private static final class Registration {
        final WeakReference<Object> controller;
        final WeakReference<Object> pane;

        Registration(Object controller, Object pane) {
            this.controller = new WeakReference<Object>(controller);
            this.pane = new WeakReference<Object>(pane);
        }
    }

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
                return v instanceof Boolean && ((Boolean) v).booleanValue();
            } catch (Throwable ignored) {
                return false;
            }
        }
    }

    public static void register(Object controller, Object pane, Object task) {
        if (controller == null || pane == null || task == null) return;
        synchronized (LOCK) {
            WeakHashMap<Object, WeakReference<Object>> perPane = LATEST.get(controller);
            if (perPane == null) {
                perPane = new WeakHashMap<Object, WeakReference<Object>>();
                LATEST.put(controller, perPane);
            }
            perPane.put(pane, new WeakReference<Object>(task));
            REGISTRATIONS.put(task, new Registration(controller, pane));
        }
    }

    public static boolean isCurrent(Object controller, Object task) {
        if (controller == null || task == null) return false;
        synchronized (LOCK) {
            Registration registration = REGISTRATIONS.get(task);
            if (registration == null) return false;

            Object registeredController = registration.controller.get();
            Object pane = registration.pane.get();
            if (registeredController != controller || pane == null) return false;

            WeakHashMap<Object, WeakReference<Object>> perPane = LATEST.get(controller);
            if (perPane == null) return false;

            WeakReference<Object> ref = perPane.get(pane);
            return ref != null && ref.get() == task;
        }
    }

    public static void ignoreDestructiveClear(Object controller, Object emptyList, boolean animate) {
        // Preserve the last successfully rendered pane.
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

            // TimSort is stable: peers keep MT's primary NAME/TIME/SIZE/TYPE order.
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
        if (mid.length() == 0) return 1;

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
