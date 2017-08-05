//
//  PushyNotificationPlugin.m
//
//  Created by Sreenivas on 2/17/17
//
//

#import "PushyNotificationPlugin.h"
#import <PushySDK/PushySDK-Swift.h>

@implementation PushyNotificationPlugin
    
    @synthesize callbackId;
    
    static NSDictionary *coldstartNotification;
    
    NSMutableArray *jsEventQueue;
    BOOL canDeliverNotifications = NO;
    
    /*
     Ideally the UIApplicationDidFinishLaunchingNotification would go in pluginInitialize
     but it is too late in the life cycle to catch the actual event for remote notifications.
     
     For local notifications it is fine becuase the base CDVPlugin takes care of forwarding the event
     but not for remote notifications and as we dont want to make changes to the cordova base classes:
     
     We use the static load method to attach the observer and if the handler finds a corresponding notification
     it is stored in a static var.
     
     Later on in pluginInitialize we check if the static var conatins a notification and if yes use it
     
     Additionaly, weo make sure the notification callbacks in the client javascript are not delivered
     until the client app is ready for them by checking:
     1. The app is in the foreground
     2. The 'register' method has been called on the plugin - passing in a handler
     
     If either of these conditions is not true we hold onto notifications in a queue and then flush it to the client
     when they are fulfilled.
     
     */
    
+(void) load
    {
        [[NSNotificationCenter defaultCenter] addObserver:self selector:@selector(checkForColdStartNotification:)
                                                     name:UIApplicationDidFinishLaunchingNotification object:nil];
    }
    
+ (void) checkForColdStartNotification:(NSNotification *)notification
    {
        NSDictionary *launchOptions = [notification userInfo];
        
        NSDictionary *payload = [launchOptions objectForKey: @"UIApplicationLaunchOptionsRemoteNotificationKey"];
        
        if(payload){
            
            NSMutableDictionary *extendedPayload = [payload mutableCopy];
            [extendedPayload setObject:[NSNumber numberWithBool:NO] forKey:@"receivedInForeground"];
            
            coldstartNotification = extendedPayload;
        }
        
    }
    
- (void)unregister:(CDVInvokedUrlCommand*)command;
    {
        self.callbackId = command.callbackId;
        
        [[UIApplication sharedApplication] unregisterForRemoteNotifications];
        [self successWithMessage:@"unregistered"];
    }
    
    - (void)register:(CDVInvokedUrlCommand*)command;
    {
        [self.commandDelegate runInBackground:^{
            Pushy* __block pushy = [[Pushy alloc]init:self.appDelegate application:[UIApplication sharedApplication]];
            
            [pushy register:^(NSError *error, NSString* deviceToken) {
                
                // Handle registration errors
                if (error != nil) {
                    return NSLog (@"Registration failed: %@", error);
                }
                
                // Print device token to console
                NSLog(@"Pushy device token: %@", deviceToken);
                
                // Persist the token locally and send it to your backend later
                [[NSUserDefaults standardUserDefaults] setObject:deviceToken forKey:@"pushyToken"];
                
                self.callbackId = command.callbackId;
                [self successWithMessage:[NSString stringWithFormat:@"%@", deviceToken]];
            }];
            
        
            [pushy setNotificationHandler:^(NSDictionary *data, void (^completionHandler)(UIBackgroundFetchResult)) {
                
                // You must call this completion handler when you finish processing
                // the notification (after fetching background data, if applicable)
                UIApplicationState appstate = [[UIApplication sharedApplication] applicationState];
                
                NSMutableDictionary *extendedPayload = [data mutableCopy];
                [extendedPayload setObject:[NSNumber numberWithBool:(appstate == UIApplicationStateActive)] forKey:@"receivedInForeground"];
                [self didReceiveRemoteNotificationWithPayload:extendedPayload];
                
                completionHandler(UIBackgroundFetchResultNewData);
            }];
        
        
        }];
        
        [self flushNotificationEventQueue];
        canDeliverNotifications = YES;
    }

    /*The below method helps after cold start.(i.e.,)when app is closed,to get the notification received when loading.*/
    - (void)getPendingNotification:(CDVInvokedUrlCommand*) command{ 
        
        [self flushNotificationEventQueue];
    }

