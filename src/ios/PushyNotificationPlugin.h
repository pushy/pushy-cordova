//
//  PushyNotificationPlugin.h
//  
//
//  Created by Sreenivas on 2/17/17
//

#import <Cordova/CDV.h>

@interface PushyNotificationPlugin : CDVPlugin
    {

    }
    
    @property (nonatomic, copy) NSString *callbackId;

- (void)register:(CDVInvokedUrlCommand*)command;
- (void)getPendingNotification:(CDVInvokedUrlCommand*) command;
- (void)didReceiveRemoteNotificationWithPayload:(NSDictionary *)payload;
    
@end
