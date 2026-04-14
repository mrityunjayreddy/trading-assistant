package com.tradingservice.tradingassistantbackend.config;

import java.util.HashMap;
import java.util.Map;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;

/**
 * Infrastructure beans for Kafka publishing and DB persistence of closed candles.
 * Reads bootstrap servers and DB credentials from environment variables with
 * sensible local-dev defaults.
 */
@Configuration
public class InfrastructureConfig {

    @Bean
    public ProducerFactory<String, String> candleProducerFactory(
        @Value("${KAFKA_BOOTSTRAP_SERVERS:localhost:9092}") String bootstrapServers
    ) {
        Map<String, Object> config = new HashMap<>();
        config.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        config.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        config.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        config.put(ProducerConfig.ACKS_CONFIG, "all");
        config.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
        config.put(ProducerConfig.COMPRESSION_TYPE_CONFIG, "lz4");
        return new DefaultKafkaProducerFactory<>(config);
    }

    @Bean
    public KafkaTemplate<String, String> candleKafkaTemplate(
        ProducerFactory<String, String> candleProducerFactory
    ) {
        return new KafkaTemplate<>(candleProducerFactory);
    }
}