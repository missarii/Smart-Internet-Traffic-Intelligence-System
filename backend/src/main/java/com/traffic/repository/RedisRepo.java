package com.traffic.repository;

import com.traffic.model.TrafficEvent;
import com.traffic.model.UserSession;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * Redis repository handling all real-time state.
 *
 * Key schema:
 *   traffic:rps:{epochSecond}          → request count for that second (TTL 300s)
 *   traffic:endpoint:{ep}:count        → total hits per endpoint (sorted set)
 *   traffic:endpoint:{ep}:latency      → running avg latency per endpoint
 *   traffic:endpoint:{ep}:errors       → error count per endpoint
 *   traffic:ip:{ip}:requests           → request count per IP per minute
 *   traffic:ip:{ip}:blocked            → "1" if IP is blocked (TTL 3600s)
 *   traffic:session:{sid}              → UserSession JSON (TTL 1800s)
 *   traffic:bypass:count               → total bypass event counter
 *   traffic:spike:baseline             → rolling baseline RPS
 *   traffic:alerts:active              → set of active alert IDs
 */
@Repository
public class RedisRepo {

    private static final String PREFIX = "traffic:";
    private final RedisTemplate<String, Object> redisTemplate;

    public RedisRepo(RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    // ─── RPS Tracking ────────────────────────────────────────────────────────────

    public void incrementRPS(long epochSecond) {
        String key = PREFIX + "rps:" + epochSecond;
        redisTemplate.opsForValue().increment(key);
        redisTemplate.expire(key, 300, TimeUnit.SECONDS);
    }

    public long getRPS(long epochSecond) {
        Object val = redisTemplate.opsForValue().get(PREFIX + "rps:" + epochSecond);
        return val == null ? 0L : Long.parseLong(val.toString());
    }

    public double getAverageRPS(int windowSeconds) {
        long now = System.currentTimeMillis() / 1000;
        long total = 0;
        for (long i = now - windowSeconds; i <= now; i++) {
            total += getRPS(i);
        }
        return windowSeconds == 0 ? 0 : (double) total / windowSeconds;
    }

    // ─── Endpoint Tracking ───────────────────────────────────────────────────────

    public void incrementEndpoint(String endpoint) {
        redisTemplate.opsForZSet().incrementScore(PREFIX + "endpoints:hits", endpoint, 1);
    }

    public void recordEndpointLatency(String endpoint, long latencyMs) {
        String key = PREFIX + "endpoint:" + endpoint + ":latencies";
        redisTemplate.opsForList().rightPush(key, latencyMs);
        redisTemplate.expire(key, 600, TimeUnit.SECONDS);
        // Keep only last 1000 readings
        redisTemplate.opsForList().trim(key, -1000, -1);
    }

    public void incrementEndpointError(String endpoint) {
        String key = PREFIX + "endpoint:" + endpoint + ":errors";
        redisTemplate.opsForValue().increment(key);
    }

    public Map<String, Long> getTopEndpoints(int n) {
        Set<Object> items = redisTemplate.opsForZSet()
                .reverseRange(PREFIX + "endpoints:hits", 0, n - 1);
        Map<String, Long> result = new LinkedHashMap<>();
        if (items != null) {
            for (Object ep : items) {
                Double score = redisTemplate.opsForZSet()
                        .score(PREFIX + "endpoints:hits", ep);
                result.put(ep.toString(), score == null ? 0L : score.longValue());
            }
        }
        return result;
    }

    // ─── Rate Limiting ───────────────────────────────────────────────────────────

    public long incrementIPRequests(String ip) {
        String key = PREFIX + "ip:" + ip + ":requests";
        Long count = redisTemplate.opsForValue().increment(key);
        if (count != null && count == 1) {
            redisTemplate.expire(key, 60, TimeUnit.SECONDS); // 1-minute window
        }
        return count == null ? 1L : count;
    }

    public void blockIP(String ip, long ttlSeconds) {
        redisTemplate.opsForValue().set(PREFIX + "ip:" + ip + ":blocked", "1",
                Duration.ofSeconds(ttlSeconds));
        redisTemplate.opsForSet().add(PREFIX + "blocked:ips", ip);
    }

    public boolean isIPBlocked(String ip) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(PREFIX + "ip:" + ip + ":blocked"));
    }

    public long getBlockedIPCount() {
        Long size = redisTemplate.opsForSet().size(PREFIX + "blocked:ips");
        return size == null ? 0L : size;
    }

    // ─── Session Tracking ────────────────────────────────────────────────────────

    public void saveSession(UserSession session) {
        String key = PREFIX + "session:" + session.getSessionId();
        redisTemplate.opsForValue().set(key, session, Duration.ofMinutes(30));
    }

    public Optional<UserSession> getSession(String sessionId) {
        Object val = redisTemplate.opsForValue().get(PREFIX + "session:" + sessionId);
        if (val instanceof UserSession s) return Optional.of(s);
        return Optional.empty();
    }

    public void trackActiveSession(String sessionId) {
        redisTemplate.opsForZSet().add(PREFIX + "sessions:active", sessionId,
                System.currentTimeMillis());
    }

    public long getActiveSessions() {
        long cutoff = System.currentTimeMillis() - 300_000; // 5 minutes
        redisTemplate.opsForZSet().removeRangeByScore(PREFIX + "sessions:active", 0, cutoff);
        Long size = redisTemplate.opsForZSet().size(PREFIX + "sessions:active");
        return size == null ? 0L : size;
    }

    // ─── Bypass Tracking ─────────────────────────────────────────────────────────

    public void incrementBypass() {
        redisTemplate.opsForValue().increment(PREFIX + "bypass:count");
    }

    public long getBypassCount() {
        Object val = redisTemplate.opsForValue().get(PREFIX + "bypass:count");
        return val == null ? 0L : Long.parseLong(val.toString());
    }

    public void incrementBypassType(String type) {
        redisTemplate.opsForZSet().incrementScore(PREFIX + "bypass:types", type, 1);
    }

    public Map<String, Long> getBypassTypes() {
        Set<Object> items = redisTemplate.opsForZSet()
                .reverseRange(PREFIX + "bypass:types", 0, -1);
        Map<String, Long> result = new LinkedHashMap<>();
        if (items != null) {
            for (Object t : items) {
                Double score = redisTemplate.opsForZSet().score(PREFIX + "bypass:types", t);
                result.put(t.toString(), score == null ? 0L : score.longValue());
            }
        }
        return result;
    }

    // ─── Spike Baseline ──────────────────────────────────────────────────────────

    public void setBaseline(double rps) {
        redisTemplate.opsForValue().set(PREFIX + "spike:baseline", String.valueOf(rps));
    }

    public double getBaseline() {
        Object val = redisTemplate.opsForValue().get(PREFIX + "spike:baseline");
        return val == null ? 10.0 : Double.parseDouble(val.toString());
    }

    // ─── Total Counters ──────────────────────────────────────────────────────────

    public void incrementTotalRequests() {
        redisTemplate.opsForValue().increment(PREFIX + "total:requests");
    }

    public long getTotalRequests() {
        Object val = redisTemplate.opsForValue().get(PREFIX + "total:requests");
        return val == null ? 0L : Long.parseLong(val.toString());
    }

    public void incrementErrorCount() {
        redisTemplate.opsForValue().increment(PREFIX + "total:errors");
    }

    public long getTotalErrors() {
        Object val = redisTemplate.opsForValue().get(PREFIX + "total:errors");
        return val == null ? 0L : Long.parseLong(val.toString());
    }

    // ─── Latency Tracking ────────────────────────────────────────────────────────

    public void recordGlobalLatency(long latencyMs) {
        String key = PREFIX + "latency:global";
        redisTemplate.opsForList().rightPush(key, latencyMs);
        redisTemplate.expire(key, 600, TimeUnit.SECONDS);
        redisTemplate.opsForList().trim(key, -5000, -1);
    }

    public List<Object> getLatencies(int count) {
        return redisTemplate.opsForList().range(PREFIX + "latency:global", -count, -1);
    }

    // ─── Region Tracking ─────────────────────────────────────────────────────────

    public void incrementRegion(String region) {
        redisTemplate.opsForZSet().incrementScore(PREFIX + "regions", region, 1);
    }

    public Map<String, Long> getRegionDistribution() {
        Set<Object> items = redisTemplate.opsForZSet().reverseRange(PREFIX + "regions", 0, -1);
        Map<String, Long> result = new LinkedHashMap<>();
        if (items != null) {
            for (Object r : items) {
                Double score = redisTemplate.opsForZSet().score(PREFIX + "regions", r);
                result.put(r.toString(), score == null ? 0L : score.longValue());
            }
        }
        return result;
    }

    // ─── Error Code Tracking ─────────────────────────────────────────────────────

    public void incrementErrorCode(int code) {
        redisTemplate.opsForZSet().incrementScore(PREFIX + "error:codes", String.valueOf(code), 1);
    }

    public Map<String, Long> getErrorCodeDistribution() {
        Set<Object> items = redisTemplate.opsForZSet()
                .reverseRange(PREFIX + "error:codes", 0, -1);
        Map<String, Long> result = new LinkedHashMap<>();
        if (items != null) {
            for (Object c : items) {
                Double score = redisTemplate.opsForZSet().score(PREFIX + "error:codes", c);
                result.put(c.toString(), score == null ? 0L : score.longValue());
            }
        }
        return result;
    }
}
