package com.trading.marketdata.config;

import com.trading.marketdata.model.TradeEvent;
import java.util.HashMap;
import java.util.Map;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.support.serializer.JsonSerializer;

@Configuration
public class KafkaConfig {

    @Bean
    public ProducerFactory<String, TradeEvent> tradeEventProducerFactory(MarketDataProperties properties) {
        Map<String, Object> configuration = new HashMap<>();
        configuration.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, properties.getKafka().getBootstrapServers());
        configuration.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        configuration.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
        configuration.put(ProducerConfig.ACKS_CONFIG, "all");
        configuration.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
        configuration.put(ProducerConfig.COMPRESSION_TYPE_CONFIG, "lz4");
        configuration.put(ProducerConfig.DELIVERY_TIMEOUT_MS_CONFIG, 120000);
        configuration.put(ProducerConfig.REQUEST_TIMEOUT_MS_CONFIG, 30000);
        configuration.put(JsonSerializer.ADD_TYPE_INFO_HEADERS, false);
        return new DefaultKafkaProducerFactory<>(configuration);
    }

    @Bean
    public KafkaTemplate<String, TradeEvent> tradeEventKafkaTemplate(
        ProducerFactory<String, TradeEvent> tradeEventProducerFactory
    ) {
        return new KafkaTemplate<>(tradeEventProducerFactory);
    }
}
