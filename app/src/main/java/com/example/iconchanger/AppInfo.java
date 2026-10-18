package com.example.iconchanger;

import android.graphics.drawable.Drawable;

public class AppInfo {
    public final String packageName;
    public final String label;
    public final Drawable icon;

    public AppInfo(String packageName, String label, Drawable icon) {
        this.packageName = packageName;
        this.label = label;
        this.icon = icon;
    }
}