- (void)didReceiveRemoteNotificationWithPayload: (NSDictionary *)payload
    {
        NSLog(@"didReceiveRemoteNotificationWithPayload received");
        
        NSDictionary *aps = [payload objectForKey:@"aps"];
        NSMutableDictionary *data = [[payload objectForKey:@"data"] mutableCopy];
        
        if(data == nil){
            data = [[NSMutableDictionary alloc] init];
        }
        
        /*
         
         the aps.alert value is required in order for the ios notification center to have something to show
         or else it wouls show the full JSON payload.
         
         however on the js side we want to access all the properties for this notification inside a single
         object and care not for ios specific implemenataion such as the aps wrapper
         
         we could just duplicate the text and have it in both *aps.alert* and inside data.message but as the
         payload size limit is only 256 bytes it is better to check if an explicit data.message value exists
         and if not just copy aps.alert into it
         
         */
        
        if([aps objectForKey:@"alert"]){
            if(![data objectForKey:@"message"]){
                [data setObject:[aps objectForKey:@"alert"] forKey:@"message"];
            }
        }
        
        BOOL receivedInForeground = [[payload objectForKey:@"receivedInForeground"] boolValue];
        NSString* stateName = receivedInForeground ? @"foreground" : @"background";
        
        NSData *jsonData = [NSJSONSerialization dataWithJSONObject:payload options:NSJSONWritingPrettyPrinted error:nil];
        
        NSString *json = [[NSString alloc] initWithData:jsonData encoding:NSUTF8StringEncoding];
        
        
        NSLog(@"Msg: %@", json);
        
        NSString * jsCallBack = [NSString stringWithFormat:@"setTimeout(function(){pushJSON('%@', %@)},100)", stateName, json];
        
        if(receivedInForeground){
            
            //  iOS <= 8
            if([self.webView respondsToSelector:@selector(stringByEvaluatingJavaScriptFromString:)]){
                
                [self.webView performSelectorOnMainThread:@selector(stringByEvaluatingJavaScriptFromString:) withObject:jsCallBack waitUntilDone:NO];
                
            }else{
                
                [self.webViewEngine evaluateJavaScript:jsCallBack completionHandler:nil];
            }

        }
        else
        {
            if(jsEventQueue == nil)
            {
                jsEventQueue = [[NSMutableArray alloc] init];
            }
            
            [jsEventQueue addObject:jsCallBack];
        }
        
    }
    
    
    
- (void) didBecomeActive:(NSNotification *)notification
    {
        if(canDeliverNotifications)
        {
            [self flushNotificationEventQueue];
        }
        
    }
    
-(void) flushNotificationEventQueue
    {
        if(jsEventQueue != nil && [jsEventQueue count] > 0)
        {
            for(NSString *notificationEvent in jsEventQueue)
            {
                [self.commandDelegate evalJs:notificationEvent];
            }
            
            [jsEventQueue removeAllObjects];
        }
    }
    
- (void) pluginInitialize
    {
        [[NSNotificationCenter defaultCenter] addObserver:self selector:@selector(didBecomeActive:)
                                                     name:UIApplicationDidBecomeActiveNotification object:nil];
        
        if(coldstartNotification)
        {
            [self didReceiveRemoteNotificationWithPayload:coldstartNotification];
            coldstartNotification = nil;
        }
    }
    
    
-(void)successWithMessage:(NSString *)message
    {
        CDVPluginResult *commandResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK messageAsString:message];
        
        [self.commandDelegate sendPluginResult:commandResult callbackId:self.callbackId];
    }
    
-(void)failWithMessage:(NSString *)message withError:(NSError *)error
    {
        NSString        *errorMessage = (error) ? [NSString stringWithFormat:@"%@ - %@", message, [error localizedDescription]] : message;
        CDVPluginResult *commandResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_ERROR messageAsString:errorMessage];
        
        [self.commandDelegate sendPluginResult:commandResult callbackId:self.callbackId];
    }
    
    @end
