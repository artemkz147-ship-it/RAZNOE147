package ru.forma365.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;

public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || !Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) return;
        SharedPreferences p = context.getSharedPreferences("forma365_native", Context.MODE_PRIVATE);
        if (!p.getBoolean("reminder_enabled", false)) return;
        int hour = p.getInt("reminder_hour", 20);
        int minute = p.getInt("reminder_minute", 30);
        ReminderScheduler.schedule(context, hour, minute);
    }
}
