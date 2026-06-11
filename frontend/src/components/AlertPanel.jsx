import React from 'react';

const SEVERITY_ORDER = ['EMERGENCY', 'CRITICAL', 'WARNING', 'INFO'];

export default function AlertPanel({ alerts = [] }) {
  const grouped = SEVERITY_ORDER.reduce((acc, s) => {
    acc[s] = alerts.filter(a => a.severity === s);
    return acc;
  }, {});

  const countBySeverity = (s) => grouped[s]?.length || 0;

  return (
    <div>
      <div className="page-header">
        <h1 className="page-title">🚨 Alert Center</h1>
        <p className="page-subtitle">Auto-triggered alerts from spike detection, bypass detection, and load analysis</p>
      </div>

      {/* ── Summary ── */}
      <div className="kpi-grid" style={{ gridTemplateColumns: 'repeat(4,1fr)', marginBottom: 24 }}>
        {SEVERITY_ORDER.map(s => (
          <div key={s} className="kpi-card">
            <div className="kpi-label">{s}</div>
            <div className="kpi-value">{countBySeverity(s)}</div>
          </div>
        ))}
      </div>

      {/* ── Alert list ── */}
      <div className="card">
        <div className="card-header">
          <span className="card-title">All Alerts ({alerts.length})</span>
        </div>
        <div className="card-body" style={{ maxHeight: 600, overflowY: 'auto' }}>
          {alerts.length === 0
            ? <div style={{ color: 'var(--text-muted)', fontSize: 14 }}>No alerts recorded yet.</div>
            : alerts.map(a => (
              <div key={a.alertId} className={`alert-item ${a.severity}`} style={{ marginBottom: 8 }}>
                <span className={`alert-severity severity-${a.severity}`}>{a.severity}</span>
                <div className="alert-content">
                  <div className="alert-title">{a.title}</div>
                  <div className="alert-msg">{a.message}</div>
                  {a.affectedEndpoint && (
                    <div style={{ fontSize: 10, color: 'var(--accent-cyan)', marginTop: 2, fontFamily: 'JetBrains Mono' }}>
                      {a.affectedEndpoint}
                    </div>
                  )}
                  {a.currentValue != null && (
                    <div style={{ fontSize: 10, color: 'var(--text-muted)', marginTop: 2 }}>
                      Value: {a.currentValue?.toFixed(1)} {a.unit} / Threshold: {a.thresholdValue?.toFixed(1)} {a.unit}
                    </div>
                  )}
                </div>
                <span className="alert-time">{new Date(a.timestamp).toLocaleTimeString()}</span>
              </div>
            ))
          }
        </div>
      </div>
    </div>
  );
}
