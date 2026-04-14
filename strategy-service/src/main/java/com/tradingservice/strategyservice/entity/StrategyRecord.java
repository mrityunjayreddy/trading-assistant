package com.tradingservice.strategyservice.entity;

import com.tradingservice.strategyservice.domain.StrategyDSL;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

/**
 * JPA entity mapping to the {@code strategies} table.
 *
 * <p>The {@code dsl} column is stored as PostgreSQL JSONB.
 * Hibernate 6 handles JSONB serialisation via {@code @JdbcTypeCode(SqlTypes.JSON)}
 * with Jackson on the classpath — no extra library required.</p>
 */
@Entity
@Table(name = "strategies")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StrategyRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    /**
     * One of: BUILTIN, USER, LLM, EVOLVED
     */
    @Column(name = "source", nullable = false, length = 20)
    private String source;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "dsl", nullable = false, columnDefinition = "jsonb")
    private StrategyDSL dsl;

    @Builder.Default
    @Column(name = "is_active", nullable = false)
    private boolean isActive = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void prePersist() {
        Instant now = Instant.now();
        if (createdAt == null) createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = Instant.now();
    }
}