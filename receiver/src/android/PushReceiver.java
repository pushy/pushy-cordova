package me.pushy.sdk;

import android.os.Build;
import android.app.Notification;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.Context;
import android.media.RingtoneManager;
import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.util.Log;

public class PushReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        // Notification title and text
        String notificationTitle = getAppName(context);
        String notificationText = "";

        // Attempt to extract the notification text from the "message" property of the data payload
        if (intent.getStringExtra("message") != null) {
            notificationText = intent.getStringExtra("message");
        }

        // Prepare a notification with vibration and sound
        Notification.Builder builder = new Notification.Builder(context)
                .setAutoCancel(true)
                .setContentTitle(notificationTitle)
                .setContentText(notificationText)
                .setVibrate(new long[]{0, 400, 250, 400})
                .setSmallIcon(context.getApplicationInfo().icon)
                .setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION))
                .setContentIntent(getMainActivityPendingIntent(context));

        // Get an instance of the NotificationManager service
        NotificationManager notificationManager = (NotificationManager) context.getSystemService(context.NOTIFICATION_SERVICE);

        // Automatically configure a Notification Channel for devices running Android O+
        Pushy.setNotificationChannel(builder, context);

        // Build the notification and display it
        notificationManager.notify(1, builder.build());
    }

    private static String getAppName(Context context) {
        // Attempt to determine app name via package manager
        return context.getPackageManager().getApplicationLabel(context.getApplicationInfo()).toString();
    }

    private PendingIntent getMainActivityPendingIntent(Context context) {
        // Get launcher activity intent
        Intent launchIntent = context.getPackageManager().getLaunchIntentForPackage(context.getApplicationContext().getPackageName());

        // Make sure to update the activity if it exists
        launchIntent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);

        // Convert intent into pending intent
        return PendingIntent.getActivity(context, 0, launchIntent, PendingIntent.FLAG_UPDATE_CURRENT);
    }
}