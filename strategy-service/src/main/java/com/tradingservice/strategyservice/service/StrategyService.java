package com.tradingservice.strategyservice.service;

import com.tradingservice.strategyservice.domain.StrategyDSL;
import com.tradingservice.strategyservice.dto.RegisterStrategyResponse;
import com.tradingservice.strategyservice.entity.StrategyRecord;
import com.tradingservice.strategyservice.exception.ValidationException;
import com.tradingservice.strategyservice.repository.StrategyRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Core strategy lifecycle service.
 *
 * <h3>Source limits and enforcement</h3>
 * <ul>
 *   <li><b>BUILTIN</b>  — max 5; hard reject when full</li>
 *   <li><b>USER</b>     — max 20; hard reject when full</li>
 *   <li><b>LLM</b>      — max 50; deactivates oldest when full, then inserts new</li>
 *   <li><b>EVOLVED</b>  — max 20; deactivates oldest when full, then inserts new</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StrategyService {

    private static final int BUILTIN_MAX  = 5;
    private static final int USER_MAX     = 20;
    private static final int LLM_MAX      = 50;
    private static final int EVOLVED_MAX  = 20;

    private final StrategyRepository strategyRepository;
    private final StrategyValidator  strategyValidator;

    // -------------------------------------------------------------------------
    // Register
    // -------------------------------------------------------------------------

    @Transactional
    public RegisterStrategyResponse register(StrategyDSL dsl) {
        strategyValidator.validate(dsl);
        enforceSourceLimit(dsl.source());

        StrategyRecord record = StrategyRecord.builder()
                .name(dsl.name())
                .source(dsl.source())
                .dsl(dsl)
                .isActive(true)
                .build();

        StrategyRecord saved = strategyRepository.save(record);
        log.info("Registered strategy id={} name='{}' source={}", saved.getId(), saved.getName(), saved.getSource());

        return new RegisterStrategyResponse(saved.getId(), saved.getName(), saved.getSource(), "REGISTERED");
    }

    // -------------------------------------------------------------------------
    // Deactivate
    // -------------------------------------------------------------------------

    @Transactional
    public void deactivate(UUID id) {
        StrategyRecord record = strategyRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Strategy not found: " + id));
        record.setActive(false);
        strategyRepository.save(record);
        log.info("Deactivated strategy id={} name='{}'", id, record.getName());
    }

    // -------------------------------------------------------------------------
    // List active
    // -------------------------------------------------------------------------

    @Transactional(readOnly = true)
    public List<StrategyRecord> listActive() {
        return strategyRepository.findByIsActiveTrue(PageRequest.of(0, 200));
    }

    // -------------------------------------------------------------------------
    // Get by id
    // -------------------------------------------------------------------------

    @Transactional(readOnly = true)
    public StrategyRecord getById(UUID id) {
        return strategyRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Strategy not found: " + id));
    }

    // -------------------------------------------------------------------------
    // Source limit enforcement
    // -------------------------------------------------------------------------

    private void enforceSourceLimit(String source) {
        long count = strategyRepository.countBySourceAndIsActiveTrue(source);

        switch (source) {
            case "BUILTIN" -> {
                if (count >= BUILTIN_MAX) {
                    throw new ValidationException(List.of(
                            "BUILTIN limit reached (" + BUILTIN_MAX + "). Cannot add more BUILTIN strategies."));
                }
            }
            case "USER" -> {
                if (count >= USER_MAX) {
                    throw new ValidationException(List.of(
                            "USER limit reached (" + USER_MAX + "). Deactivate an existing strategy first."));
                }
            }
            case "LLM" -> {
                if (count >= LLM_MAX) {
                    deactivateOldest("LLM");
                }
            }
            case "EVOLVED" -> {
                if (count >= EVOLVED_MAX) {
                    deactivateOldest("EVOLVED");
                }
            }
            default -> { /* already validated by StrategyValidator */ }
        }
    }

    private void deactivateOldest(String source) {
        StrategyRecord oldest = strategyRepository.findFirstBySourceAndIsActiveTrueOrderByCreatedAtAsc(source);
        if (oldest != null) {
            oldest.setActive(false);
            strategyRepository.save(oldest);
            log.info("Auto-deactivated oldest {} strategy id={} name='{}' to make room",
                    source, oldest.getId(), oldest.getName());
        }
    }
}