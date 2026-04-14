package com.tradingservice.tradingassistantbackend.controller;

import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;

/**
 * Thin reverse-proxy for downstream microservices not yet running.
 * Returns 503 with a clear message if the downstream is unreachable.
 *
 * Routes:
 *   /api/strategies/** → strategy-service (8082)
 *   /api/sim/**        → sim-service      (8086)
 *   /api/ai/**         → ai-service       (8083)
 */
@RestController
public class ProxyController {

    private static final Logger log = LoggerFactory.getLogger(ProxyController.class);
    private static final Duration PROXY_TIMEOUT = Duration.ofSeconds(30);

    private final WebClient webClient;
    private final String strategyServiceUrl;
    private final String simServiceUrl;
    private final String aiServiceUrl;

    public ProxyController(
        WebClient.Builder webClientBuilder,
        @Value("${proxy.strategy-service.url:http://localhost:8083}") String strategyServiceUrl,
        @Value("${proxy.sim-service.url:http://localhost:8086}") String simServiceUrl,
        @Value("${proxy.ai-service.url:http://localhost:8083}") String aiServiceUrl
    ) {
        this.webClient = webClientBuilder.build();
        this.strategyServiceUrl = strategyServiceUrl;
        this.simServiceUrl = simServiceUrl;
        this.aiServiceUrl = aiServiceUrl;
    }

    @RequestMapping("/api/strategies/**")
    public ResponseEntity<String> proxyStrategies(
        HttpServletRequest request,
        @RequestBody(required = false) String body
    ) {
        return proxy(request, body, strategyServiceUrl, "strategy-service");
    }

    @RequestMapping("/api/sim/**")
    public ResponseEntity<String> proxySim(
        HttpServletRequest request,
        @RequestBody(required = false) String body
    ) {
        return proxy(request, body, simServiceUrl, "sim-service");
    }

    @RequestMapping("/api/ai/**")
    public ResponseEntity<String> proxyAi(
        HttpServletRequest request,
        @RequestBody(required = false) String body
    ) {
        return proxy(request, body, aiServiceUrl, "ai-service");
    }

    // -------------------------------------------------------------------------

    private ResponseEntity<String> proxy(
        HttpServletRequest request,
        String body,
        String baseUrl,
        String serviceName
    ) {
        try {
            String path = request.getRequestURI();
            String query = request.getQueryString();
            String targetUrl = baseUrl + path + (query != null ? "?" + query : "");

            HttpMethod method = HttpMethod.valueOf(request.getMethod());

            WebClient.RequestBodySpec requestSpec = webClient
                .method(method)
                .uri(URI.create(targetUrl));

            // Forward relevant headers (skip hop-by-hop)
            java.util.Enumeration<String> headerNames = request.getHeaderNames();
            while (headerNames.hasMoreElements()) {
                String name = headerNames.nextElement();
                if (!isHopByHopHeader(name)) {
                    requestSpec.header(name, request.getHeader(name));
                }
            }

            WebClient.ResponseSpec responseSpec = body != null && !body.isBlank()
                ? requestSpec.bodyValue(body).retrieve()
                : requestSpec.retrieve();

            String responseBody = responseSpec
                .bodyToMono(String.class)
                .timeout(PROXY_TIMEOUT)
                .block();

            return ResponseEntity.ok(responseBody);

        } catch (WebClientRequestException e) {
            log.warn("Downstream {} is unreachable: {}", serviceName, e.getMessage());
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body("{\"error\":\"" + serviceName + " is not available\",\"status\":503}");
        } catch (Exception e) {
            log.error("Proxy error for {}: {}", serviceName, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body("{\"error\":\"" + serviceName + " is not available\",\"status\":503}");
        }
    }

    private boolean isHopByHopHeader(String name) {
        return name.equalsIgnoreCase(HttpHeaders.CONNECTION)
            || name.equalsIgnoreCase("Keep-Alive")
            || name.equalsIgnoreCase(HttpHeaders.TRANSFER_ENCODING)
            || name.equalsIgnoreCase(HttpHeaders.UPGRADE)
            || name.equalsIgnoreCase(HttpHeaders.HOST);
    }
}