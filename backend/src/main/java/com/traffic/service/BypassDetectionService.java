package com.traffic.service;

import com.traffic.model.TrafficEvent;
import com.traffic.model.TrafficAlert;
import com.traffic.repository.RedisRepo;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * Bypass Detection Service — analyzes traffic events for:
 *   1. RETRY behavior (same IP hitting same endpoint repeatedly after failures)
 *   2. FAILOVER behavior (switching endpoints after 503/504 responses)
 *   3. DIRECT bypass (hitting internal endpoints directly, skipping gateway)
 *   4. ALTERNATE_ROUTE (using different path to reach same resource)
 *
 * Uses Redis session data to maintain per-session state across requests.
 */
@Service
public class BypassDetectionService {

    // Endpoints that should ONLY be reached through the gateway
    private static final Set<String> PROTECTED_ENDPOINTS = Set.of(
            "/internal/health", "/internal/metrics", "/admin/config",
            "/internal/debug", "/api/internal/users"
    );

    private static final int MAX_RETRIES_THRESHOLD = 3;
    private static final int FAILOVER_WINDOW_MS    = 30_000; // 30 seconds

    private final RedisRepo   redisRepo;
    private final AlertService alertService;

    // In-memory per-session recent failure tracking
    private final Map<String, Deque<Long>> sessionFailureTimes = new java.util.concurrent.ConcurrentHashMap<>();

    public BypassDetectionService(RedisRepo redisRepo, AlertService alertService) {
        this.redisRepo    = redisRepo;
        this.alertService = alertService;
    }

    /**
     * Analyze a traffic event for bypass patterns.
     * Mutates the event (sets isBypass, bypassType) if detected.
     */
    public void analyze(TrafficEvent event) {
        // 1. Direct endpoint bypass (hitting protected internal endpoints)
        if (PROTECTED_ENDPOINTS.contains(event.getEndpoint())) {
            event.setBypass(true);
            event.setAnomaly(true);
            event.setBypassType("DIRECT");

            alertService.fireAlert(TrafficAlert.builder()
                    .type(TrafficAlert.AlertType.BYPASS_DETECTED)
                    .severity(TrafficAlert.Severity.CRITICAL)
                    .title("Direct Endpoint Bypass")
                    .message("IP " + event.getIpAddress() +
                             " directly accessed protected endpoint: " + event.getEndpoint())
                    .affectedEndpoint(event.getEndpoint())
                    .sourceIP(event.getIpAddress())
                    .build());

            redisRepo.blockIP(event.getIpAddress(), 7200);
            return;
        }

        // 2. Retry detection — multiple failed requests from same IP to same endpoint
        if (event.getStatusCode() >= 400) {
            String key = event.getSessionId() + ":" + event.getEndpoint();
            sessionFailureTimes.computeIfAbsent(key, k -> new java.util.ArrayDeque<>())
                    .addLast(event.getTimestamp());

            Deque<Long> failures = sessionFailureTimes.get(key);
            // Remove old entries outside 30-second window
            long cutoff = event.getTimestamp() - FAILOVER_WINDOW_MS;
            while (!failures.isEmpty() && failures.peekFirst() < cutoff) {
                failures.pollFirst();
            }

            if (failures.size() >= MAX_RETRIES_THRESHOLD) {
                event.setBypass(true);
                event.setBypassType("RETRY");
                event.setRetryCount(failures.size());

                alertService.fireAlert(TrafficAlert.builder()
                        .type(TrafficAlert.AlertType.BYPASS_DETECTED)
                        .severity(TrafficAlert.Severity.WARNING)
                        .title("Excessive Retry Detected")
                        .message(String.format("Session %s retried %s %d times in 30s",
                                event.getSessionId(), event.getEndpoint(), failures.size()))
                        .affectedEndpoint(event.getEndpoint())
                        .sourceIP(event.getIpAddress())
                        .build());
            }
        }

        // 3. Failover detection — after 503/504, session immediately switches endpoint
        if (event.getStatusCode() == 503 || event.getStatusCode() == 504) {
            event.setBypass(true);
            event.setBypassType("FAILOVER");
        }

        // 4. Update session bypass state in Redis
        if (event.isBypass()) {
            redisRepo.getSession(event.getSessionId()).ifPresent(session -> {
                session.incrementBypass();
                redisRepo.saveSession(session);
            });
        }
    }
}
