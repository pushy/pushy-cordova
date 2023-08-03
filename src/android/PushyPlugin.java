package me.pushy.sdk.cordova.internal;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
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
import me.pushy.sdk.cordova.internal.config.PushyIntentExtras;
import me.pushy.sdk.cordova.internal.util.PushyPersistence;
import me.pushy.sdk.util.PushyStringUtils;
import me.pushy.sdk.util.exceptions.PushyException;

public class PushyPlugin extends CordovaPlugin {
    private static PushyPlugin mInstance;
    private CallbackContext mNotificationHandler;
    private CallbackContext mNotificationClickHandler;

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

                // Listen for notifications received
                if (action.equals("setNotificationListener")) {
                    setNotificationListener(callbackContext);
                }

                // Listen for notifications clicked
                if (action.equals("setNotificationClickListener")) {
                    setNotificationClickListener(callbackContext);
                }

                // Check if device is registered
                if (action.equals("isRegistered")) {
                    isRegistered(callbackContext);
                }

                // Unregister a device
                if (action.equals("unregister")) {
                    Pushy.unregister(cordova.getActivity());
                }

                // Subscribe device to topic
                if (action.equals("subscribe")) {
                    subscribe(args, callbackContext);
                }

                // Unsubscribe device from topic
                if (action.equals("unsubscribe")) {
                    unsubscribe(args, callbackContext);
                }

                // Set Pushy App ID (override package name identification)
                if (action.equals("setAppId")) {
                    setAppId(args, callbackContext);
                }

                // Static IP / proxy support
                if (action.equals("setProxyEndpoint")) {
                    setProxyEndpoint(args, callbackContext);
                }

                // Pushy Enterprise support
                if (action.equals("setEnterpriseConfig")) {
                    setEnterpriseConfig(args, callbackContext);
                }

                // Pushy Enterprise custom certificate support
                if (action.equals("setEnterpriseCertificate")) {
                    setEnterpriseCertificate(args, callbackContext);
                }

                // Custom icon support
                if (action.equals("setNotificationIcon")) {
                    setNotificationIcon(args);
                }

