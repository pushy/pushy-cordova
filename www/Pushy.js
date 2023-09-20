var cordova = require('cordova');

// Native class names by platform
var nativeClassNames = {
    android: 'Pushy',
    ios: 'PushyPlugin'
};

// Native action names
var actions = [
    {
        name: 'listen',
        platforms: ['android']
    },
    {
        name: 'register',
        platforms: ['android', 'ios']
    },
    {
        name: 'subscribe',
        platforms: ['android', 'ios']
    },
    {
        name: 'unsubscribe',
        platforms: ['android', 'ios']
    },
    {
        name: 'toggleFCM',
        noError: true,
        noCallback: true,
        platforms: ['android']
    },
    {
        // Leave for backward compatibility
        name: 'requestStoragePermission',
        platforms: ['android']
    },
    {
        name: 'isRegistered',
        noError: true,
        platforms: ['android', 'ios']
    },
    {
        name: 'unregister',
        platforms: ['android']
    },
    {
        name: 'setNotificationListener',
        noError: true,
        platforms: ['android', 'ios']
    },
    {
        name: 'setNotificationClickListener',
        noError: true,
        platforms: ['android', 'ios']
    },
    {
        name: 'setAppId',
        noError: true,
        platforms: ['android', 'ios']
    },
    {
        name: 'setBadge',
        noError: true,
        noCallback: true,
        platforms: ['ios']
    },
    {
        name: 'clearBadge',
        noError: true,
        noCallback: true,
        platforms: ['ios']
    },
    {
        name: 'setProxyEndpoint',
        noError: true,
        noCallback: true,
        platforms: ['android', 'ios']
    },
    {
        name: 'setEnterpriseConfig',
        noError: true,
        noCallback: true,
        platforms: ['android', 'ios']
    },
    {
        name: 'setEnterpriseCertificate',
        noError: true,
        noCallback: true,
        platforms: ['android']
    },
    {
        name: 'setNotificationIcon',
        noError: true,
        noCallback: true,
        platforms: ['android']
    },
    {
        name: 'toggleInAppBanner',
        noError: true,
        noCallback: true,
        platforms: ['ios']
    },
    {
        name: 'toggleAPNsConnectivityCheck',
        noError: true,
        noCallback: true,
        platforms: ['ios']
    }
];

// Expose native actions
for (var i in actions) {
    // Get action by index
    var action = actions[i];

    // Expose action to JS code
    exports[action.name] = executeNativeAction(action);
}

function executeNativeAction(action) {
    // Get platform name dynamically
    var platform = cordova.platformId;

    // Return custom function
    return function () {
        var callback;

        // Obtain function arguments dynamically
        var args = [].slice.apply(arguments);

        // At least one argument provided?
        if (args.length > 0 && !action.noCallback) {
            // Callback should be last argument
            callback = args[args.length - 1];

            // Remove callback from arguments list
            args.splice(args.length - 1, 1);
        }

        // Action not intended for this platform?
        if (action.platforms.indexOf(platform) === -1) {
            // Invoke success callback immediately if passed, otherwise do nothing
            return callback ? callback() : null;
        }

        // If action succeeds
        var successCallback = function () {
            if (callback) {
                // Is the action never supposed to throw an error?
                if (action.noError) {
                    callback(arguments[0]);
                }
                else {
                    callback(null, arguments[0]);
                }
            }
        };

        // If action fails
        var errorCallback = function () {
            if (callback) {
                callback(arguments[0]);
            }
        };

        // Execute native plugin function by name
        cordova.exec(successCallback, errorCallback, nativeClassNames[platform], action.name, args);
    };
}