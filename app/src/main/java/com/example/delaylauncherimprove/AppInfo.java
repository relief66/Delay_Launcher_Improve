package com.example.delaylauncherimprove;

import android.graphics.drawable.Drawable;

public class AppInfo {
    private String label;
    private String packageName;
    private Drawable icon;

    // Costruttore completo (con Icona)
    public AppInfo(String label, String packageName, Drawable icon) {
        this.label = label;
        this.packageName = packageName;
        this.icon = icon;
    }

    // Costruttore leggero (senza Icona - serve per evitare l'errore del compilatore)
    public AppInfo(String label, String packageName) {
        this.label = label;
        this.packageName = packageName;
        this.icon = null;
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
}
