package ru.forma365.app;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;

import java.util.Calendar;

public class ReminderReceiver extends BroadcastReceiver {
    private static final String CHANNEL_ID = "forma365_daily";

    @Override
    public void onReceive(Context context, Intent intent) {
        SharedPreferences p = context.getSharedPreferences("forma365_native", Context.MODE_PRIVATE);
        if (!p.getBoolean("reminder_enabled", false)) return;

        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager == null) return;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Ежедневный план Форма 365",
                    NotificationManager.IMPORTANCE_DEFAULT
            );
            channel.setDescription("Напоминание открыть план, отметить питание, шаги и тренировку");
            manager.createNotificationChannel(channel);
        }

        String gymDays = p.getString("gym_days", "1,3,5");
        int dow = Calendar.getInstance().get(Calendar.DAY_OF_WEEK);
        int mondayBased = dow == Calendar.SUNDAY ? 7 : dow - 1;
        boolean gym = containsDay(gymDays, mondayBased);

        Intent open = new Intent(context, MainActivity.class);
        open.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent content = PendingIntent.getActivity(
                context,
                366,
                open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        String text = gym
                ? "Сегодня зал. Открой тренировку, сделай план и запиши рабочие веса."
                : "Проверь питание, шаги, сон и отметь день — это займёт меньше минуты.";

        Notification.Builder builder;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            builder = new Notification.Builder(context, CHANNEL_ID);
        } else {
            builder = new Notification.Builder(context);
            builder.setPriority(Notification.PRIORITY_DEFAULT);
        }

        builder.setSmallIcon(R.drawable.ic_stat_fitness)
                .setContentTitle("Форма 365")
                .setContentText(text)
                .setStyle(new Notification.BigTextStyle().bigText(text))
                .setAutoCancel(true)
                .setContentIntent(content)
                .setWhen(System.currentTimeMillis());

        manager.notify(365, builder.build());
    }

    private boolean containsDay(String csv, int day) {
        if (csv == null) return false;
        for (String part : csv.split(",")) {
            try {
                if (Integer.parseInt(part.trim()) == day) return true;
            } catch (NumberFormatException ignored) {}
        }
        return false;
    }
}
