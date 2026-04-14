package com.tradingservice.strategyservice.repository;

import com.tradingservice.strategyservice.entity.StrategyRecord;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface StrategyRepository extends JpaRepository<StrategyRecord, UUID> {

    List<StrategyRecord> findBySourceAndIsActiveTrue(String source);

    long countBySourceAndIsActiveTrue(String source);

    List<StrategyRecord> findByIsActiveTrue(Pageable pageable);

    /**
     * Used by LLM/EVOLVED source-limit enforcement: finds the oldest active
     * strategy for a given source so it can be deactivated to make room.
     */
    StrategyRecord findFirstBySourceAndIsActiveTrueOrderByCreatedAtAsc(String source);
}