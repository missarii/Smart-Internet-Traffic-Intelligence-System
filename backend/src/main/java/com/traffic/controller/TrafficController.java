package com.traffic.controller;

import com.traffic.model.TrafficAlert;
import com.traffic.model.TrafficEvent;
import com.traffic.model.UserSession;
import com.traffic.repository.ClickHouseRepo;
import com.traffic.repository.RedisRepo;
import com.traffic.service.AlertService;
import com.traffic.service.BypassDetectionService;
import com.traffic.service.SessionTrackingService;
import com.traffic.service.TrafficSimulatorService;
import com.traffic.engine.RequestProcessorEngine;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * REST API for the traffic intelligence platform.
 *
 * Endpoints:
 *   POST /api/traffic/ingest        → ingest a real external event
 *   GET  /api/traffic/stats         → current traffic statistics
 *   GET  /api/traffic/endpoints     → top endpoint breakdown
 *   GET  /api/traffic/regions       → region distribution
 *   GET  /api/traffic/errors        → error code breakdown
 *   GET  /api/traffic/bypass        → bypass type breakdown
 *   GET  /api/traffic/alerts        → recent alert history
 *   POST /api/traffic/spike/trigger → manually trigger a spike (demo)
 *   POST /api/traffic/ip/block      → manually block an IP
 *   GET  /api/traffic/sessions/{id} → get session details
 *   GET  /api/analytics/timeseries  → historical traffic from ClickHouse
 *   GET  /api/analytics/anomalies   → anomalous IPs from ClickHouse
 */
@RestController
@RequestMapping("/api")
public class TrafficController {

    private final RedisRepo              redisRepo;
    private final ClickHouseRepo         clickHouseRepo;
    private final AlertService           alertService;
    private final RequestProcessorEngine engine;
    private final BypassDetectionService bypassDetection;
    private final SessionTrackingService sessionTracking;
    private final TrafficSimulatorService simulator;

    public TrafficController(RedisRepo redisRepo,
                              ClickHouseRepo clickHouseRepo,
                              AlertService alertService,
                              RequestProcessorEngine engine,
                              BypassDetectionService bypassDetection,
                              SessionTrackingService sessionTracking,
                              TrafficSimulatorService simulator) {
        this.redisRepo      = redisRepo;
        this.clickHouseRepo = clickHouseRepo;
        this.alertService   = alertService;
        this.engine         = engine;
        this.bypassDetection= bypassDetection;
        this.sessionTracking= sessionTracking;
        this.simulator      = simulator;
    }

    // ─── Ingest ──────────────────────────────────────────────────────────────

    @PostMapping("/traffic/ingest")
    public ResponseEntity<Map<String, Object>> ingestEvent(@RequestBody TrafficEvent event) {
        if (event.getEventId() == null) event.setEventId(UUID.randomUUID().toString());
        if (event.getTimestamp() == 0)  event.setTimestamp(System.currentTimeMillis());

        var session = sessionTracking.getOrCreate(
                event.getSessionId(), event.getIpAddress(), event.getUserAgent());
        bypassDetection.analyze(event);
        sessionTracking.update(session, event);
        engine.submit(event);

        return ResponseEntity.ok(Map.of("status", "accepted", "eventId", event.getEventId()));
    }

    // ─── Stats ───────────────────────────────────────────────────────────────

    @GetMapping("/traffic/stats")
    public ResponseEntity<Map<String, Object>> getStats() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("totalRequests",  redisRepo.getTotalRequests());
        stats.put("totalErrors",    redisRepo.getTotalErrors());
        stats.put("currentRPS",     redisRepo.getAverageRPS(5));
        stats.put("activeSessions", redisRepo.getActiveSessions());
        stats.put("blockedIPs",     redisRepo.getBlockedIPCount());
        stats.put("bypassCount",    redisRepo.getBypassCount());
        stats.put("baseline",       redisRepo.getBaseline());
        stats.put("processedEvents",engine.getProcessedCount());
        stats.put("alertCount",     alertService.getAlertCount());
        return ResponseEntity.ok(stats);
    }

    @GetMapping("/traffic/endpoints")
    public ResponseEntity<Map<String, Long>> getTopEndpoints(
            @RequestParam(defaultValue = "10") int n) {
        return ResponseEntity.ok(redisRepo.getTopEndpoints(n));
    }

    @GetMapping("/traffic/regions")
    public ResponseEntity<Map<String, Long>> getRegions() {
        return ResponseEntity.ok(redisRepo.getRegionDistribution());
    }

    @GetMapping("/traffic/errors")
    public ResponseEntity<Map<String, Long>> getErrors() {
        return ResponseEntity.ok(redisRepo.getErrorCodeDistribution());
    }

    @GetMapping("/traffic/bypass")
    public ResponseEntity<Map<String, Long>> getBypassTypes() {
        return ResponseEntity.ok(redisRepo.getBypassTypes());
    }

    // ─── Alerts ──────────────────────────────────────────────────────────────

    @GetMapping("/traffic/alerts")
    public ResponseEntity<List<TrafficAlert>> getAlerts(
            @RequestParam(defaultValue = "50") int n) {
        return ResponseEntity.ok(alertService.getRecentAlerts(n));
    }

    // ─── Control ─────────────────────────────────────────────────────────────

    @PostMapping("/traffic/spike/trigger")
    public ResponseEntity<Map<String, String>> triggerSpike(
            @RequestParam(defaultValue = "true") boolean active) {
        simulator.setManualSpike(active);
        return ResponseEntity.ok(Map.of("status", active ? "spike activated" : "spike deactivated"));
    }

    @PostMapping("/traffic/ip/block")
    public ResponseEntity<Map<String, String>> blockIP(
            @RequestParam String ip,
            @RequestParam(defaultValue = "3600") long ttlSeconds) {
        redisRepo.blockIP(ip, ttlSeconds);
        return ResponseEntity.ok(Map.of("status", "blocked", "ip", ip, "ttl", String.valueOf(ttlSeconds)));
    }

    // ─── Session ─────────────────────────────────────────────────────────────

    @GetMapping("/traffic/sessions/{sessionId}")
    public ResponseEntity<?> getSession(@PathVariable String sessionId) {
        return redisRepo.getSession(sessionId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    // ─── Analytics (ClickHouse) ───────────────────────────────────────────────

    @GetMapping("/analytics/timeseries")
    public ResponseEntity<List<Map<String, Object>>> getTimeSeries(
            @RequestParam(defaultValue = "60") int minutesBack) {
        return ResponseEntity.ok(clickHouseRepo.getTrafficTimeSeries(minutesBack));
    }

    @GetMapping("/analytics/anomalies")
    public ResponseEntity<List<Map<String, Object>>> getAnomalies(
            @RequestParam(defaultValue = "20") int limit) {
        return ResponseEntity.ok(clickHouseRepo.getAnomalousIPs(limit));
    }

    @GetMapping("/analytics/top-errors")
    public ResponseEntity<List<Map<String, Object>>> getTopErrors(
            @RequestParam(defaultValue = "10") int limit) {
        return ResponseEntity.ok(clickHouseRepo.getTopEndpointsByErrors(limit));
    }
}
