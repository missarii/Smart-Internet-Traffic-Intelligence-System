package com.traffic.engine;

import com.traffic.model.TrafficEvent;
import com.traffic.repository.ClickHouseRepo;
import com.traffic.repository.RedisRepo;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Core multithreaded request processing engine.
 *
 * Uses a bounded thread pool (ExecutorService) to process each incoming
 * TrafficEvent asynchronously. A bounded queue prevents memory exhaustion
 * under extreme load — events beyond capacity are dropped with logging,
 * mimicking production-grade backpressure.
 *
 * Pipeline per event:
 *   1. Increment RPS counter in Redis
 *   2. Update endpoint stats
 *   3. Record latency
 *   4. Update session
 *   5. Persist to ClickHouse
 *   6. Forward to downstream analyzers
 */
@Component
public class RequestProcessorEngine {

    private static final int THREAD_POOL_SIZE = 8;
    private static final int QUEUE_CAPACITY   = 10_000;
    private static final int MAX_IP_RPS       = 100;  // per-minute rate limit

    private ExecutorService executor;
    private final BlockingQueue<TrafficEvent> eventQueue = new LinkedBlockingQueue<>(QUEUE_CAPACITY);

    private final RedisRepo redisRepo;
    private final ClickHouseRepo clickHouseRepo;
    private final SpikeDetectionEngine spikeDetector;

    private final AtomicLong processedCount  = new AtomicLong(0);
    private final AtomicLong droppedCount    = new AtomicLong(0);

    public RequestProcessorEngine(RedisRepo redisRepo,
                                   ClickHouseRepo clickHouseRepo,
                                   SpikeDetectionEngine spikeDetector) {
        this.redisRepo       = redisRepo;
        this.clickHouseRepo  = clickHouseRepo;
        this.spikeDetector   = spikeDetector;
    }

    @PostConstruct
    public void start() {
        executor = new ThreadPoolExecutor(
                THREAD_POOL_SIZE, THREAD_POOL_SIZE,
                0L, TimeUnit.MILLISECONDS,
                new LinkedBlockingQueue<>(QUEUE_CAPACITY),
                new ThreadFactory() {
                    int n = 0;
                    public Thread newThread(Runnable r) {
                        Thread t = new Thread(r, "traffic-processor-" + n++);
                        t.setDaemon(true);
                        return t;
                    }
                },
                new ThreadPoolExecutor.DiscardPolicy()  // drop under extreme load
        );
        System.out.println("[Engine] RequestProcessorEngine started with " + THREAD_POOL_SIZE + " threads.");
    }

    @PreDestroy
    public void shutdown() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Submit a traffic event for async processing.
     * Non-blocking — returns immediately.
     */
    public void submit(TrafficEvent event) {
        executor.submit(() -> process(event));
    }

    private void process(TrafficEvent event) {
        try {
            long epochSecond = event.getTimestamp() / 1000;

            // 1. RPS counter
            redisRepo.incrementRPS(epochSecond);
            redisRepo.incrementTotalRequests();

            // 2. Per-IP rate limiting check
            long ipCount = redisRepo.incrementIPRequests(event.getIpAddress());
            if (ipCount > MAX_IP_RPS) {
                redisRepo.blockIP(event.getIpAddress(), 3600);
                event.setAnomaly(true);
            }

            // 3. Endpoint stats
            redisRepo.incrementEndpoint(event.getEndpoint());
            redisRepo.recordEndpointLatency(event.getEndpoint(), event.getLatencyMs());

            // 4. Error tracking
            if (event.getStatusCode() >= 400) {
                redisRepo.incrementErrorCode(event.getStatusCode());
                redisRepo.incrementEndpointError(event.getEndpoint());
                redisRepo.incrementErrorCount();
            }

            // 5. Global latency
            redisRepo.recordGlobalLatency(event.getLatencyMs());

            // 6. Region distribution
            if (event.getRegion() != null) {
                redisRepo.incrementRegion(event.getRegion());
            }

            // 7. Bypass tracking
            if (event.isBypass()) {
                redisRepo.incrementBypass();
                if (event.getBypassType() != null) {
                    redisRepo.incrementBypassType(event.getBypassType());
                }
            }

            // 8. Session tracking
            redisRepo.trackActiveSession(event.getSessionId());

            // 9. Spike detection (non-blocking, fire & check)
            spikeDetector.evaluate(epochSecond);

            // 10. Persist to ClickHouse
            clickHouseRepo.insertEvent(event);

            processedCount.incrementAndGet();

        } catch (Exception e) {
            System.err.println("[Engine] Error processing event: " + e.getMessage());
        }
    }

    public long getProcessedCount() { return processedCount.get(); }
    public long getDroppedCount()   { return droppedCount.get(); }
}
