package org.example.aiscanner_server.config;

import org.example.aiscanner_server.stream.DetectStreamConsumer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.stream.Consumer;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.core.StreamOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.data.redis.stream.StreamMessageListenerContainer;

import java.time.Duration;

@Configuration
public class RedisStreamConfig {

    static final String STREAM_KEY = "detect:stream";
    static final String CONSUMER_GROUP = "detect-consumers";
    static final String CONSUMER_NAME = "consumer-1";

    @Bean
    public StreamOperations<String, String, String> streamOperations(
            StringRedisTemplate stringRedisTemplate) {
        return stringRedisTemplate.opsForStream();
    }

    @Bean
    public StreamMessageListenerContainer<String, MapRecord<String, String, String>> streamContainer(
            RedisConnectionFactory connectionFactory,
            DetectStreamConsumer consumer) {

        var options = StreamMessageListenerContainer.StreamMessageListenerContainerOptions
                .builder()
                .pollTimeout(Duration.ofSeconds(2))
                .serializer(RedisSerializer.string())
                .build();

        var container = StreamMessageListenerContainer.create(connectionFactory, options);

        // Auto-create consumer group if it doesn't exist
        try {
            connectionFactory.getConnection().streamCommands()
                    .xGroupCreate(STREAM_KEY.getBytes(), CONSUMER_GROUP, ReadOffset.from("0"), true);
        } catch (Exception ignored) {
            // Group already exists
        }

        container.receive(Consumer.from(CONSUMER_GROUP, CONSUMER_NAME),
                StreamOffset.create(STREAM_KEY, ReadOffset.lastConsumed()),
                consumer);

        container.start();
        return container;
    }
}
