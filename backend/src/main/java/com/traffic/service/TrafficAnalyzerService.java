package com.traffic.service;

import com.traffic.model.TrafficAlert;
import com.traffic.model.TrafficReport;
import com.traffic.repository.RedisRepo;
import com.traffic.engine.LoadAnalyzer;
import com.traffic.engine.SpikeDetectionEngine;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * Core traffic analysis service — runs every second to assemble the
 * TrafficReport and broadcast it over WebSocket to all connected clients.
 *
 * Also triggers load analysis every 10 seconds.
 */
@Service
public class TrafficAnalyzerService {

    private static final double HIGH_ERROR_RATE_THRESHOLD = 0.20; // 20%

    private final RedisRepo              redisRepo;
    private final LoadAnalyzer           loadAnalyzer;
    private final SpikeDetectionEngine   spikeDetector;
    private final AlertService           alertService;
    private final SimpMessagingTemplate  messagingTemplate;

    public TrafficAnalyzerService(RedisRepo redisRepo,
                                   LoadAnalyzer loadAnalyzer,
                                   SpikeDetectionEngine spikeDetector,
                                   AlertService alertService,
                                   SimpMessagingTemplate messagingTemplate) {
        this.redisRepo         = redisRepo;
        this.loadAnalyzer      = loadAnalyzer;
        this.spikeDetector     = spikeDetector;
        this.alertService      = alertService;
        this.messagingTemplate = messagingTemplate;
    }

    /**
     * Runs every 1 second — assembles a full TrafficReport and pushes to WebSocket.
     */
    @Scheduled(fixedRate = 1000)
    public void broadcastTrafficReport() {
        try {
            long now = System.currentTimeMillis();
            long epochSecond = now / 1000;

            long totalRequests = redisRepo.getTotalRequests();
            long totalErrors   = redisRepo.getTotalErrors();
            long activeSessions= redisRepo.getActiveSessions();
            long blockedIPs    = redisRepo.getBlockedIPCount();
            long bypassCount   = redisRepo.getBypassCount();

            double currentRPS  = redisRepo.getAverageRPS(5); // 5-sec avg
            double errorRate   = totalRequests == 0 ? 0 : (double) totalErrors / totalRequests;

            double[] percentiles = loadAnalyzer.computeLatencyPercentiles();

            // Build per-endpoint stats
            Map<String, Long> topEndpoints = redisRepo.getTopEndpoints(10);
            Map<String, TrafficReport.EndpointStats> endpointBreakdown = new LinkedHashMap<>();
            for (Map.Entry<String, Long> entry : topEndpoints.entrySet()) {
                endpointBreakdown.put(entry.getKey(), TrafficReport.EndpointStats.builder()
                        .endpoint(entry.getKey())
                        .requestCount(entry.getValue())
                        .avgLatencyMs(percentiles[0])
                        .build());
            }

            String systemStatus = loadAnalyzer.evaluateSystemHealth(errorRate, percentiles[0]);

            // Fire high error rate alert
            if (errorRate > HIGH_ERROR_RATE_THRESHOLD) {
                alertService.fireAlert(TrafficAlert.builder()
                        .type(TrafficAlert.AlertType.HIGH_ERROR_RATE)
                        .severity(errorRate > 0.5
                                ? TrafficAlert.Severity.CRITICAL
                                : TrafficAlert.Severity.WARNING)
                        .title("High Error Rate")
                        .message(String.format("Error rate is %.1f%% (threshold: %.0f%%)",
                                errorRate * 100, HIGH_ERROR_RATE_THRESHOLD * 100))
                        .currentValue(errorRate * 100)
                        .thresholdValue(HIGH_ERROR_RATE_THRESHOLD * 100)
                        .unit("%")
                        .build());
            }

            TrafficReport report = TrafficReport.builder()
                    .timestamp(now)
                    .totalRequests(totalRequests)
                    .requestsPerSecond(currentRPS)
                    .avgLatencyMs(percentiles[0])
                    .p95LatencyMs(percentiles[1])
                    .p99LatencyMs(percentiles[2])
                    .errorRate(errorRate)
                    .activeSessions(activeSessions)
                    .blockedIPs(blockedIPs)
                    .bypassAttempts(bypassCount)
                    .spikeActive(spikeDetector.isSpikeActive())
                    .spikeMultiplier(spikeDetector.getSpikeMultiplier())
                    .spikeEndpoint(spikeDetector.getSpikeEndpoint())
                    .endpointBreakdown(endpointBreakdown)
                    .regionDistribution(redisRepo.getRegionDistribution())
                    .errorCodeDistribution(redisRepo.getErrorCodeDistribution())
                    .bypassTypeDistribution(redisRepo.getBypassTypes())
                    .systemStatus(systemStatus)
                    .build();

            messagingTemplate.convertAndSend("/topic/traffic", report);

        } catch (Exception e) {
            System.err.println("[Analyzer] Broadcast error: " + e.getMessage());
        }
    }

    /**
     * Runs every 10 seconds — deeper endpoint load analysis.
     */
    @Scheduled(fixedRate = 10_000)
    public void analyzeLoad() {
        loadAnalyzer.analyzeEndpoints();
    }
}
