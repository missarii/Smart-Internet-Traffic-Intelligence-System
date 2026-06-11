package com.traffic.model;

import java.util.UUID;

/**
 * Represents a system alert triggered when thresholds are breached.
 */
public class TrafficAlert {

    public enum Severity { INFO, WARNING, CRITICAL, EMERGENCY }
    public enum AlertType {
        SPIKE_DETECTED, DDoS_SUSPECTED, BYPASS_DETECTED,
        RATE_LIMIT_EXCEEDED, ENDPOINT_BOTTLENECK, HIGH_ERROR_RATE,
        LATENCY_SPIKE, IP_BLOCKED, SESSION_ANOMALY, SYSTEM_OVERLOAD
    }

    private String alertId;
    private AlertType type;
    private Severity severity;
    private String title;
    private String message;
    private String affectedEndpoint;
    private String sourceIP;
    private long timestamp;
    private boolean resolved;
    private double currentValue;
    private double thresholdValue;
    private String unit;

    public TrafficAlert() {}

    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private final TrafficAlert a = new TrafficAlert();
        public Builder type(AlertType v)           { a.type = v; return this; }
        public Builder severity(Severity v)         { a.severity = v; return this; }
        public Builder title(String v)              { a.title = v; return this; }
        public Builder message(String v)            { a.message = v; return this; }
        public Builder affectedEndpoint(String v)   { a.affectedEndpoint = v; return this; }
        public Builder sourceIP(String v)           { a.sourceIP = v; return this; }
        public Builder currentValue(double v)       { a.currentValue = v; return this; }
        public Builder thresholdValue(double v)     { a.thresholdValue = v; return this; }
        public Builder unit(String v)               { a.unit = v; return this; }
        public TrafficAlert build()                 { return a; }
    }

    // ── Getters / Setters ────────────────────────────────────────────────────
    public String    getAlertId()          { return alertId; }
    public void      setAlertId(String v)  { this.alertId = v; }
    public AlertType getType()             { return type; }
    public Severity  getSeverity()         { return severity; }
    public String    getTitle()            { return title; }
    public String    getMessage()          { return message; }
    public String    getAffectedEndpoint() { return affectedEndpoint; }
    public String    getSourceIP()         { return sourceIP; }
    public long      getTimestamp()        { return timestamp; }
    public void      setTimestamp(long v)  { this.timestamp = v; }
    public boolean   isResolved()          { return resolved; }
    public void      setResolved(boolean v){ this.resolved = v; }
    public double    getCurrentValue()     { return currentValue; }
    public double    getThresholdValue()   { return thresholdValue; }
    public String    getUnit()             { return unit; }
}
