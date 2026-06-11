import React, { useState, useEffect } from 'react';

const LATENCY_LEVELS = [
  { max: 50,   color: '#10b981', label: '<50ms'   },
  { max: 100,  color: '#34d399', label: '<100ms'  },
  { max: 200,  color: '#f59e0b', label: '<200ms'  },
  { max: 500,  color: '#f97316', label: '<500ms'  },
  { max: 1000, color: '#ef4444', label: '<1000ms' },
  { max: Infinity, color: '#dc2626', label: '>1s'  },
];

function getLatencyColor(ms) {
  for (const level of LATENCY_LEVELS) {
    if (ms < level.max) return level.color;
  }
  return '#dc2626';
}

function getLatencyLabel(ms) {
  for (const level of LATENCY_LEVELS) {
    if (ms < level.max) return level.label;
  }
  return '>1s';
}

export default function TrafficHeatmap({ report }) {
  const [cells, setCells] = useState(() =>
    Array.from({ length: 96 }, (_, i) => ({ id: i, latency: Math.random() * 100 + 10, ts: Date.now() }))
  );

  useEffect(() => {
    if (!report) return;
    const latency = report.avgLatencyMs || 50;
    setCells(prev => {
      const next = [...prev];
      const idxToUpdate = Math.floor(Math.random() * next.length);
      const jitter = (Math.random() - 0.5) * latency * 0.8;
      next[idxToUpdate] = {
        ...next[idxToUpdate],
        latency: Math.max(5, latency + jitter),
        ts: Date.now(),
      };
      return next;
    });
  }, [report]);

  const avgLatency = cells.reduce((s, c) => s + c.latency, 0) / cells.length;
  const maxLatency = Math.max(...cells.map(c => c.latency));
  const p95Cell    = [...cells].sort((a, b) => a.latency - b.latency)[Math.floor(cells.length * 0.95)]?.latency;

  const distribution = LATENCY_LEVELS.map(level => ({
    ...level,
    count: cells.filter(c => c.latency < level.max && c.latency >= (LATENCY_LEVELS[LATENCY_LEVELS.indexOf(level) - 1]?.max || 0)).length,
  }));

  return (
    <div>
      <div className="page-header">
        <h1 className="page-title">🔥 Latency Heatmap</h1>
        <p className="page-subtitle">Real-time visualization of system response latency distribution</p>
      </div>

      {/* ── Stats Row ── */}
      <div className="kpi-grid" style={{ gridTemplateColumns: 'repeat(4,1fr)', marginBottom: 24 }}>
        <div className="kpi-card">
          <div className="kpi-label">Avg Latency</div>
          <div className="kpi-value" style={{ color: getLatencyColor(avgLatency) }}>
            {avgLatency.toFixed(0)}<span className="kpi-unit">ms</span>
          </div>
        </div>
        <div className="kpi-card">
          <div className="kpi-label">Peak Latency</div>
          <div className="kpi-value" style={{ color: getLatencyColor(maxLatency) }}>
            {maxLatency.toFixed(0)}<span className="kpi-unit">ms</span>
          </div>
        </div>
        <div className="kpi-card">
          <div className="kpi-label">P95 Latency</div>
          <div className="kpi-value" style={{ color: getLatencyColor(p95Cell) }}>
            {(p95Cell || 0).toFixed(0)}<span className="kpi-unit">ms</span>
          </div>
        </div>
        <div className="kpi-card">
          <div className="kpi-label">Healthy Cells</div>
          <div className="kpi-value" style={{ color: '#10b981' }}>
            {cells.filter(c => c.latency < 100).length}
            <span className="kpi-unit">/ {cells.length}</span>
          </div>
        </div>
      </div>

      {/* ── Heatmap Grid ── */}
      <div className="card" style={{ marginBottom: 20 }}>
        <div className="card-header">
          <span className="card-title">🌡 Live Latency Grid</span>
          <span style={{ fontSize: 11, color: 'var(--text-muted)' }}>Each cell = a request bucket</span>
        </div>
        <div className="card-body">
          <div style={{
            display: 'grid',
            gridTemplateColumns: 'repeat(12, 1fr)',
            gap: 4,
            marginBottom: 16,
          }}>
            {cells.map(cell => (
              <div
                key={cell.id}
                title={`${cell.latency.toFixed(0)}ms`}
                style={{
                  height: 32,
                  borderRadius: 4,
                  background: getLatencyColor(cell.latency),
                  opacity: 0.7 + (cell.latency / maxLatency) * 0.3,
                  transition: 'background 0.4s, opacity 0.4s',
                  cursor: 'default',
                }}
              />
            ))}
          </div>

          {/* ── Legend ── */}
          <div style={{ display: 'flex', gap: 16, flexWrap: 'wrap' }}>
            {LATENCY_LEVELS.map(level => (
              <div key={level.label} style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
                <div style={{
                  width: 12, height: 12, borderRadius: 2, background: level.color
                }} />
                <span style={{ fontSize: 11, color: 'var(--text-secondary)' }}>{level.label}</span>
              </div>
            ))}
          </div>
        </div>
      </div>

      {/* ── Distribution Bars ── */}
      <div className="card">
        <div className="card-header">
          <span className="card-title">📊 Latency Distribution</span>
        </div>
        <div className="card-body">
          <div style={{ display: 'flex', flexDirection: 'column', gap: 10 }}>
            {distribution.map(level => (
              <div key={level.label}>
                <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: 4 }}>
                  <span style={{ fontSize: 12, color: level.color, fontWeight: 600 }}>{level.label}</span>
                  <span style={{ fontSize: 12, color: 'var(--text-secondary)' }}>
                    {level.count} cells ({((level.count / cells.length) * 100).toFixed(0)}%)
                  </span>
                </div>
                <div className="progress-bar" style={{ height: 8 }}>
                  <div style={{
                    height: '100%',
                    borderRadius: 4,
                    background: level.color,
                    width: `${(level.count / cells.length) * 100}%`,
                    transition: 'width 0.5s ease',
                  }} />
                </div>
              </div>
            ))}
          </div>
        </div>
      </div>
    </div>
  );
}
