package com.traffic.model;

import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

/**
 * Represents a single traffic event (HTTP request) captured in the system.
 */
public class TrafficEvent implements Serializable {

    private String eventId;
    private String sessionId;
    private String ipAddress;
    private String endpoint;
    private String method;
    private int statusCode;
    private long latencyMs;
    private long timestamp;
    private String userAgent;
    private String region;
    private boolean isBypass;
    private boolean isAnomaly;
    private String bypassType;
    private int retryCount;
    private long requestSizeBytes;
    private long responseSizeBytes;

    public TrafficEvent() {}

    public static TrafficEvent create(String sessionId, String ip, String endpoint,
                                      String method, int statusCode, long latencyMs,
                                      String userAgent, String region) {
        TrafficEvent e = new TrafficEvent();
        e.eventId   = UUID.randomUUID().toString();
        e.sessionId = sessionId;
        e.ipAddress = ip;
        e.endpoint  = endpoint;
        e.method    = method;
        e.statusCode= statusCode;
        e.latencyMs = latencyMs;
        e.timestamp = Instant.now().toEpochMilli();
        e.userAgent = userAgent;
        e.region    = region;
        return e;
    }

    // ── Getters ──────────────────────────────────────────────────────────────
    public String getEventId()           { return eventId; }
    public String getSessionId()         { return sessionId; }
    public String getIpAddress()         { return ipAddress; }
    public String getEndpoint()          { return endpoint; }
    public String getMethod()            { return method; }
    public int    getStatusCode()        { return statusCode; }
    public long   getLatencyMs()         { return latencyMs; }
    public long   getTimestamp()         { return timestamp; }
    public String getUserAgent()         { return userAgent; }
    public String getRegion()            { return region; }
    public boolean isBypass()            { return isBypass; }
    public boolean isAnomaly()           { return isAnomaly; }
    public String getBypassType()        { return bypassType; }
    public int    getRetryCount()        { return retryCount; }
    public long   getRequestSizeBytes()  { return requestSizeBytes; }
    public long   getResponseSizeBytes() { return responseSizeBytes; }

    // ── Setters ──────────────────────────────────────────────────────────────
    public void setEventId(String v)           { this.eventId = v; }
    public void setSessionId(String v)         { this.sessionId = v; }
    public void setIpAddress(String v)         { this.ipAddress = v; }
    public void setEndpoint(String v)          { this.endpoint = v; }
    public void setMethod(String v)            { this.method = v; }
    public void setStatusCode(int v)           { this.statusCode = v; }
    public void setLatencyMs(long v)           { this.latencyMs = v; }
    public void setTimestamp(long v)           { this.timestamp = v; }
    public void setUserAgent(String v)         { this.userAgent = v; }
    public void setRegion(String v)            { this.region = v; }
    public void setBypass(boolean v)           { this.isBypass = v; }
    public void setAnomaly(boolean v)          { this.isAnomaly = v; }
    public void setBypassType(String v)        { this.bypassType = v; }
    public void setRetryCount(int v)           { this.retryCount = v; }
    public void setRequestSizeBytes(long v)    { this.requestSizeBytes = v; }
    public void setResponseSizeBytes(long v)   { this.responseSizeBytes = v; }
}
