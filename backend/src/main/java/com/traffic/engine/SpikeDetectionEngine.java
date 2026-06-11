package com.traffic.engine;

import com.traffic.model.TrafficAlert;
import com.traffic.repository.RedisRepo;
import com.traffic.service.AlertService;
import org.springframework.stereotype.Component;
import java.util.Map;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Spike Detection Engine using Exponential Moving Average (EMA).
 *
 * Algorithm:
 *   1. Maintain a rolling EMA baseline of RPS
 *   2. Compare current second's RPS to baseline
 *   3. If ratio exceeds SPIKE_MULTIPLIER threshold → fire SPIKE_DETECTED alert
 *   4. Sustain spike state until traffic returns below 1.5x baseline
 *
 * This is the same approach used by Cloudflare's DDoS detection layer.
 */
@Component
public class SpikeDetectionEngine {

    private static final double EMA_ALPHA        = 0.1;    // smoothing factor
    private static final double SPIKE_MULTIPLIER = 3.0;    // 3x baseline = spike
    private static final double RESUME_MULTIPLIER= 1.5;    // return to normal
    private static final long   MIN_BASELINE_RPS = 5L;     // ignore if traffic too low

    private final RedisRepo   redisRepo;
    private final AlertService alertService;

    private final AtomicBoolean spikeActive      = new AtomicBoolean(false);
    private final AtomicLong    spikeStartTime   = new AtomicLong(0);
    private volatile double     currentRPS       = 0.0;
    private volatile double     spikeMultiplier  = 1.0;
    private volatile String     spikeEndpoint    = "";

    public SpikeDetectionEngine(RedisRepo redisRepo, AlertService alertService) {
        this.redisRepo    = redisRepo;
        this.alertService = alertService;
    }

    /**
     * Called per-event from the processor engine.
     * Lightweight — only evaluates once per second bucket.
     */
    public void evaluate(long epochSecond) {
        long rps     = redisRepo.getRPS(epochSecond);
        double baseline = redisRepo.getBaseline();

        currentRPS = rps;

        // Update EMA baseline
        double newBaseline = EMA_ALPHA * rps + (1 - EMA_ALPHA) * baseline;
        redisRepo.setBaseline(newBaseline);

        if (baseline < MIN_BASELINE_RPS) return;

        double ratio = rps / baseline;
        spikeMultiplier = ratio;

        if (!spikeActive.get() && ratio >= SPIKE_MULTIPLIER) {
            spikeActive.set(true);
            spikeStartTime.set(System.currentTimeMillis());
            spikeEndpoint = getMostActiveEndpoint();

            alertService.fireAlert(TrafficAlert.builder()
                    .type(TrafficAlert.AlertType.SPIKE_DETECTED)
                    .severity(ratio >= 10.0
                            ? TrafficAlert.Severity.EMERGENCY
                            : ratio >= 5.0
                            ? TrafficAlert.Severity.CRITICAL
                            : TrafficAlert.Severity.WARNING)
                    .title("Traffic Spike Detected")
                    .message(String.format("RPS jumped to %.1fx above baseline (%.0f vs %.0f baseline)",
                            ratio, (double) rps, baseline))
                    .affectedEndpoint(spikeEndpoint)
                    .currentValue(rps)
                    .thresholdValue(baseline)
                    .unit("rps")
                    .build());

        } else if (spikeActive.get() && ratio < RESUME_MULTIPLIER) {
            spikeActive.set(false);
            spikeMultiplier = 1.0;
        }

        // DDoS detection: spike sustained for > 30 seconds
        if (spikeActive.get()) {
            long duration = (System.currentTimeMillis() - spikeStartTime.get()) / 1000;
            if (duration > 30 && duration % 30 == 0) {
                alertService.fireAlert(TrafficAlert.builder()
                        .type(TrafficAlert.AlertType.DDoS_SUSPECTED)
                        .severity(TrafficAlert.Severity.EMERGENCY)
                        .title("Possible DDoS Attack")
                        .message(String.format(
                                "Spike sustained for %d seconds at %.1fx baseline. Possible DDoS.", duration, ratio))
                        .affectedEndpoint(spikeEndpoint)
                        .currentValue(rps)
                        .thresholdValue(baseline)
                        .unit("rps")
                        .build());
            }
        }
    }

    private String getMostActiveEndpoint() {
        Map<String, Long> top = redisRepo.getTopEndpoints(1);
        return top.isEmpty() ? "unknown" : top.keySet().iterator().next();
    }

    public boolean isSpikeActive()     { return spikeActive.get(); }
    public double  getSpikeMultiplier(){ return spikeMultiplier; }
    public String  getSpikeEndpoint()  { return spikeEndpoint; }
    public double  getCurrentRPS()     { return currentRPS; }
}
