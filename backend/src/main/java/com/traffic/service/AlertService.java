package com.traffic.service;

import com.traffic.model.TrafficAlert;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.util.Deque;
import java.util.LinkedList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedDeque;

/**
 * Alert Service — centralized alert firing and history management.
 *
 * Deduplicates alerts within a 30-second window per alert type.
 * Broadcasts all alerts to /topic/alerts for live dashboard display.
 * Maintains an in-memory ring buffer of the last 200 alerts.
 */
@Service
public class AlertService {

    private static final int  MAX_HISTORY       = 200;
    private static final long DEDUP_WINDOW_MS   = 30_000; // 30 seconds

    private final SimpMessagingTemplate messagingTemplate;
    private final Deque<TrafficAlert>   alertHistory = new ConcurrentLinkedDeque<>();

    // Dedup tracking: type+endpoint → last fire timestamp
    private final java.util.concurrent.ConcurrentHashMap<String, Long> lastFired =
            new java.util.concurrent.ConcurrentHashMap<>();

    public AlertService(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    public void fireAlert(TrafficAlert alert) {
        // Deduplication key
        String dedupKey = alert.getType().name() + ":" +
                (alert.getAffectedEndpoint() == null ? "" : alert.getAffectedEndpoint());

        long now = System.currentTimeMillis();
        Long last = lastFired.get(dedupKey);
        if (last != null && (now - last) < DEDUP_WINDOW_MS) {
            return; // suppress duplicate
        }
        lastFired.put(dedupKey, now);

        // Finalize alert
        alert.setAlertId(UUID.randomUUID().toString());
        alert.setTimestamp(now);
        alert.setResolved(false);

        // Store in history ring buffer
        alertHistory.addFirst(alert);
        if (alertHistory.size() > MAX_HISTORY) {
            alertHistory.pollLast();
        }

        // Broadcast to dashboard
        try {
            messagingTemplate.convertAndSend("/topic/alerts", alert);
        } catch (Exception e) {
            System.err.println("[AlertService] Broadcast failed: " + e.getMessage());
        }

        System.out.printf("[ALERT][%s] %s — %s%n",
                alert.getSeverity(), alert.getTitle(), alert.getMessage());
    }

    public List<TrafficAlert> getRecentAlerts(int n) {
        return alertHistory.stream().limit(n).toList();
    }

    public long getAlertCount() {
        return alertHistory.size();
    }
}
