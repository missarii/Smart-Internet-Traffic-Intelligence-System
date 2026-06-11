package com.traffic.engine;

import com.traffic.model.TrafficAlert;
import com.traffic.repository.RedisRepo;
import com.traffic.service.AlertService;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Load Analyzer — evaluates per-endpoint health and identifies bottlenecks.
 *
 * A service is considered a bottleneck if:
 *   - avg latency > LATENCY_BOTTLENECK_MS threshold, OR
 *   - error rate > ERROR_RATE_THRESHOLD
 *
 * Runs as a scheduled task (see TrafficAnalyzerService).
 */
@Component
public class LoadAnalyzer {

    private static final double LATENCY_BOTTLENECK_MS = 500.0;
    private static final double ERROR_RATE_THRESHOLD  = 0.15;  // 15%

    private final RedisRepo   redisRepo;
    private final AlertService alertService;

    public LoadAnalyzer(RedisRepo redisRepo, AlertService alertService) {
        this.redisRepo    = redisRepo;
        this.alertService = alertService;
    }

    /**
     * Returns system health status based on overall error rate and latency.
     */
    public String evaluateSystemHealth(double errorRate, double avgLatencyMs) {
        if (errorRate > 0.5 || avgLatencyMs > 2000)      return "CRITICAL";
        if (errorRate > 0.3 || avgLatencyMs > 1000)      return "OVERLOAD";
        if (errorRate > 0.1 || avgLatencyMs > 500)       return "DEGRADED";
        return "HEALTHY";
    }

    /**
     * Check for endpoint-level bottlenecks using Redis latency data.
     * Fires ENDPOINT_BOTTLENECK alerts for identified hot spots.
     */
    public void analyzeEndpoints() {
        Map<String, Long> endpoints = redisRepo.getTopEndpoints(20);

        for (String endpoint : endpoints.keySet()) {
            List<Object> latencies = redisRepo.getLatencies(100);
            if (latencies.isEmpty()) continue;

            double avg = latencies.stream()
                    .mapToLong(l -> Long.parseLong(l.toString()))
                    .average()
                    .orElse(0);

            if (avg > LATENCY_BOTTLENECK_MS) {
                alertService.fireAlert(TrafficAlert.builder()
                        .type(TrafficAlert.AlertType.ENDPOINT_BOTTLENECK)
                        .severity(avg > 1500
                                ? TrafficAlert.Severity.CRITICAL
                                : TrafficAlert.Severity.WARNING)
                        .title("Endpoint Bottleneck Detected")
                        .message(String.format("Endpoint %s avg latency %.0f ms (threshold: %.0f ms)",
                                endpoint, avg, LATENCY_BOTTLENECK_MS))
                        .affectedEndpoint(endpoint)
                        .currentValue(avg)
                        .thresholdValue(LATENCY_BOTTLENECK_MS)
                        .unit("ms")
                        .build());
            }
        }
    }

    /**
     * Compute global latency percentiles from Redis list.
     */
    public double[] computeLatencyPercentiles() {
        List<Object> latencies = redisRepo.getLatencies(5000);
        if (latencies.isEmpty()) return new double[]{0, 0, 0};

        long[] sorted = latencies.stream()
                .mapToLong(l -> Long.parseLong(l.toString()))
                .sorted()
                .toArray();

        double p50 = sorted[(int)(sorted.length * 0.50)];
        double p95 = sorted[(int)(sorted.length * 0.95)];
        double p99 = sorted[Math.min(sorted.length - 1, (int)(sorted.length * 0.99))];

        return new double[]{p50, p95, p99};
    }
}
