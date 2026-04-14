package com.trading.marketdata.publisher;

import com.trading.marketdata.config.MarketDataProperties;
import com.trading.marketdata.model.TradeEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
public class KafkaMarketEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(KafkaMarketEventPublisher.class);

    private final KafkaTemplate<String, TradeEvent> kafkaTemplate;
    private final String tradeTopic;

    public KafkaMarketEventPublisher(
        KafkaTemplate<String, TradeEvent> kafkaTemplate,
        MarketDataProperties properties
    ) {
        this.kafkaTemplate = kafkaTemplate;
        this.tradeTopic = properties.getKafka().getTradeTopic();
    }

    public Mono<Void> publishTradeEvent(TradeEvent tradeEvent) {
        return Mono.fromFuture(() -> kafkaTemplate.send(tradeTopic, tradeEvent.symbol(), tradeEvent))
            .doOnSuccess(result -> log.info("Published event to Kafka topic {}", tradeTopic))
            .doOnError(error -> log.error(
                "Failed to publish event for symbol {} to topic {}",
                tradeEvent.symbol(),
                tradeTopic,
                error
            ))
            .then();
    }
}
