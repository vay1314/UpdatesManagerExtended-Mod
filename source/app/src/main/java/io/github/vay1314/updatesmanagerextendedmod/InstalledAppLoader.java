package io.github.vay1314.updatesmanagerextendedmod;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.util.Log;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class InstalledAppLoader {
    static final class Result {
        final List<AppInfo> apps;
        final boolean complete;
        Result(List<AppInfo> apps, boolean complete) { this.apps = apps; this.complete = complete; }
    }

    static Result load(Context context, boolean showSystem, Set<String> saved) {
        PackageManager pm = context.getPackageManager();
        Map<String, ApplicationInfo> local = new LinkedHashMap<>();
        for (ApplicationInfo app : pm.getInstalledApplications(0)) local.put(app.packageName, app);
        List<AppInfo> apps = new ArrayList<>();
        boolean complete = false;
        try {
            UserPackageCatalog catalog = RootPackageQuery.load();
            complete = catalog.complete;
            for (UserPackageCatalog.Entry entry : catalog.packages.values()) {
                if (!showSystem && entry.system && !saved.contains(entry.packageName)) continue;
                ApplicationInfo info = local.get(entry.packageName);
                if (info == null) {
                    try {
                        PackageInfo archive = pm.getPackageArchiveInfo(entry.apkPath, 0);
                        if (archive != null && archive.applicationInfo != null) {
                            info = archive.applicationInfo;
                            info.sourceDir = entry.apkPath;
                            info.publicSourceDir = entry.apkPath;
                        }
                    } catch (RuntimeException ignored) {
                        // A locked profile or OEM may deny APK resource access; the package is still selectable.
                    }
                }
                AppInfo app = describe(pm, entry.packageName, info);
                addProfileBadges(context, app, catalog.profilesFor(entry));
                apps.add(app);
            }
        } catch (IOException | RuntimeException e) {
            Log.w("UpdatesManagerExtended", "Cross-profile app query unavailable", e);
            addLocal(context, pm, local, showSystem, saved, apps);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            addLocal(context, pm, local, showSystem, saved, apps);
        }
        PackageSelection.includeSaved(apps, saved, AppInfo::getPackageName,
                name -> AppInfo.loadSaved(context, name));
        return new Result(apps, complete);
    }

    private static void addLocal(Context context, PackageManager pm, Map<String, ApplicationInfo> local,
            boolean showSystem, Set<String> saved, List<AppInfo> apps) {
        for (ApplicationInfo info : local.values()) {
            if (showSystem || (info.flags & ApplicationInfo.FLAG_SYSTEM) == 0 || saved.contains(info.packageName)) {
                AppInfo app = describe(pm, info.packageName, info);
                app.addProfileBadge(context.getString(R.string.profile_current),
                        context.getString(R.string.profile_current), R.drawable.ic_profile_person);
                apps.add(app);
            }
        }
    }

    private static AppInfo describe(PackageManager pm, String name, ApplicationInfo info) {
        if (info != null) {
            try { return new AppInfo(info.loadLabel(pm).toString(), name, info.loadIcon(pm)); }
            catch (RuntimeException ignored) { /* Keep the package editable even without resources. */ }
        }
        return new AppInfo(name, name, pm.getDefaultActivityIcon());
    }

    private static void addProfileBadges(Context context, AppInfo app,
            List<UserPackageCatalog.Profile> profiles) {
        // Put the main space first, preserving a separate badge for every other profile.
        List<UserPackageCatalog.Profile> ordered = new ArrayList<>(profiles);
        ordered.sort((a, b) -> Boolean.compare(b.main, a.main));
        for (UserPackageCatalog.Profile profile : ordered) {
            int icon = profile.cloneProfile() ? R.drawable.ic_profile_clone
                    : profile.workProfile() ? R.drawable.ic_profile_work
                    : profile.privateProfile() ? R.drawable.ic_profile_lock
                    : R.drawable.ic_profile_person;
            String label = profile.name + "(" + profile.id + ")";
            String description = profile.main ? context.getString(R.string.profile_main)
                    : profile.cloneProfile() ? context.getString(R.string.profile_clone)
                    : profile.workProfile() ? context.getString(R.string.profile_work, profile.name)
                    : profile.privateProfile() ? context.getString(R.string.profile_private, profile.name)
                    : context.getString(R.string.profile_other, profile.name);
            app.addProfileBadge(label, description + ", " + label, icon);
        }
    }
}
