
#ifdef RCT_NEW_ARCH_ENABLED
#import "RNRabbitmqSpec.h"

@interface Rabbitmq : NSObject <NativeRabbitmqSpec>
#else
#import <React/RCTBridgeModule.h>

@interface Rabbitmq : NSObject <RCTBridgeModule>
#endif

@end
