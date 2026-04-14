package com.tradingservice.aiservice.generation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tradingservice.aiservice.analysis.MarketContextAnalyzer;
import com.tradingservice.aiservice.domain.GenerationRequest;
import com.tradingservice.aiservice.domain.GenerationResult;
import com.tradingservice.aiservice.domain.MarketContext;
import com.tradingservice.aiservice.domain.MemoryRecord;
import com.tradingservice.aiservice.memory.StrategyMemoryReader;
import com.tradingservice.aiservice.service.AnthropicClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Generates new trading strategies using AI with retrieved memories as context.
 *
 * Process:
 * 1. Build MarketContext via MarketContextAnalyzer
 * 2. Retrieve top 5 relevant memories via StrategyMemoryReader
 * 3. Build Claude prompt with system + user prompts
 * 4. Call Anthropic API (claude-sonnet-4-20250514)
 * 5. Parse response as StrategyDSL JSON
 * 6. Validate JSON has all required fields
 * 7. Return GenerationResult with dsl, reasoning, confidence
 */
@Component
public class StrategyGenerationService {

    private static final Logger log = LoggerFactory.getLogger(StrategyGenerationService.class);

    private static final String SYSTEM_PROMPT = """
You are a quantitative trading strategy generator for crypto futures markets.
Your ONLY output is a valid JSON object. No explanation. No markdown. No preamble. Just JSON.

Optimize for: Sharpe ratio > 1.5, max drawdown < 15%, minimum 50 trades over 6 months.
Available indicators: RSI, EMA, SMA, MACD, ATR, BB_UPPER, BB_LOWER, BB_MIDDLE, STOCH_K, VOLUME
Logical operators: AND, OR, NOT
Comparison operators: <, >, <=, >=, ==, !=

Required JSON schema:
{
  "name": "string (descriptive, max 50 chars)",
  "version": "1.0",
  "source": "LLM",
  "indicators": [{"id": "RSI_14", "type": "RSI", "params": {"period": 14}}],
  "entry": "RSI_14 < 30 AND EMA_21 > SMA_50",
  "exit": "RSI_14 > 70",
  "risk": {"stopLossPct": 1.5, "takeProfitPct": 3.0, "positionSizePct": 10.0, "trailingStop": false}
}
""";

    private final StrategyMemoryReader memoryReader;
    private final MarketContextAnalyzer contextAnalyzer;
    private final AnthropicClient anthropicClient;
    private final ObjectMapper objectMapper;

    public StrategyGenerationService(
            StrategyMemoryReader memoryReader,
            MarketContextAnalyzer contextAnalyzer,
            AnthropicClient anthropicClient,
            ObjectMapper objectMapper) {
        this.memoryReader = memoryReader;
        this.contextAnalyzer = contextAnalyzer;
        this.anthropicClient = anthropicClient;
        this.objectMapper = objectMapper;
    }

    /**
     * Generate a new strategy based on the given objective.
     */
    public GenerationResult generate(GenerationRequest request) {
        log.info("Generating strategy for {} {} with objective: {}",
                request.getSymbol(), request.getInterval(), request.getObjective());

        try {
            // Step 1: Build MarketContext
            MarketContext context = contextAnalyzer.analyze(request.getSymbol(), request.getInterval());

            // Step 2: Retrieve top 5 relevant memories
            List<MemoryRecord> memories = memoryReader.retrieve(context, 5);
            log.debug("Retrieved {} memories for context", memories.size());

            // Step 3: Build user prompt
            String userPrompt = buildUserPrompt(request, context, memories);

            // Step 4: Call Anthropic API
            String responseText = anthropicClient.generateStrategy(SYSTEM_PROMPT, userPrompt);
            if (responseText == null || responseText.isEmpty()) {
                log.error("Empty response from Anthropic API");
                return GenerationResult.error("Empty response from AI model");
            }

            // Step 5: Parse response as JSON
            String jsonText = extractJsonFromResponse(responseText);
            if (jsonText == null) {
                log.error("Could not extract JSON from response");
                return GenerationResult.error("Invalid JSON format in response");
            }

            // Step 6: Validate JSON structure
            if (!validateStrategyJson(jsonText)) {
                return GenerationResult.error("Generated strategy missing required fields");
            }

            // Step 7: Return result
            GenerationResult result = GenerationResult.builder()
                    .dsl(jsonText)
                    .reasoning("Generated from " + memories.size() + " memories")
                    .confidence(determineConfidence(jsonText))
                    .retrievedMemoryCount(memories.size())
                    .build();

            log.info("Successfully generated strategy: {} (confidence={})",
                    extractStrategyName(jsonText), result.getConfidence());

            return result;

        } catch (Exception e) {
            log.error("Strategy generation failed: {}", e.getMessage(), e);
            return GenerationResult.error("Generation failed: " + e.getMessage());
        }
    }

