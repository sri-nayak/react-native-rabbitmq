package com.rabbitmq;

import android.util.Log;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReadableMap;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.bridge.ReadableType;
import com.facebook.react.modules.core.DeviceEventManagerModule;
import com.facebook.react.bridge.ReadableMapKeySetIterator;

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.AlreadyClosedException;
import com.rabbitmq.RabbitMqExchange;

public class RabbitMqQueue {

    public String name;
    public String routingKey;
    public String consumerTag;
    public Boolean exclusive;
    public Boolean durable;
    public Boolean autoDelete;
    public Boolean autoAck;
    public ReadableMap consumerArguments;

    private ReactApplicationContext context;
    private Channel channel;
    private RabbitMqExchange exchange;

    public RabbitMqQueue(ReactApplicationContext context, Channel channel, ReadableMap queueConfig, ReadableMap arguments) {
        this.context = context;
        this.channel = channel;

        this.name = queueConfig.getString("name");
        this.exclusive = queueConfig.hasKey("exclusive") && queueConfig.getBoolean("exclusive");
        this.durable = !queueConfig.hasKey("durable") || queueConfig.getBoolean("durable");
        this.autoDelete = queueConfig.hasKey("autoDelete") && queueConfig.getBoolean("autoDelete");
        this.autoAck = queueConfig.hasKey("autoAck") && queueConfig.getBoolean("autoAck");

        this.consumerArguments = queueConfig.hasKey("consumer_arguments") ? queueConfig.getMap("consumer_arguments") : null;

        Map<String, Object> args = toHashMap(arguments);

        try {
            RabbitMqConsumer consumer = new RabbitMqConsumer(this.channel, this);
            Map<String, Object> consumerArgs = toHashMap(this.consumerArguments);

            this.channel.queueDeclare(this.name, this.durable, this.exclusive, this.autoDelete, args);
            this.channel.basicConsume(this.name, this.autoAck, consumerArgs, consumer);
        } catch (Exception e) {
            Log.e("RabbitMqQueue", "Queue error " + e);
            e.printStackTrace();
        }
    }

    public void onMessage(WritableMap message) {
        Log.e("RabbitMqQueue", message.getString("message"));
        message.putString("queue_name", this.name);
        this.context.getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class).emit("RabbitMqQueueEvent", message);
    }

    public void bind(RabbitMqExchange exchange, String routingKey) {
        try {
            this.exchange = exchange;
            this.routingKey = routingKey.isEmpty() ? this.name : routingKey;
            this.channel.queueBind(this.name, this.exchange.name, this.routingKey);
        } catch (Exception e) {
            Log.e("RabbitMqQueue", "Queue bind error " + e);
            e.printStackTrace();
        }
    }

    public void unbind(String routingKey) {
        try {
            if (this.exchange != null) {
                this.channel.queueUnbind(this.name, this.exchange.name, routingKey);
                this.exchange = null;
            }
        } catch (Exception e) {
            Log.e("RabbitMqQueue", "Queue unbind error " + e);
            e.printStackTrace();
        }
    }

    public void purge() {
        try {
            // this.channel.queuePurge(this.name); 

            WritableMap event = Arguments.createMap();
            event.putString("name", "purged");
            event.putString("queue_name", this.name);

            this.context.getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class).emit("RabbitMqQueueEvent", event);
        } catch (Exception e) {
            Log.e("RabbitMqQueue", "Queue purge error " + e);
            e.printStackTrace();
        }
    }

    public void delete() {
        try {
            this.channel.queueDelete(this.name);

            WritableMap event = Arguments.createMap();
            event.putString("name", "deleted");
            event.putString("queue_name", this.name);

            this.context.getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class).emit("RabbitMqQueueEvent", event);
        } catch (Exception e) {
            Log.e("RabbitMqQueue", "Queue delete error " + e);
            e.printStackTrace();
        }
    }

    private Map<String, Object> toHashMap(ReadableMap data) {
        Map<String, Object> args = new HashMap<>();

        if (data == null) {
            return args;
        }

        ReadableMapKeySetIterator iterator = data.keySetIterator();
        while (iterator.hasNextKey()) {
            String key = iterator.nextKey();
            ReadableType readableType = data.getType(key);

            switch (readableType) {
                case Null:
                    args.put(key, null);
                    break;
                case Boolean:
                    args.put(key, data.getBoolean(key));
                    break;
                case Number:
                    double tmp = data.getDouble(key);
                    if (tmp == (int) tmp) {
                        args.put(key, (int) tmp);
                    } else {
                        args.put(key, tmp);
                    }
                    break;
                case String:
                    Log.e("RabbitMqQueue", data.getString(key));
                    args.put(key, data.getString(key));
                    break;
                default:
                    break;
            }
        }
        return args;
    }

    public void basicAck(long deliveryTag) {
        try {
            this.channel.basicAck(deliveryTag, false);
        } catch (IOException | AlreadyClosedException e) {
            Log.e("RabbitMqQueue", "basicAck " + e);
            e.printStackTrace();
        }
    }
}
