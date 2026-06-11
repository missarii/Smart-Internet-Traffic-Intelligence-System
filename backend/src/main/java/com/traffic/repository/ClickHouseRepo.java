package com.traffic.repository;

import com.traffic.model.TrafficEvent;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import jakarta.annotation.PostConstruct;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;

/**
 * ClickHouse repository for persistent analytics storage.
 *
 * Table schema:
 *
 *   traffic_events (
 *     event_id     String,
 *     session_id   String,
 *     ip_address   String,
 *     endpoint     String,
 *     method       String,
 *     status_code  UInt16,
 *     latency_ms   UInt64,
 *     event_time   DateTime,
 *     user_agent   String,
 *     region       String,
 *     is_bypass    UInt8,
 *     is_anomaly   UInt8,
 *     bypass_type  String,
 *     retry_count  UInt8
 *   ) ENGINE = MergeTree()
 *     PARTITION BY toYYYYMM(event_time)
 *     ORDER BY (event_time, ip_address);
 */
@Repository
public class ClickHouseRepo {

    private final JdbcTemplate jdbcTemplate;

    public ClickHouseRepo(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @PostConstruct
    public void initSchema() {
        try {
            jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS traffic_events (
                    event_id     String,
                    session_id   String,
                    ip_address   String,
                    endpoint     String,
                    method       String,
                    status_code  UInt16,
                    latency_ms   UInt64,
                    event_time   DateTime DEFAULT now(),
                    user_agent   String,
                    region       String,
                    is_bypass    UInt8 DEFAULT 0,
                    is_anomaly   UInt8 DEFAULT 0,
                    bypass_type  String DEFAULT '',
                    retry_count  UInt8 DEFAULT 0
                ) ENGINE = MergeTree()
                PARTITION BY toYYYYMM(event_time)
                ORDER BY (event_time, ip_address)
            """);

            jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS traffic_alerts (
                    alert_id      String,
                    alert_type    String,
                    severity      String,
                    title         String,
                    message       String,
                    endpoint      String DEFAULT '',
                    source_ip     String DEFAULT '',
                    alert_time    DateTime DEFAULT now(),
                    current_value Float64 DEFAULT 0,
                    threshold     Float64 DEFAULT 0
                ) ENGINE = MergeTree()
                PARTITION BY toYYYYMM(alert_time)
                ORDER BY alert_time
            """);
        } catch (Exception e) {
            System.err.println("[ClickHouse] Schema init failed (may be offline): " + e.getMessage());
        }
    }

    public void insertEvent(TrafficEvent event) {
        try {
            jdbcTemplate.update("""
                INSERT INTO traffic_events
                (event_id, session_id, ip_address, endpoint, method,
                 status_code, latency_ms, event_time, user_agent, region,
                 is_bypass, is_anomaly, bypass_type, retry_count)
                VALUES (?, ?, ?, ?, ?, ?, ?, toDateTime(?), ?, ?, ?, ?, ?, ?)
            """,
                event.getEventId(), event.getSessionId(), event.getIpAddress(),
                event.getEndpoint(), event.getMethod(), event.getStatusCode(),
                event.getLatencyMs(), event.getTimestamp() / 1000,
                event.getUserAgent(), event.getRegion(),
                event.isBypass() ? 1 : 0, event.isAnomaly() ? 1 : 0,
                event.getBypassType() == null ? "" : event.getBypassType(),
                event.getRetryCount()
            );
        } catch (Exception e) {
            System.err.println("[ClickHouse] Insert failed: " + e.getMessage());
        }
    }

    public List<Map<String, Object>> getTopEndpointsByErrors(int limit) {
        try {
            return jdbcTemplate.queryForList("""
                SELECT endpoint,
                       count() AS total,
                       countIf(status_code >= 400) AS errors,
                       avg(latency_ms) AS avg_latency
                FROM traffic_events
                WHERE event_time >= now() - INTERVAL 1 HOUR
                GROUP BY endpoint
                ORDER BY errors DESC
                LIMIT ?
            """, limit);
        } catch (Exception e) {
            return List.of();
        }
    }

    public List<Map<String, Object>> getLatencyPercentiles(String endpoint) {
        try {
            return jdbcTemplate.queryForList("""
                SELECT
                    quantile(0.50)(latency_ms) AS p50,
                    quantile(0.95)(latency_ms) AS p95,
                    quantile(0.99)(latency_ms) AS p99
                FROM traffic_events
                WHERE endpoint = ? AND event_time >= now() - INTERVAL 5 MINUTE
            """, endpoint);
        } catch (Exception e) {
            return List.of();
        }
    }

    public List<Map<String, Object>> getTrafficTimeSeries(int minutesBack) {
        try {
            return jdbcTemplate.queryForList("""
                SELECT
                    toStartOfMinute(event_time) AS minute,
                    count() AS requests,
                    avg(latency_ms) AS avg_latency,
                    countIf(status_code >= 400) AS errors
                FROM traffic_events
                WHERE event_time >= now() - INTERVAL ? MINUTE
                GROUP BY minute
                ORDER BY minute ASC
            """, minutesBack);
        } catch (Exception e) {
            return List.of();
        }
    }

    public List<Map<String, Object>> getAnomalousIPs(int limit) {
        try {
            return jdbcTemplate.queryForList("""
                SELECT
                    ip_address,
                    count() AS requests,
                    sum(is_bypass) AS bypasses,
                    sum(is_anomaly) AS anomalies,
                    sum(retry_count) AS retries
                FROM traffic_events
                WHERE event_time >= now() - INTERVAL 1 HOUR
                GROUP BY ip_address
                HAVING anomalies > 0 OR bypasses > 0
                ORDER BY anomalies DESC, bypasses DESC
                LIMIT ?
            """, limit);
        } catch (Exception e) {
            return List.of();
        }
    }
}
