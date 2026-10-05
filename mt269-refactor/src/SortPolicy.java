package mt269.refactor;

import java.util.Comparator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class SortPolicy {
    public enum Mode { NAME, TIME, SIZE, TYPE }

    public interface Row {
        String name();
        boolean directory();
    }

    private static final Pattern CLASSES = Pattern.compile("^classes(?:(\\d+))?\\.dex$", Pattern.CASE_INSENSITIVE);

    private SortPolicy() {}

    /**
     * Secondary APK priority only. Returning 0 is intentional: the primary MT
     * comparator/order remains authoritative for rows in the same priority group.
     */
    public static Comparator<Row> apkSecondary(Mode mode) {
        return (a, b) -> {
            if (mode != Mode.NAME) return 0;
            int pa = priority(a), pb = priority(b);
            if (pa != pb) return Integer.compare(pa, pb);
            if (pa == 3) {
                int da = dexOrdinal(a.name()), db = dexOrdinal(b.name());
                if (da != db) return Integer.compare(da, db);
            }
            return 0;
        };
    }

    private static int priority(Row row) {
        String n = row.name();
        if (row.directory()) return 0;
        if ("AndroidManifest.xml".equalsIgnoreCase(n)) return 1;
        if ("resources.arsc".equalsIgnoreCase(n)) return 2;
        if (CLASSES.matcher(n).matches()) return 3;
        return 4;
    }

    private static int dexOrdinal(String name) {
        Matcher m = CLASSES.matcher(name);
        if (!m.matches()) return Integer.MAX_VALUE;
        String group = m.group(1);
        return group == null ? 1 : Integer.parseInt(group);
    }
}
