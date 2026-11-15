package io.github.vay1314.updatesmanagerextendedmod;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Parses read-only package queries without assuming any user IDs. */
final class UserPackageCatalog {
    static final class Profile {
        final int id;
        String name;
        String type;
        boolean main;
        Profile(int id, String name, String type, boolean main) {
            this.id = id; this.name = name; this.type = type; this.main = main;
        }
        boolean cloneProfile() { return type.endsWith(".CLONE"); }
        boolean workProfile() { return type.endsWith(".MANAGED"); }
        boolean privateProfile() { return type.endsWith(".PRIVATE"); }
    }
    static final class Entry {
        final String packageName;
        final String apkPath;
        boolean system;
        final Set<Integer> users = new LinkedHashSet<>();
        Entry(String packageName, String apkPath) {
            this.packageName = packageName; this.apkPath = apkPath;
        }
    }
    final Map<Integer, Profile> profiles = new LinkedHashMap<>();
    final Map<String, Entry> packages = new LinkedHashMap<>();
    boolean complete = true;
    private static final Pattern VERBOSE = Pattern.compile(
            "id=(\\d+), name=(.*), type=([^,]+), flags=(.*)");
    private static final Pattern LEGACY = Pattern.compile(
            "UserInfo\\{(\\d+):(.*):([0-9a-fA-F]+)\\}");
    private static final Pattern PACKAGE = Pattern.compile("[A-Za-z0-9_]+(?:\\.[A-Za-z0-9_]+)*");

    static UserPackageCatalog parse(String output) {
        UserPackageCatalog result = new UserPackageCatalog();
        String section = "";
        int user = -1;
        Integer mainId = null;
        Set<Integer> completed = new LinkedHashSet<>();
        for (String raw : output.split("\n")) {
            String line = raw.trim();
            if (line.startsWith("@@")) {
                section = line;
                if (line.startsWith("@@PACKAGES:") || line.startsWith("@@SYSTEM:")) {
                    user = Integer.parseInt(line.substring(line.indexOf(':') + 1));
                } else if (line.startsWith("@@DONE:")) {
                    String[] parts = line.split(":");
                    if (parts.length == 4 && "0".equals(parts[2]) && "0".equals(parts[3])) {
                        completed.add(Integer.parseInt(parts[1]));
                    } else result.complete = false;
                }
                continue;
            }
            if (section.equals("@@MAIN") && line.matches("[0-9]+")) {
                mainId = Integer.parseInt(line);
            } else if (section.equals("@@USERS")) {
                Matcher m = VERBOSE.matcher(line);
                if (m.find()) {
                    int id = Integer.parseInt(m.group(1));
                    result.profiles.put(id, new Profile(id, m.group(2), m.group(3),
                            ("|" + m.group(4).split("\\s", 2)[0] + "|").contains("|MAIN|")));
                }
            } else if (section.equals("@@FALLBACK")) {
                Matcher m = LEGACY.matcher(line);
                if (m.find()) {
                    int id = Integer.parseInt(m.group(1));
                    long flags = Long.parseLong(m.group(3), 16);
                    if (!result.profiles.containsKey(id)) {
                        result.profiles.put(id, new Profile(id, m.group(2), "",
                                (flags & 0x4000) != 0 || (flags & 1) != 0));
                    }
                }
            } else if (line.startsWith("package:") && user >= 0) {
                String value = line.substring(8);
                if (section.startsWith("@@PACKAGES:")) {
                    int separator = value.lastIndexOf('=');
                    if (separator < 0) continue;
                    String name = value.substring(separator + 1);
                    String path = value.substring(0, separator);
                    if (!PACKAGE.matcher(name).matches() || !path.startsWith("/")) continue;
                    Entry entry = result.packages.get(name);
                    if (entry == null) {
                        entry = new Entry(name, path);
                        result.packages.put(name, entry);
                    }
                    entry.users.add(user);
                } else if (section.startsWith("@@SYSTEM:")) {
                    Entry entry = result.packages.get(value);
                    if (entry != null) entry.system = true;
                }
            }
        }
        if (mainId != null) {
            for (Profile p : result.profiles.values()) p.main = p.id == mainId;
        }
        if (result.profiles.isEmpty() || !completed.containsAll(result.profiles.keySet())) {
            result.complete = false;
        }
        return result;
    }

    List<Profile> profilesFor(Entry entry) {
        List<Profile> result = new ArrayList<>();
        for (int id : entry.users) {
            Profile p = profiles.get(id);
            if (p != null) result.add(p);
        }
        return result;
    }
}
