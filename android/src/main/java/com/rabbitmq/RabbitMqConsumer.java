package nl.kega.reactnativerabbitmq;

import android.util.Log;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.ReadableMap;
import com.facebook.react.bridge.WritableMap;

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.DefaultConsumer;
import com.rabbitmq.client.Envelope;
import com.rabbitmq.client.AMQP;


public class RabbitMqConsumer extends DefaultConsumer {

    private RabbitMqQueue connection;
    private Channel channel;

    public RabbitMqConsumer(Channel channel, RabbitMqQueue connection) {
        super(channel);
        this.channel = channel;
        this.connection = connection;
    }

    @Override
    public void handleDelivery(String consumerTag, Envelope envelope, AMQP.BasicProperties properties, byte[] body) throws IOException {
        String routingKey = envelope.getRoutingKey();
        String exchange = envelope.getExchange();
        String contentType = properties.getContentType();
        boolean isRedeliver = envelope.isRedeliver();

        String message = new String(body, StandardCharsets.UTF_8);

        WritableMap messageParams = Arguments.createMap();
        messageParams.putString("name", "message");
        messageParams.putString("message", message);
        messageParams.putString("routing_key", routingKey);
        messageParams.putString("exchange", exchange);
        messageParams.putString("content_type", contentType);
        messageParams.putDouble("delivery_tag", envelope.getDeliveryTag());
        messageParams.putBoolean("is_redeliver", isRedeliver);

        this.connection.onMessage(messageParams);

        // Uncomment this line if you want to automatically acknowledge messages
        // this.channel.basicAck(envelope.getDeliveryTag(), false);
    }
}
