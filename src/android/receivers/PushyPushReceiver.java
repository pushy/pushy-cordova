package me.pushy.sdk.cordova.internal.receivers;

import android.content.Intent;
import android.content.Context;
import android.content.BroadcastReceiver;
import me.pushy.sdk.cordova.internal.PushyPlugin;
import me.pushy.sdk.cordova.internal.util.PushyPersistence;

public class PushyPushReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        // Invoke the notification received handler
        PushyPlugin.onNotificationReceived(PushyPersistence.getJSONObjectFromIntentExtras(intent), context);
    }
}