package com.traffic.service;

import com.traffic.model.TrafficEvent;
import com.traffic.model.UserSession;
import com.traffic.repository.RedisRepo;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Session Tracking Service — maintains full user request flow.
 *
 * Each session tracks:
 *   - Entry point → API call chain → exit
 *   - Risk score accumulation
 *   - Anomaly escalation
 *
 * High-risk sessions (score ≥ 70) trigger automatic review.
 */
@Service
public class SessionTrackingService {

    private final RedisRepo             redisRepo;
    private final AlertService          alertService;
    private final SimpMessagingTemplate messagingTemplate;

    public SessionTrackingService(RedisRepo redisRepo,
                                   AlertService alertService,
                                   SimpMessagingTemplate messagingTemplate) {
        this.redisRepo         = redisRepo;
        this.alertService      = alertService;
        this.messagingTemplate = messagingTemplate;
    }

    /**
     * Get or create a session. Called before the event is processed.
     */
    public UserSession getOrCreate(String sessionId, String ip, String userAgent) {
        return redisRepo.getSession(sessionId).orElseGet(() -> {
            UserSession s = new UserSession();
            s.setSessionId(sessionId);
            s.setIpAddress(ip);
            s.setUserAgent(userAgent);
            s.setStartTime(System.currentTimeMillis());
            s.setLastActivityTime(System.currentTimeMillis());
            redisRepo.saveSession(s);
            return s;
        });
    }

    /**
     * Update session with event data, recompute risk, broadcast if high risk.
     */
    public void update(UserSession session, TrafficEvent event) {
        session.addEndpoint(event.getEndpoint());

        if (event.getStatusCode() >= 400) session.incrementFailed();
        if (event.isBypass())            session.incrementBypass();
        if (event.getRetryCount() > 0)   session.incrementRetry();

        // IP-level risk escalation
        if (redisRepo.isIPBlocked(session.getIpAddress())) {
            session.setBlocked(true);
            session.setBlockReason("Rate limit exceeded");
        }

        redisRepo.saveSession(session);

        // Broadcast anomalous sessions
        if (session.isHighRisk()) {
            messagingTemplate.convertAndSend("/topic/sessions", session);
        }
    }
}
