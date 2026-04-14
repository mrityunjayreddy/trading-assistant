package com.tradingservice.aiservice.repository;

import com.tradingservice.aiservice.domain.StrategyMemory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;

/**
 * JPA repository for strategy_memory table.
 * Note: Vector similarity queries use native SQL via JdbcTemplate in StrategyMemoryReader.
 */
@Repository
public interface StrategyMemoryRepository extends JpaRepository<StrategyMemory, UUID> {

    @Query("SELECT m FROM StrategyMemory m WHERE m.strategyId = :strategyId ORDER BY m.createdAt DESC")
    List<StrategyMemory> findByStrategyId(@Param("strategyId") UUID strategyId);

    @Query("SELECT m FROM StrategyMemory m WHERE m.verdict = :verdict ORDER BY m.sharpeRatio DESC")
    List<StrategyMemory> findByVerdict(@Param("verdict") String verdict);

    @Query("SELECT COUNT(m) FROM StrategyMemory m WHERE m.createdAt > :since")
    long countSince(@Param("since") Instant since);

    @Query("SELECT AVG(m.sharpeRatio) FROM StrategyMemory m WHERE m.createdAt > :since")
    Double avgSharpeSince(@Param("since") Instant since);

    @Query("SELECT m FROM StrategyMemory m ORDER BY m.createdAt DESC")
    List<StrategyMemory> findRecent(Pageable pageable);

    @Query("SELECT m.verdict, COUNT(m) FROM StrategyMemory m GROUP BY m.verdict")
    List<Object[]> verdictBreakdown();

    Optional<StrategyMemory> findFirstByOrderByCreatedAtAsc();
}
