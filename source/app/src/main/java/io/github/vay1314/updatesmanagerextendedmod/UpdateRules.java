package io.github.vay1314.updatesmanagerextendedmod;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.List;
import java.util.Map;

/** Evaluates one immutable snapshot of the settings shared by the framework. */
final class UpdateRules {
    private static final Gson GSON = new Gson();
    private static final Type GROUPS_TYPE = new TypeToken<List<GroupInfo>>() {}.getType();

    static boolean shouldBlock(Map<String, ?> settings, String app, String source, long now) {
        if (app == null || app.isEmpty()) return false;
        Object status = settings.get("moduleStatus");
        Object resumeTime = settings.get("moduleStatusTime");
        if (!isEnabled(status, resumeTime instanceof Number
                ? ((Number) resumeTime).longValue() : null, now)) return false;

        Object json = settings.get("groups_list");
        if (!(json instanceof String)) return false;
        List<GroupInfo> groups = GSON.fromJson((String) json, GROUPS_TYPE);
        if (groups == null) return false;
        for (GroupInfo group : groups) {
            if (group != null && isEnabled(group.getStatus(), group.getStatusTime(), now)
                    && contains(group.getAppsToBlockUpdates(), app)
                    && (group.getBlockAllInstallationSources()
                    || contains(group.getInstallationSources(), source))) return true;
        }
        return false;
    }

    private static boolean isEnabled(Object status, Long resumeTime, long now) {
        return "enabled_immediately".equals(status)
                || ("enabled_since".equals(status) && resumeTime != null && now >= resumeTime);
    }

    private static boolean contains(String packages, String name) {
        if (packages == null || name == null || name.isEmpty()) return false;
        for (String entry : packages.split(",")) {
            if (name.equals(entry)) return true;
        }
        return false;
    }
}
