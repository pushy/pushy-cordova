var cordova = require('cordova');

// Native action names
var actions = [
    {
        name: 'listen'
    },
    {
        name: 'register'
    },
    {
        name: 'subscribe'
    },
    {
        name: 'unsubscribe'
    },
    {
        name: 'requestStoragePermission'
    },
    {
        name: 'isRegistered',
        noError: true
    },
    {
        name: 'setNotificationListener',
        noError: true
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
    return function () {
        var callback;

        // Obtain function arguments dynamically
        var args = [].slice.apply(arguments);

        // At least one argument provided?
        if (args.length > 0) {
            // Callback should be last argument
            callback = args[args.length - 1];

            // Remove callback from arguments list
            args.splice(args.length - 1, 1);
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
        cordova.exec(successCallback, errorCallback, 'Pushy', action.name, args);
    };
}

//ios
    var exec = require('cordova/exec');
    var pluginNativeName = "PushyNotificationPlugin";
               
    var Pushy = function () {};

    Pushy.prototype = {
    	
		register : function(successCallback, errorCallback) {
           
			exec(successCallback,errorCallback,pluginNativeName,'register',[]);
		},

        getPendingNotification: function(successCallback, errorCallback) {
               
			exec(successCallback,errorCallback,pluginNativeName,'getPendingNotification',[]);
		},
    };
	
    module.exports = new Pushy();