                // Pushy FCM high-priority fallback support
                if (action.equals("toggleFCM")) {
                    toggleFCM(args, callbackContext);
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

    @Override
    public void onNewIntent(Intent intent) {
        super.onNewIntent(intent);

        // Handle notification click
        onNotificationClicked(intent);
    }

    private void setNotificationClickListener(CallbackContext callbackContext) {
        // Save notification click listener callback for later
        mNotificationClickHandler = callbackContext;

        // Attempt to check whether pending notifications
        Intent activityIntent = cordova.getActivity().getIntent();

        // Check whether notification was clicked
        if (activityIntent.getBooleanExtra(PushyIntentExtras.NOTIFICATION_CLICKED, false)) {
            onNotificationClicked(cordova.getActivity().getIntent());
        }
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

    public static void onNotificationClicked(Intent intent) {
        // Activity is not running or no notification click handler defined?
        if (mInstance == null || !mInstance.isActivityRunning() || mInstance.mNotificationClickHandler == null) {
            return;
        }

        // Not a Pushy notification?
        if (!intent.getBooleanExtra(PushyIntentExtras.NOTIFICATION_CLICKED, false) ) {
            return;
        }

        // Attempt to extract stringified JSON payload
        String payload = intent.getStringExtra(PushyIntentExtras.NOTIFICATION_PAYLOAD);

        // No payload?
        if (PushyStringUtils.stringIsNullOrEmpty(payload)) {
            return;
        }

        // Notification payload object
        JSONObject notification;

        try {
            // Gracefully attempt to parse it back into JSONObject
            notification = new JSONObject(payload);
        }
        catch (Exception e) {
            // Log error to logcat and stop execution
            Log.e(PushyLogging.TAG, "Failed to parse notification click data into JSONObject:" + e.getMessage(), e);
            return;
        }

        // We're live, prepare a plugin result object that allows invoking the notification click listener multiple times
        PluginResult pluginResult = new PluginResult(PluginResult.Status.OK, notification);

        // Keep the callback valid for future use
        pluginResult.setKeepCallback(true);

        // Invoke the JavaScript callback
        mInstance.mNotificationClickHandler.sendPluginResult(pluginResult);
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

    private void setAppId(JSONArray args, CallbackContext callback) {
        // No args?
        if (args.length() == 0) {
            // Clear Pushy App ID (identify by package name instead)
            Pushy.setAppId(null, cordova.getActivity());

            // Resolve the callback with success
            callback.success();
            return;
        }

        try {
            // Extract appId from arguments
            String appId = args.getString(0);

            // Cordova converts JavaScript null and undefined to "null" (String), convert back to null
            if (appId.equals("null")) {
                appId = null;
            }

            // Set Pushy App ID (override package name identification)
            Pushy.setAppId(appId, cordova.getActivity());

            // Resolve the callback with success
            callback.success();
        }
        catch (Exception exc) {
            // Reject the callback with the exception
            callback.error(exc.getMessage());
        }
    }

    private void setEnterpriseConfig(JSONArray args, CallbackContext callback) {
        try {
            // Default to null
            String apiEndpoint = null, mqttEndpoint = null;

            // Non-null, non-empty strings provided?
            if (args.length() > 0 && !args.getString(0).equals("null") && !args.getString(0).trim().equals("")) {
                apiEndpoint = args.getString(0);
                mqttEndpoint = args.getString(1);
            }
                
            // Attempt to set Enterprise endpoints
            Pushy.setEnterpriseConfig(apiEndpoint, mqttEndpoint, cordova.getActivity());

            // Resolve the callback with success
            callback.success();
        }
        catch (Exception exc) {
            // Reject the callback with the exception
            callback.error(exc.getMessage());
        }
    }

    private void setProxyEndpoint(JSONArray args, CallbackContext callback) {
        try {
            // Default to null
            String endpoint = null;

            // Non-null, non-empty string provided?
            if (args.length() > 0 && !args.getString(0).equals("null") && !args.getString(0).trim().equals("")) {
                endpoint = args.getString(0);
            }

            // Attempt to set proxy endpoint
            Pushy.setProxyEndpoint(endpoint, cordova.getActivity());

            // Resolve the callback with success
            callback.success();
        }
        catch (Exception exc) {
            // Reject the callback with the exception
            callback.error(exc.getMessage());
        }
    }

    private void toggleFCM(JSONArray args, CallbackContext callback) {
        try {
            // Enable or disable FCM fallback support
            Pushy.toggleFCM(args.getBoolean(0), cordova.getActivity());

            // Resolve the callback with success
            callback.success();
        }
        catch (Exception exc) {
            // Reject the callback with the exception
            callback.error(exc.getMessage());
        }
    }

    private void setEnterpriseCertificate(JSONArray args, CallbackContext callback) {
        // Default to null
        String resourceName = null;

        try {
            // Attempt to extract certificate resource name from first parameter (may be null)
            resourceName = args.getString(0);

            // Cordova converts JavaScript null and undefined to "null" (String), convert back to null
            if (resourceName.equals("null")) {
                resourceName = null;
            }
        } catch (JSONException e) {
            // Null was passed in, disable the feature
        }

        // Attempt to set custom certificate resource name / disable the feature
        Pushy.setEnterpriseCertificate(resourceName, cordova.getActivity());

        // Resolve the callback with success
        callback.success();
    }

    private void setNotificationIcon(JSONArray args) {
        String iconResourceName;

        try {
            // Attempt to get icon resource name from first parameter
            iconResourceName = args.getString(0);
        } catch (JSONException e) {
            return;
        }

        // Store in SharedPreferences using PushyPersistence helper
        PushyPersistence.setNotificationIcon(iconResourceName, cordova.getActivity());
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