    /**
     * Build the user prompt for Claude.
     */
    private String buildUserPrompt(GenerationRequest request, MarketContext context, List<MemoryRecord> memories) {
        StringBuilder sb = new StringBuilder();

        // Past results section
        sb.append("PAST RESULTS (most relevant to current market conditions):\n");
        for (int i = 0; i < memories.size(); i++) {
            MemoryRecord m = memories.get(i);
            sb.append("--- Memory ").append(i + 1).append(" ---\n");
            sb.append(m.getDocument()).append("\n");
        }

        // Current market section
        sb.append("\nCURRENT MARKET:\n");
        sb.append("Symbol: ").append(request.getSymbol())
          .append(" | Interval: ").append(request.getInterval()).append("\n");
        sb.append("Regime: trend=").append(context.getTrend())
          .append(" volatility=").append(context.getVolatility())
          .append(" session=").append(context.getSession()).append("\n");

        // Objective
        sb.append("\nOBJECTIVE: ").append(request.getObjective());

        return sb.toString();
    }

    /**
     * Extract JSON from Claude's response (handle markdown code blocks).
     */
    private String extractJsonFromResponse(String response) {
        String trimmed = response.trim();

        // Handle markdown code blocks
        if (trimmed.startsWith("```json")) {
            trimmed = trimmed.substring(7);
        } else if (trimmed.startsWith("```")) {
            trimmed = trimmed.substring(3);
        }
        if (trimmed.endsWith("```")) {
            trimmed = trimmed.substring(0, trimmed.length() - 3);
        }

        trimmed = trimmed.trim();

        // Validate it's valid JSON
        try {
            objectMapper.readTree(trimmed);
            return trimmed;
        } catch (Exception e) {
            log.warn("Response is not valid JSON: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Validate that the JSON has all required fields.
     */
    private boolean validateStrategyJson(String json) {
        try {
            JsonNode root = objectMapper.readTree(json);

            // Required fields: name, version, source, indicators, entry, exit, risk
            String[] requiredFields = {"name", "version", "source", "indicators", "entry", "exit", "risk"};
            for (String field : requiredFields) {
                if (!root.has(field)) {
                    log.warn("Missing required field: {}", field);
                    return false;
                }
            }

            // Validate indicators is an array
            if (!root.path("indicators").isArray()) {
                log.warn("indicators field is not an array");
                return false;
            }

            // Validate risk has required subfields
            JsonNode risk = root.path("risk");
            if (!risk.has("stopLossPct") || !risk.has("takeProfitPct") || !risk.has("positionSizePct")) {
                log.warn("risk object missing required fields");
                return false;
            }

            return true;
        } catch (Exception e) {
            log.error("JSON validation failed: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Determine confidence level based on strategy characteristics.
     */
    private String determineConfidence(String json) {
        try {
            JsonNode root = objectMapper.readTree(json);

            // Higher confidence if strategy has more indicators (more specific)
            int indicatorCount = root.path("indicators").size();
            String entry = root.path("entry").asText();
            String exit = root.path("exit").asText();

            // Check for reasonable entry/exit complexity
            boolean hasEntryConditions = entry.contains("AND") || entry.contains("OR");
            boolean hasExitConditions = !exit.isEmpty() && exit.length() > 5;

            if (indicatorCount >= 2 && hasEntryConditions && hasExitConditions) {
                return "HIGH";
            } else if (indicatorCount >= 1 && !entry.isEmpty()) {
                return "MEDIUM";
            } else {
                return "LOW";
            }
        } catch (Exception e) {
            return "LOW";
        }
    }

    /**
     * Extract strategy name from JSON for logging.
     */
    private String extractStrategyName(String json) {
        try {
            JsonNode root = objectMapper.readTree(json);
            return root.path("name").asText();
        } catch (Exception e) {
            return "unknown";
        }
    }
}
