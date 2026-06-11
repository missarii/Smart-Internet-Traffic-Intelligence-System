package com.traffic.model;

import java.util.List;
import java.util.Map;

/**
 * Snapshot report of traffic analytics for a given time window.
 */
public class TrafficReport {

    private long timestamp;
    private long totalRequests;
    private double requestsPerSecond;
    private double avgLatencyMs;
    private double p95LatencyMs;
    private double p99LatencyMs;
    private double errorRate;
    private long activeSessions;
    private long blockedIPs;
    private long bypassAttempts;
    private long spikeCount;
    private boolean spikeActive;
    private double spikeMultiplier;
    private String spikeEndpoint;
    private Map<String, EndpointStats> endpointBreakdown;
    private List<String> topAnomalousIPs;
    private Map<String, Long> regionDistribution;
    private Map<String, Long> errorCodeDistribution;
    private Map<String, Long> latencyHistogram;
    private Map<String, Long> bypassTypeDistribution;
    private String systemStatus;

    public TrafficReport() {}

    // ── Builder ───────────────────────────────────────────────────────────────
    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private final TrafficReport r = new TrafficReport();
        public Builder timestamp(long v)                              { r.timestamp = v; return this; }
        public Builder totalRequests(long v)                          { r.totalRequests = v; return this; }
        public Builder requestsPerSecond(double v)                    { r.requestsPerSecond = v; return this; }
        public Builder avgLatencyMs(double v)                         { r.avgLatencyMs = v; return this; }
        public Builder p95LatencyMs(double v)                         { r.p95LatencyMs = v; return this; }
        public Builder p99LatencyMs(double v)                         { r.p99LatencyMs = v; return this; }
        public Builder errorRate(double v)                            { r.errorRate = v; return this; }
        public Builder activeSessions(long v)                         { r.activeSessions = v; return this; }
        public Builder blockedIPs(long v)                             { r.blockedIPs = v; return this; }
        public Builder bypassAttempts(long v)                         { r.bypassAttempts = v; return this; }
        public Builder spikeActive(boolean v)                         { r.spikeActive = v; return this; }
        public Builder spikeMultiplier(double v)                      { r.spikeMultiplier = v; return this; }
        public Builder spikeEndpoint(String v)                        { r.spikeEndpoint = v; return this; }
        public Builder endpointBreakdown(Map<String,EndpointStats> v) { r.endpointBreakdown = v; return this; }
        public Builder regionDistribution(Map<String,Long> v)         { r.regionDistribution = v; return this; }
        public Builder errorCodeDistribution(Map<String,Long> v)      { r.errorCodeDistribution = v; return this; }
        public Builder bypassTypeDistribution(Map<String,Long> v)     { r.bypassTypeDistribution = v; return this; }
        public Builder systemStatus(String v)                         { r.systemStatus = v; return this; }
        public TrafficReport build()                                  { return r; }
    }

    // ── Getters / Setters ────────────────────────────────────────────────────
    public long   getTimestamp()                             { return timestamp; }
    public long   getTotalRequests()                         { return totalRequests; }
    public double getRequestsPerSecond()                     { return requestsPerSecond; }
    public double getAvgLatencyMs()                          { return avgLatencyMs; }
    public double getP95LatencyMs()                          { return p95LatencyMs; }
    public double getP99LatencyMs()                          { return p99LatencyMs; }
    public double getErrorRate()                             { return errorRate; }
    public long   getActiveSessions()                        { return activeSessions; }
    public long   getBlockedIPs()                            { return blockedIPs; }
    public long   getBypassAttempts()                        { return bypassAttempts; }
    public boolean isSpikeActive()                           { return spikeActive; }
    public double getSpikeMultiplier()                       { return spikeMultiplier; }
    public String getSpikeEndpoint()                         { return spikeEndpoint; }
    public Map<String,EndpointStats> getEndpointBreakdown()  { return endpointBreakdown; }
    public Map<String,Long> getRegionDistribution()          { return regionDistribution; }
    public Map<String,Long> getErrorCodeDistribution()       { return errorCodeDistribution; }
    public Map<String,Long> getBypassTypeDistribution()      { return bypassTypeDistribution; }
    public String getSystemStatus()                          { return systemStatus; }

    // ── Inner class ───────────────────────────────────────────────────────────
    public static class EndpointStats {
        private String endpoint;
        private long requestCount;
        private double avgLatencyMs;
        private long errorCount;
        private double errorRate;
        private long bypassCount;
        private boolean isBottleneck;

        public static Builder builder() { return new Builder(); }

        public static class Builder {
            private final EndpointStats s = new EndpointStats();
            public Builder endpoint(String v)     { s.endpoint = v; return this; }
            public Builder requestCount(long v)   { s.requestCount = v; return this; }
            public Builder avgLatencyMs(double v) { s.avgLatencyMs = v; return this; }
            public Builder errorCount(long v)     { s.errorCount = v; return this; }
            public Builder errorRate(double v)    { s.errorRate = v; return this; }
            public Builder bypassCount(long v)    { s.bypassCount = v; return this; }
            public Builder isBottleneck(boolean v){ s.isBottleneck = v; return this; }
            public EndpointStats build()          { return s; }
        }

        public String getEndpoint()       { return endpoint; }
        public long   getRequestCount()   { return requestCount; }
        public double getAvgLatencyMs()   { return avgLatencyMs; }
        public long   getErrorCount()     { return errorCount; }
        public double getErrorRate()      { return errorRate; }
        public long   getBypassCount()    { return bypassCount; }
        public boolean isBottleneck()     { return isBottleneck; }
    }
}
