package com.example.delaylauncherimprove;

import android.graphics.drawable.Drawable;

public class AppInfo {
    private final String label;
    private final String packageName;
    private final Drawable icon;

    public AppInfo(String label, String packageName, Drawable icon) {
        this.label = label;
        this.packageName = packageName;
        this.icon = icon;
    }

    public String getLabel() {
        return label;
    }

    public String getPackageName() {
        return packageName;
    }

    public Drawable getIcon() {
        return icon;
    }

    // Fondamentale: lo Spinner usa questo metodo per mostrare la stringa nel menu a tendina
    @Override
    public String toString() {
        return label;
    }
}
