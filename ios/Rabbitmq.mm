#import "Rabbitmq.h"
#import "RabbitMqDelegateLogger.h"
#import "RabbitMqQueue.h"
#import "EventEmitter.h"
#import <RMQClient/RMQClient.h>

@interface Rabbitmq ()
@property (nonatomic, strong) NSDictionary *config;
@property (nonatomic, strong) RMQConnection *connection;
@property (nonatomic, strong) id<RMQChannel> channel;
@property (nonatomic, assign) BOOL connected;
@property (nonatomic, strong) NSMutableArray<RabbitMqQueue *> *queues;
@property (nonatomic, strong) NSMutableArray<id<RMQExchange>> *exchanges;
@end

@implementation Rabbitmq
RCT_EXPORT_MODULE()

RCT_EXPORT_METHOD(initialize:(NSDictionary *)config)
{
    self.config = config;
    self.connected = NO;
    self.queues = [NSMutableArray array];
    self.exchanges = [NSMutableArray array];
}

RCT_EXPORT_METHOD(connect)
{
    RabbitMqDelegateLogger *delegate = [[RabbitMqDelegateLogger alloc] init];

    NSString *protocol = [self.config[@"ssl"] boolValue] ? @"amqps" : @"amqp";
    NSString *uri = [NSString stringWithFormat:@"%@://%@:%@@%@:%@/%@", protocol, self.config[@"username"], self.config[@"password"], self.config[@"host"], self.config[@"port"], self.config[@"virtualhost"]];

    self.connection = [[RMQConnection alloc] initWithUri:uri
                                              channelMax:@65535
                                                frameMax:@(RMQFrameMax)
                                               heartbeat:@10
                                           connectTimeout:@15
                                              readTimeout:@30
                                             writeTimeout:@30
                                              syncTimeout:@10
                                                 delegate:delegate
                                            delegateQueue:dispatch_get_global_queue(DISPATCH_QUEUE_PRIORITY_DEFAULT, 0)];

    [self.connection start:^{
        self.connected = YES;
        [EventEmitter emitEventWithName:@"RabbitMqConnectionEvent" body:@{@"name": @"connected"}];
    }];
}

RCT_EXPORT_METHOD(close)
{
    for (RabbitMqQueue *queue in self.queues) {
        [queue cancelConsumer];
    }

    [self.connection close];
    [self.queues removeAllObjects];
    [self.exchanges removeAllObjects];
    self.connection = nil;
    self.connected = NO;
}

RCT_EXPORT_METHOD(addQueue:(NSDictionary *)config arguments:(NSDictionary *)arguments)
{
    if (self.connected) {
        self.channel = [self.connection createChannel];
        RabbitMqQueue *queue = [[RabbitMqQueue alloc] initWithConfig:config channel:self.channel];
        [self.queues addObject:queue];
    }
}

RCT_EXPORT_METHOD(bindQueue:(NSString *)exchangeName queueName:(NSString *)queueName routingKey:(NSString *)routingKey)
{
    RabbitMqQueue *queue = [self findQueue:queueName];
    id<RMQExchange> exchange = [self findExchange:exchangeName];

    if (queue && exchange) {
        [queue bind:exchange routingKey:routingKey];
    }
}

RCT_EXPORT_METHOD(unbindQueue:(NSString *)exchangeName queueName:(NSString *)queueName routingKey:(NSString *)routingKey)
{
    RabbitMqQueue *queue = [self findQueue:queueName];
    id<RMQExchange> exchange = [self findExchange:exchangeName];

    if (queue && exchange) {
        [queue unbind:exchange routingKey:routingKey];
    }
}

RCT_EXPORT_METHOD(removeQueue:(NSString *)queueName)
{
    RabbitMqQueue *queue = [self findQueue:queueName];

    if (queue) {
        [queue delete];
    }
}

RCT_EXPORT_METHOD(basicAck:(NSString *)queueName deliveryTag:(nonnull NSNumber *)deliveryTag)
{
    RabbitMqQueue *queue = [self findQueue:queueName];

    if (queue) {
        [queue ack:deliveryTag];
    }
}

RCT_EXPORT_METHOD(cancelConsumer:(NSString *)queueName)
{
    RabbitMqQueue *queue = [self findQueue:queueName];

    if (queue) {
        [queue cancelConsumer];
    }
}

