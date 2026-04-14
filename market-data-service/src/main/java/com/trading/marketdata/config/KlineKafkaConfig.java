package com.trading.marketdata.config;

import java.util.HashMap;
import java.util.Map;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.listener.ContainerProperties;

/**
 * Kafka infrastructure for kline events.
 *
 * Producer: KafkaTemplate<String, String> — KlineEvent is serialized to JSON manually
 * before send, keeping this producer independent of type changes.
 *
 * Consumer: batch container factory — accumulates up to 50 records or 10 s,
 * whichever comes first (controlled by max-poll-records + fetch.max.wait.ms).
 */
@Configuration
public class KlineKafkaConfig {

    // -------------------------------------------------------------------------
    // Producer
    // -------------------------------------------------------------------------

    @Bean
    public ProducerFactory<String, String> klineProducerFactory(MarketDataProperties properties) {
        Map<String, Object> config = new HashMap<>();
        config.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, properties.getKafka().getBootstrapServers());
        config.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        config.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        config.put(ProducerConfig.ACKS_CONFIG, "all");
        config.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
        config.put(ProducerConfig.COMPRESSION_TYPE_CONFIG, "lz4");
        config.put(ProducerConfig.DELIVERY_TIMEOUT_MS_CONFIG, 120_000);
        config.put(ProducerConfig.REQUEST_TIMEOUT_MS_CONFIG, 30_000);
        return new DefaultKafkaProducerFactory<>(config);
    }

    @Bean
    public KafkaTemplate<String, String> klineKafkaTemplate(
        ProducerFactory<String, String> klineProducerFactory
    ) {
        return new KafkaTemplate<>(klineProducerFactory);
    }

    // -------------------------------------------------------------------------
    // Batch consumer factory
    // -------------------------------------------------------------------------

    @Bean
    public ConsumerFactory<String, String> klineConsumerFactory(MarketDataProperties properties) {
        Map<String, Object> config = new HashMap<>();
        config.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, properties.getKafka().getBootstrapServers());
        config.put(ConsumerConfig.GROUP_ID_CONFIG, "kline-persister");
        config.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        config.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        config.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "latest");
        // Batch tuning: up to 50 records per poll, wait up to 10 s for them
        config.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, 50);
        config.put(ConsumerConfig.FETCH_MAX_WAIT_MS_CONFIG, 10_000);
        config.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
        return new DefaultKafkaConsumerFactory<>(config);
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, String> klineBatchContainerFactory(
        ConsumerFactory<String, String> klineConsumerFactory
    ) {
        ConcurrentKafkaListenerContainerFactory<String, String> factory =
            new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(klineConsumerFactory);
        factory.setBatchListener(true);
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.BATCH);
        return factory;
    }
}