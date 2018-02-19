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

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

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

        // Device is Android O or newer?
        if (Build.VERSION.SDK_INT >= 26) {
            configureNotificationChannel(builder, notificationManager);
        }

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

    private void configureNotificationChannel(Notification.Builder builder, NotificationManager notificationManager) {
        // Channel details
        String channelId = "pushy";
        String channelName = "Push Notifications";

        // Channel importance (4 means high importance)
        int channelImportance = 4;

        try {
            // Get NotificationChannel class via reflection (only available on API level 26+)
            Class notificationChannelClass = Class.forName("android.app.NotificationChannel");

            // Get NotificationChannel constructor
            Constructor<?> notificationChannelConstructor = notificationChannelClass.getDeclaredConstructor(String.class, CharSequence.class, int.class);

            // Instantiate new notification channel
            Object notificationChannel = notificationChannelConstructor.newInstance(channelId, channelName, channelImportance);

            // Get notification channel creation method via reflection
            Method createNotificationChannelMethod = notificationManager.getClass().getDeclaredMethod("createNotificationChannel", notificationChannelClass);

            // Invoke method on NotificationManager, passing in the channel object
            createNotificationChannelMethod.invoke(notificationManager, notificationChannel);

            // Get "setChannelId" method for Notification.Builder (only for AppCompat v26+)
            Method setChannelIdMethod = builder.getClass().getDeclaredMethod("setChannelId", String.class);

            // Invoke method, passing in the channel ID
            setChannelIdMethod.invoke(builder, channelId);

            // Log success to console
            Log.d("Pushy", "Notification channel set successfully");
        } catch (Exception exc) {
            // Log exception to console
            Log.e("Pushy", "Creating notification channel failed", exc);
        }
    }
}