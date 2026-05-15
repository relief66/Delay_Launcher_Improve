package com.example.delaylauncherimprove;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;

public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            SharedPreferences prefs = context.getSharedPreferences("DelayLauncherPrefs", Context.MODE_PRIVATE);
            String launcherPkg = prefs.getString("launcher_package", "");
            
            if (!launcherPkg.isEmpty()) {
                Intent i = new Intent(context, CountdownActivity.class);
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                i.putExtra("auto_start", true);
                context.startActivity(i);
            }
        }
    }
}
