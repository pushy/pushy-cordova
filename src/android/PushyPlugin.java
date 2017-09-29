package me.pushy.sdk.cordova.internal;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.util.Log;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaInterface;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.CordovaWebView;
import org.apache.cordova.PluginResult;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.lang.reflect.Method;

import me.pushy.sdk.Pushy;
import me.pushy.sdk.config.PushyLogging;
import me.pushy.sdk.cordova.internal.util.PushyPersistence;
import me.pushy.sdk.util.exceptions.PushyException;

public class PushyPlugin extends CordovaPlugin {
    private static PushyPlugin mInstance;
    private CallbackContext mNotificationHandler;

    @Override
    public void initialize(CordovaInterface cordova, CordovaWebView webView) {
        super.initialize(cordova, webView);

        // Store plugin instance
        mInstance = this;
    }

    @Override
    public boolean execute(final String action, final JSONArray args, final CallbackContext callbackContext) throws JSONException {
        // Run all plugin actions in background thread
        cordova.getThreadPool().execute(new Runnable() {
            @Override
            public void run() {
                // Restart the socket service
                if (action.equals("listen")) {
                    Pushy.listen(cordova.getActivity());
                }

                // Register devices
                if (action.equals("register")) {
                    register(callbackContext);
                }

                // Listen for notifications
                if (action.equals("setNotificationListener")) {
                    setNotificationListener(callbackContext);
                }

                // Request WRITE_EXTERNAL_STORAGE permission
                if (action.equals("requestStoragePermission")) {
                    requestStoragePermission();
                }

                // Check if device is registered
                if (action.equals("isRegistered")) {
                    isRegistered(callbackContext);
                }

                // Subscribe device to topic
                if (action.equals("subscribe")) {
                    subscribe(args, callbackContext);
                }

                // Unsubscribe device from topic
                if (action.equals("unsubscribe")) {
                    unsubscribe(args, callbackContext);
                }
            }
        });

        // Always return true regardless of action validity
        return true;
    }

    private void setNotificationListener(CallbackContext callbackContext) {
        // Save notification listener callback for later
        mNotificationHandler = callbackContext;

        // Attempt to deliver any pending notifications
        deliverPendingNotifications();
    }

    private void deliverPendingNotifications() {
        // Activity must be running for this to work
        if (!isActivityRunning()) {
            return;
        }

        // Get pending notifications
        JSONArray notifications = PushyPersistence.getPendingNotifications(cordova.getActivity());

        // Got at least one?
        if (notifications.length() > 0) {
            // Traverse notifications
            for (int i = 0; i < notifications.length(); i++) {
                try {
                    // Emit notification to listener
                    onNotificationReceived(notifications.getJSONObject(i), cordova.getActivity());
                }
                catch (JSONException e) {
                    // Log error to logcat
                    Log.e(PushyLogging.TAG, "Failed to parse JSON object:" + e.getMessage(), e);
                }
            }

            // Clear persisted notifications
            PushyPersistence.clearPendingNotifications(cordova.getActivity());
        }
    }

    private void requestStoragePermission() {
        // Request permission method
        Method requestPermission;

        try {
            // Get method reference via reflection (to support Cordova Android 4.0)
            requestPermission = CordovaInterface.class.getMethod("requestPermission", CordovaPlugin.class, int.class, String.class);

            // Request the permission via user-friendly dialog
            requestPermission.invoke(cordova, this, 0, Manifest.permission.WRITE_EXTERNAL_STORAGE);
        } catch (Exception e) {
            // Log error
            Log.d(PushyLogging.TAG, "Failed to request WRITE_EXTERNAL_STORAGE permission", e);
        }
    }

    private boolean isActivityRunning() {
        // Cache activity object
        Activity activity = cordova.getActivity();

        // Check whether activity exists and is not finishing up or destroyed
        return activity != null && ! activity.isFinishing();
    }

    public static void onNotificationReceived(JSONObject notification, Context context) {
        // Activity is not running or no notification handler defined?
        if (mInstance == null || !mInstance.isActivityRunning() || mInstance.mNotificationHandler == null) {
            // Store notification JSON in SharedPreferences and deliver it when app is opened
            PushyPersistence.persistNotification(notification, context);
            return;
        }

        // We're live, prepare a plugin result object that allows invoking the notification listener multiple times
        PluginResult pluginResult = new PluginResult(PluginResult.Status.OK, notification);

        // Keep the callback valid for future use
        pluginResult.setKeepCallback(true);

        // Invoke the JavaScript callback
        mInstance.mNotificationHandler.sendPluginResult(pluginResult);
    }

    private void register(final CallbackContext callback) {
        try {
            // Assign a unique token to this device
            String deviceToken = Pushy.register(cordova.getActivity());

            // Resolve the callback with the token
            callback.success(deviceToken);
        }
        catch (PushyException exc) {
            // Reject the callback with the exception
            callback.error(exc.getMessage());
        }
    }

    private void isRegistered(CallbackContext callback) {
        // Resolve the callback with boolean result
        callback.sendPluginResult(new PluginResult(PluginResult.Status.OK, Pushy.isRegistered(cordova.getActivity())));
    }

    private void subscribe(JSONArray args, CallbackContext callback) {
        try {
            // Attempt to subscribe the device to topic
            Pushy.subscribe(args.getString(0), cordova.getActivity());

            // Resolve the callback with success
            callback.success();
        }
        catch (Exception exc) {
            // Reject the callback with the exception
            callback.error(exc.getMessage());
        }
    }

    private void unsubscribe(JSONArray args, CallbackContext callback) {
        try {
            // Attempt to unsubscribe the device from topic
            Pushy.unsubscribe(args.getString(0), cordova.getActivity());

            // Resolve the callback with success
            callback.success();
        }
        catch (Exception exc) {
            // Reject the callback with the exception
            callback.error(exc.getMessage());
        }
    }
}
