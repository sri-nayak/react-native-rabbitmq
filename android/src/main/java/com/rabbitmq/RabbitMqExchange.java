package nl.kega.reactnativerabbitmq;

import android.util.Log;

import java.util.HashMap;
import java.util.Map;

import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReadableMap;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.bridge.ReadableType;
import java.nio.charset.StandardCharsets;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.AlreadyClosedException;

public class RabbitMqExchange {

    public String name;
    public String type;
    public Boolean durable;
    public Boolean autodelete;
    public Boolean internal;

    private ReactApplicationContext context;
    private Channel channel;

    public RabbitMqExchange(ReactApplicationContext context, Channel channel, ReadableMap config) {
        this.channel = channel;
        this.context = context;

        this.name = config.getString("name");
        this.type = config.hasKey("type") ? config.getString("type") : "fanout";
        this.durable = config.hasKey("durable") ? config.getBoolean("durable") : true;
        this.autodelete = config.hasKey("autoDelete") ? config.getBoolean("autoDelete") : false;
        this.internal = config.hasKey("internal") ? config.getBoolean("internal") : false;

        Map<String, Object> args = new HashMap<>();

        try {
            this.channel.exchangeDeclare(this.name, this.type, this.durable, this.autodelete, this.internal, args);
        } catch (Exception e) {
            Log.e("RabbitMqExchange", "Exchange error " + e);
            e.printStackTrace();
        }
    }

    public void publish(String message, String routingKey, ReadableMap messageProperties) {
        try {
            byte[] messageBodyBytes = message.getBytes(StandardCharsets.UTF_8);

            AMQP.BasicProperties.Builder properties = new AMQP.BasicProperties.Builder();

            if (messageProperties != null) {
                try {
                    if (messageProperties.hasKey("content_type") && messageProperties.getType("content_type") == ReadableType.String) {
                        properties.contentType(messageProperties.getString("content_type"));
                    }
                    if (messageProperties.hasKey("content_encoding") && messageProperties.getType("content_encoding") == ReadableType.String) {
                        properties.contentEncoding(messageProperties.getString("content_encoding"));
                    }
                    if (messageProperties.hasKey("delivery_mode") && messageProperties.getType("delivery_mode") == ReadableType.Number) {
                        properties.deliveryMode(messageProperties.getInt("delivery_mode"));
                    }
                    if (messageProperties.hasKey("priority") && messageProperties.getType("priority") == ReadableType.Number) {
                        properties.priority(messageProperties.getInt("priority"));
                    }
                    if (messageProperties.hasKey("correlation_id") && messageProperties.getType("correlation_id") == ReadableType.String) {
                        properties.correlationId(messageProperties.getString("correlation_id"));
                    }
                    if (messageProperties.hasKey("expiration") && messageProperties.getType("expiration") == ReadableType.String) {
                        properties.expiration(messageProperties.getString("expiration"));
                    }
                    if (messageProperties.hasKey("message_id") && messageProperties.getType("message_id") == ReadableType.String) {
                        properties.messageId(messageProperties.getString("message_id"));
                    }
                    if (messageProperties.hasKey("type") && messageProperties.getType("type") == ReadableType.String) {
                        properties.type(messageProperties.getString("type"));
                    }
                    if (messageProperties.hasKey("user_id") && messageProperties.getType("user_id") == ReadableType.String) {
                        properties.userId(messageProperties.getString("user_id"));
                    }
                    if (messageProperties.hasKey("app_id") && messageProperties.getType("app_id") == ReadableType.String) {
                        properties.appId(messageProperties.getString("app_id"));
                    }
                    if (messageProperties.hasKey("reply_to") && messageProperties.getType("reply_to") == ReadableType.String) {
                        properties.replyTo(messageProperties.getString("reply_to"));
                    }
                    // Handle timestamp and headers if needed

                } catch (Exception e) {
                    Log.e("RabbitMqExchange", "Exchange publish properties error " + e);
                    e.printStackTrace();
                }
            }

            this.channel.basicPublish(this.name, routingKey, properties.build(), messageBodyBytes);
        } catch (Exception e) {
            Log.e("RabbitMqExchange", "Exchange publish error " + e);
            e.printStackTrace();
        }
    }

    public void delete(Boolean ifUnused) {
        try {
            this.channel.exchangeDelete(this.name, ifUnused);
        } catch (Exception e) {
            Log.e("RabbitMqExchange", "Exchange delete error " + e);
            e.printStackTrace();
        }
    }
}
