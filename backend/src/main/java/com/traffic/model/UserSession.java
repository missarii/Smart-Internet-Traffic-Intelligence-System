package com.traffic.model;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Tracks the full lifecycle of a user session across multiple requests.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class UserSession implements Serializable {

    private String sessionId;
    private String ipAddress;
    private String userAgent;
    private long startTime;
    private long lastActivityTime;
    private int totalRequests;
    private int failedRequests;
    private int bypassAttempts;
    private int retryCount;
    private double riskScore;
    private boolean isBlocked;
    private String blockReason;
    private List<String> visitedEndpoints = new ArrayList<>();
    private List<String> routePath        = new ArrayList<>();

    public UserSession() {}

    public void addEndpoint(String endpoint) {
        visitedEndpoints.add(endpoint);
        routePath.add(endpoint);
        totalRequests++;
        lastActivityTime = System.currentTimeMillis();
    }

    public void incrementBypass() { bypassAttempts++; riskScore = Math.min(100.0, riskScore + 15.0); }
    public void incrementRetry()  { retryCount++;     riskScore = Math.min(100.0, riskScore + 5.0); }
    public void incrementFailed() { failedRequests++;  riskScore = Math.min(100.0, riskScore + 3.0); }

    public boolean isHighRisk()   { return riskScore >= 70.0; }
    public boolean isMediumRisk() { return riskScore >= 40.0 && riskScore < 70.0; }

    // ── Getters / Setters ────────────────────────────────────────────────────
    public String getSessionId()             { return sessionId; }
    public void   setSessionId(String v)     { this.sessionId = v; }
    public String getIpAddress()             { return ipAddress; }
    public void   setIpAddress(String v)     { this.ipAddress = v; }
    public String getUserAgent()             { return userAgent; }
    public void   setUserAgent(String v)     { this.userAgent = v; }
    public long   getStartTime()             { return startTime; }
    public void   setStartTime(long v)       { this.startTime = v; }
    public long   getLastActivityTime()      { return lastActivityTime; }
    public void   setLastActivityTime(long v){ this.lastActivityTime = v; }
    public int    getTotalRequests()         { return totalRequests; }
    public void   setTotalRequests(int v)    { this.totalRequests = v; }
    public int    getFailedRequests()        { return failedRequests; }
    public void   setFailedRequests(int v)   { this.failedRequests = v; }
    public int    getBypassAttempts()        { return bypassAttempts; }
    public void   setBypassAttempts(int v)   { this.bypassAttempts = v; }
    public int    getRetryCount()            { return retryCount; }
    public void   setRetryCount(int v)       { this.retryCount = v; }
    public double getRiskScore()             { return riskScore; }
    public void   setRiskScore(double v)     { this.riskScore = v; }
    public boolean isBlocked()               { return isBlocked; }
    public void   setBlocked(boolean v)      { this.isBlocked = v; }
    public String getBlockReason()           { return blockReason; }
    public void   setBlockReason(String v)   { this.blockReason = v; }
    public List<String> getVisitedEndpoints(){ return visitedEndpoints; }
    public void   setVisitedEndpoints(List<String> v){ this.visitedEndpoints = v; }
    public List<String> getRoutePath()       { return routePath; }
    public void   setRoutePath(List<String> v){ this.routePath = v; }
}
