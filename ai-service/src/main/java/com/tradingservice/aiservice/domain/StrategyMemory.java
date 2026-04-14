package com.tradingservice.aiservice.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Entity for strategy_memory table - stores performance memories with vector embeddings.
 */
@Entity
@Table(name = "strategy_memory")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StrategyMemory {

    @Id
    private UUID id;

    @Column(length = 100)
    private String strategyName;

    @Column(name = "strategy_id")
    private UUID strategyId;

    @Column(columnDefinition = "TEXT")
    private String document;

    @Transient  // Managed exclusively via JdbcTemplate with ::vector cast; Hibernate cannot map pgvector type
    private float[] embedding;

    @Column(name = "sharpe_ratio")
    private Double sharpeRatio;

    @Column(name = "win_rate")
    private Double winRate;

    @Column(name = "max_drawdown")
    private Double maxDrawdown;

    @Column(name = "trade_count")
    private Integer tradeCount;

    @Column(length = 20)
    private String verdict;

    @Column(columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private Map<String, Object> marketContext;

    @Column(name = "created_at")
    private Instant createdAt;

    @PrePersist
    public void prePersist() {
        if (id == null) id = UUID.randomUUID();
        if (createdAt == null) createdAt = Instant.now();
    }
}
