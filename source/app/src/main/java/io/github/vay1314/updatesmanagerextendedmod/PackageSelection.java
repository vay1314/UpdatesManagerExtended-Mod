package io.github.vay1314.updatesmanagerextendedmod;

import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

/** Saved rules survive missing packages and partial visibility into other profiles. */
final class PackageSelection {
    private PackageSelection() {}

    static Set<String> parse(String packages) {
        Set<String> result = new LinkedHashSet<>();
        if (packages != null) {
            for (String entry : packages.split(",")) {
                String name = entry.trim();
                if (!name.isEmpty()) result.add(name);
            }
        }
        return result;
    }

    static <T> void includeSaved(List<T> entries, Set<String> saved,
            Function<T, String> packageName, Function<String, T> resolve) {
        Set<String> listed = new HashSet<>();
        for (T entry : entries) listed.add(packageName.apply(entry));
        for (String name : saved) {
            if (!name.isEmpty() && listed.add(name)) entries.add(resolve.apply(name));
        }
    }
}
