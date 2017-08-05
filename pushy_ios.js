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