RCT_EXPORT_METHOD(addExchange:(NSDictionary *)config)
{
    RMQExchangeDeclareOptions options = RMQExchangeDeclareNoOptions;

    if ([config[@"passive"] boolValue]) {
        options |= RMQExchangeDeclarePassive;
    }
    if ([config[@"durable"] boolValue]) {
        options |= RMQExchangeDeclareDurable;
    }
    if ([config[@"autoDelete"] boolValue]) {
        options |= RMQExchangeDeclareAutoDelete;
    }
    if ([config[@"internal"] boolValue]) {
        options |= RMQExchangeDeclareInternal;
    }
    if ([config[@"NoWait"] boolValue]) {
        options |= RMQExchangeDeclareNoWait;
    }

    NSString *type = config[@"type"];
    id<RMQExchange> exchange = nil;

    if ([type isEqualToString:@"fanout"]) {
        exchange = [self.channel fanout:config[@"name"] options:options];
    } else if ([type isEqualToString:@"direct"]) {
        exchange = [self.channel direct:config[@"name"] options:options];
    } else if ([type isEqualToString:@"topic"]) {
        exchange = [self.channel topic:config[@"name"] options:options];
    }

    if (exchange) {
        [self.exchanges addObject:exchange];
    }
}

RCT_EXPORT_METHOD(publishToExchange:(NSString *)message exchangeName:(NSString *)exchangeName routingKey:(NSString *)routingKey messageProperties:(NSDictionary *)messageProperties)
{
    id<RMQExchange> exchange = [self findExchange:exchangeName];

    if (exchange) {
        NSData *data = [message dataUsingEncoding:NSUTF8StringEncoding];
        NSMutableArray<id<RMQBasicProperty>> *properties = [NSMutableArray array];

        if (messageProperties[@"content_type"]) {
            [properties addObject:[[RMQBasicContentType alloc] initWithString:messageProperties[@"content_type"]]];
        }
        if (messageProperties[@"content_encoding"]) {
            [properties addObject:[[RMQBasicContentEncoding alloc] initWithString:messageProperties[@"content_encoding"]]];
        }
        if (messageProperties[@"delivery_mode"]) {
            [properties addObject:[[RMQBasicDeliveryMode alloc] initWithInt:[messageProperties[@"delivery_mode"] intValue]]];
        }
        if (messageProperties[@"priority"]) {
            [properties addObject:[[RMQBasicPriority alloc] initWithInt:[messageProperties[@"priority"] intValue]]];
        }
        if (messageProperties[@"correlation_id"]) {
            [properties addObject:[[RMQBasicCorrelationId alloc] initWithString:messageProperties[@"correlation_id"]]];
        }
        if (messageProperties[@"expiration"]) {
            [properties addObject:[[RMQBasicExpiration alloc] initWithString:messageProperties[@"expiration"]]];
        }
        if (messageProperties[@"message_id"]) {
            [properties addObject:[[RMQBasicMessageId alloc] initWithString:messageProperties[@"message_id"]]];
        }
        if (messageProperties[@"type"]) {
            [properties addObject:[[RMQBasicType alloc] initWithString:messageProperties[@"type"]]];
        }
        if (messageProperties[@"user_id"]) {
            [properties addObject:[[RMQBasicUserId alloc] initWithString:messageProperties[@"user_id"]]];
        }
        if (messageProperties[@"app_id"]) {
            [properties addObject:[[RMQBasicAppId alloc] initWithString:messageProperties[@"app_id"]]];
        }
        if (messageProperties[@"reply_to"]) {
            [properties addObject:[[RMQBasicReplyTo alloc] initWithString:messageProperties[@"reply_to"]]];
        }

        [exchange publish:data routingKey:routingKey properties:properties options:RMQBasicPublishNoOptions];
    }
}

RCT_EXPORT_METHOD(deleteExchange:(NSString *)exchangeName)
{
    id<RMQExchange> exchange = [self findExchange:exchangeName];

    if (exchange) {
        [exchange delete];
    }
}

- (RabbitMqQueue *)findQueue:(NSString *)name
{
    for (RabbitMqQueue *queue in self.queues) {
        if ([queue.name isEqualToString:name]) {
            return queue;
        }
    }
    return nil;
}

- (id<RMQExchange>)findExchange:(NSString *)name
{
    for (id<RMQExchange> exchange in self.exchanges) {
        if ([exchange.name isEqualToString:name]) {
            return exchange;
        }
    }
    return nil;
}

@end
