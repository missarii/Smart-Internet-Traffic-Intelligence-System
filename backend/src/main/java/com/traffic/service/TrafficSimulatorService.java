package com.traffic.service;

import com.traffic.engine.RequestProcessorEngine;
import com.traffic.model.TrafficEvent;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Traffic Simulation Service — generates realistic synthetic traffic for demo.
 *
 * Simulates:
 *   - Normal steady-state traffic
 *   - Sudden spike events (every 60 seconds)
 *   - DDoS-like burst from single IP
 *   - Bypass attempts
 *   - Failover behavior
 *   - Geographic distribution
 */
@Service
public class TrafficSimulatorService {

    private static final String[] ENDPOINTS = {
            "/api/products", "/api/users", "/api/orders", "/api/cart",
            "/api/auth/login", "/api/auth/logout", "/api/payments",
            "/api/search", "/api/recommendations", "/api/inventory",
            "/api/reviews", "/api/notifications", "/api/profile",
            "/internal/health", "/api/checkout", "/api/shipping"
    };

    private static final String[] IPS = {
            "192.168.1.10", "10.0.0.5", "172.16.0.8", "203.0.113.42",
            "198.51.100.7", "45.33.32.156", "8.8.8.8", "104.26.10.30",
            "66.249.66.1", "157.240.200.35", "31.13.72.36", "151.101.1.140"
    };

    private static final String[] REGIONS = {
            "us-east", "us-west", "eu-west", "ap-south", "ap-northeast",
            "sa-east", "ca-central", "me-south"
    };

    private static final String[] USER_AGENTS = {
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) Chrome/124",
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) Safari/605",
            "Mozilla/5.0 (Linux; Android 13) Mobile Chrome/124",
            "curl/7.81.0",
            "python-requests/2.31.0",
            "Googlebot/2.1"
    };

    private static final int[] STATUS_CODES = {200, 200, 200, 200, 200, 201, 304, 400, 401, 403, 404, 429, 500, 502, 503};

    private final RequestProcessorEngine engine;
    private final BypassDetectionService bypassDetection;
    private final SessionTrackingService sessionTracking;

    private volatile boolean spikeMode = false;
    private long spikeEndTime = 0;
    private String attackerIP = IPS[ThreadLocalRandom.current().nextInt(IPS.length)];

    public TrafficSimulatorService(RequestProcessorEngine engine,
                                    BypassDetectionService bypassDetection,
                                    SessionTrackingService sessionTracking) {
        this.engine           = engine;
        this.bypassDetection  = bypassDetection;
        this.sessionTracking  = sessionTracking;
    }

    /**
     * Generates normal traffic — 10-30 events per second.
     */
    @Scheduled(fixedRate = 50)  // every 50ms = ~20 events/sec
    public void generateNormalTraffic() {
        int count = spikeMode ? ThreadLocalRandom.current().nextInt(50, 150) : 1;
        for (int i = 0; i < count; i++) {
            emitEvent(false);
        }
    }

    /**
     * Triggers a traffic spike every 90 seconds, lasting 15 seconds.
     */
    @Scheduled(fixedDelay = 90_000, initialDelay = 30_000)
    public void triggerSpike() {
        spikeMode   = true;
        spikeEndTime = System.currentTimeMillis() + 15_000;
        attackerIP  = IPS[ThreadLocalRandom.current().nextInt(IPS.length)];
        System.out.println("[Simulator] SPIKE triggered from IP: " + attackerIP);

        // Auto-clear spike after 15 seconds
        new Thread(() -> {
            try { Thread.sleep(15_000); } catch (InterruptedException ignored) {}
            spikeMode = false;
            System.out.println("[Simulator] SPIKE ended.");
        }).start();
    }

    /**
     * Generates bypass attempts every 20 seconds.
     */
    @Scheduled(fixedRate = 20_000, initialDelay = 10_000)
    public void generateBypassAttempts() {
        String ip = IPS[ThreadLocalRandom.current().nextInt(IPS.length)];
        String sessionId = "bypass-" + UUID.randomUUID();

        // Direct hit to protected endpoint
        TrafficEvent bypass = createEvent(sessionId, ip,
                "/internal/health", "GET", 200, 50,
                "curl/7.81.0", "us-east");
        bypass.setBypass(true);
        bypass.setBypassType("DIRECT");
        bypassDetection.analyze(bypass);
        engine.submit(bypass);

        // Retry burst
        for (int i = 0; i < 6; i++) {
            TrafficEvent retry = createEvent(sessionId, ip,
                    "/api/auth/login", "POST", 401,
                    ThreadLocalRandom.current().nextInt(100, 800),
                    "python-requests/2.31.0", "us-east");
            bypassDetection.analyze(retry);
            engine.submit(retry);
        }
    }

    private void emitEvent(boolean isMalicious) {
        ThreadLocalRandom rng = ThreadLocalRandom.current();
        String ip        = isMalicious ? attackerIP : IPS[rng.nextInt(IPS.length)];
        String sessionId = "sess-" + Math.abs(ip.hashCode() % 100);
        String endpoint  = ENDPOINTS[rng.nextInt(ENDPOINTS.length)];
        String method    = rng.nextInt(10) < 7 ? "GET" : rng.nextInt(2) == 0 ? "POST" : "PUT";
        int    status    = STATUS_CODES[rng.nextInt(STATUS_CODES.length)];
        long   latency   = rng.nextLong(5, spikeMode ? 2000 : 400);
        String agent     = USER_AGENTS[rng.nextInt(USER_AGENTS.length)];
        String region    = REGIONS[rng.nextInt(REGIONS.length)];

        TrafficEvent event = createEvent(sessionId, ip, endpoint, method, status, latency, agent, region);

        // Occasionally mark as anomaly/bypass
        if (rng.nextInt(20) == 0) {
            event.setAnomaly(true);
        }

        var session = sessionTracking.getOrCreate(sessionId, ip, agent);
        bypassDetection.analyze(event);
        sessionTracking.update(session, event);
        engine.submit(event);
    }

    private TrafficEvent createEvent(String sessionId, String ip, String endpoint,
                                      String method, int status, long latency,
                                      String agent, String region) {
        return TrafficEvent.create(sessionId, ip, endpoint, method, status, latency, agent, region);
    }

    public void setManualSpike(boolean active) {
        spikeMode = active;
    }
}
