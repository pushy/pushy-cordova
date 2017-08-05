//
//  AppDelegate+pushyNotificationPlugin.m
//
//  Created by Sreenivas on 2/17/17
//
//

#import "AppDelegate+pushyNotificationPlugin.h"
#import <objc/runtime.h>



@implementation AppDelegate (pushyNotificationPlugin)

- (id) getCommandInstance:(NSString*)className
{
    return [self.viewController getCommandInstance:className];
}


@end
