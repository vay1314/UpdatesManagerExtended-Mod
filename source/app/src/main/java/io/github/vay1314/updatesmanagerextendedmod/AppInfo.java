package io.github.vay1314.updatesmanagerextendedmod;


import android.graphics.drawable.Drawable;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;

public class AppInfo {
    private String appName;
    private String packageName;
    private Drawable appIcon;

    public AppInfo(String appName, String packageName, Drawable appIcon) {
        this.appName = appName;
        this.packageName = packageName;
        this.appIcon = appIcon;
    }

    /** A package invisible here may still be installed in another user profile. */
    static AppInfo loadSaved(Context context, String packageName) {
        PackageManager pm = context.getPackageManager();
        try {
            ApplicationInfo app = pm.getApplicationInfo(packageName, 0);
            return new AppInfo(app.loadLabel(pm).toString(), packageName, app.loadIcon(pm));
        } catch (PackageManager.NameNotFoundException e) {
            return new AppInfo(context.getString(R.string.saved_package_unavailable),
                    packageName, pm.getDefaultActivityIcon());
        }
    }

    public String getAppName() {
        return appName;
    }

    public String getPackageName() {
        return packageName;
    }

    public Drawable getAppIcon() {
        return appIcon;
    }
}