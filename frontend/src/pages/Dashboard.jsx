import React, { useState, useEffect } from 'react';
import {
  LineChart, Line, AreaChart, Area, BarChart, Bar,
  XAxis, YAxis, CartesianGrid, Tooltip, ResponsiveContainer,
  PieChart, Pie, Cell, Legend
} from 'recharts';
import { trafficAPI } from '../services/api';

const COLORS = ['#3b82f6','#06b6d4','#10b981','#f59e0b','#ef4444','#8b5cf6','#f97316','#ec4899'];

const fmt = (n) => {
  if (n == null) return '0';
  if (n >= 1_000_000) return (n / 1_000_000).toFixed(1) + 'M';
  if (n >= 1_000)     return (n / 1_000).toFixed(1) + 'K';
  return String(Math.round(n));
};

function KpiCard({ label, value, unit, sub, accent, icon }) {
  return (
    <div className="kpi-card" style={{ '--kpi-accent': accent }}>
      <div className="kpi-label">{label}</div>
      <div className="kpi-value">
        {fmt(value)}<span className="kpi-unit">{unit}</span>
      </div>
      {sub && <div className="kpi-sub">{sub}</div>}
      {icon && <div className="kpi-icon">{icon}</div>}
    </div>
  );
}

function CustomTooltip({ active, payload, label }) {
  if (!active || !payload?.length) return null;
  return (
    <div style={{
      background: '#ffffff', border: '1px solid rgba(0,0,0,0.1)',
      borderRadius: 8, padding: '10px 14px', fontSize: 12,
      boxShadow: '0 4px 6px -1px rgba(0, 0, 0, 0.1)'
    }}>
      <div style={{ color: '#475569', marginBottom: 4 }}>{label}</div>
      {payload.map((p, i) => (
        <div key={i} style={{ color: p.color, fontWeight: 600 }}>
          {p.name}: {typeof p.value === 'number' ? p.value.toFixed(1) : p.value}
        </div>
      ))}
    </div>
  );
}

export default function Dashboard({ report, alerts, defaultTab }) {
  const [rpsHistory, setRpsHistory]     = useState([]);
  const [stats, setStats]               = useState(null);

  // Rolling RPS history (last 60 data points)
  useEffect(() => {
    if (!report) return;
    setRpsHistory(prev => {
      const next = [...prev, {
        t: new Date(report.timestamp).toLocaleTimeString(),
        rps: parseFloat(report.requestsPerSecond?.toFixed(1) || 0),
        latency: parseFloat(report.avgLatencyMs?.toFixed(1) || 0),
        errors: parseFloat(((report.errorRate || 0) * 100).toFixed(1)),
      }];
      return next.slice(-60);
    });
  }, [report]);

  // Load initial stats
  useEffect(() => {
    trafficAPI.getStats().then(r => setStats(r.data)).catch(() => {});
    const id = setInterval(() => {
      trafficAPI.getStats().then(r => setStats(r.data)).catch(() => {});
    }, 5000);
    return () => clearInterval(id);
  }, []);

  const rps        = report?.requestsPerSecond || 0;
  const latency    = report?.avgLatencyMs || 0;
  const errorRate  = ((report?.errorRate || 0) * 100).toFixed(1);
  const sessions   = report?.activeSessions || 0;
  const blocked    = report?.blockedIPs || 0;
  const bypass     = report?.bypassAttempts || 0;
  const total      = report?.totalRequests || stats?.totalRequests || 0;
  const p95        = report?.p95LatencyMs || 0;
  const p99        = report?.p99LatencyMs || 0;
  const sysStatus  = report?.systemStatus || 'HEALTHY';

  const endpoints  = report?.endpointBreakdown
    ? Object.values(report.endpointBreakdown) : [];
  const topEp      = endpoints.sort((a, b) => b.requestCount - a.requestCount).slice(0, 8);
  const maxCount   = topEp[0]?.requestCount || 1;

  const regions    = report?.regionDistribution || {};
  const regionData = Object.entries(regions).map(([name, value]) => ({ name, value }));

  const errCodes   = report?.errorCodeDistribution || {};
  const errData    = Object.entries(errCodes).map(([name, value]) => ({ name, value }));

  const bypassMap  = report?.bypassTypeDistribution || {};
  const bypassData = Object.entries(bypassMap).map(([type, count]) => ({ type, count }));

  const recentAlerts = (alerts || []).slice(0, 8);

  return (
    <div>
      <div className="page-header">
        <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
          <div>
            <h1 className="page-title">Live Traffic Dashboard</h1>
            <p className="page-subtitle">Real-time internet traffic intelligence — updates every second</p>
          </div>
          <span className={`status-pill status-${sysStatus}`}>
            ● {sysStatus}
          </span>
        </div>
      </div>

      {/* ── KPI Row ── */}
      <div className="kpi-grid">
        <KpiCard label="Requests / sec" value={rps} unit="rps"
          accent="linear-gradient(135deg,#1d4ed8,#06b6d4)" icon="⚡" />
        <KpiCard label="Total Requests" value={total}
          accent="linear-gradient(135deg,#059669,#10b981)" icon="📈" />
        <KpiCard label="Avg Latency" value={latency} unit="ms"
          sub={`P95: ${p95.toFixed(0)}ms  P99: ${p99.toFixed(0)}ms`}
          accent="linear-gradient(135deg,#7c3aed,#8b5cf6)" icon="⏱" />
        <KpiCard label="Error Rate" value={errorRate} unit="%"
          accent={errorRate > 15 ? "linear-gradient(135deg,#dc2626,#f97316)" : "linear-gradient(135deg,#059669,#10b981)"}
          icon="❌" />
        <KpiCard label="Active Sessions" value={sessions}
          accent="linear-gradient(135deg,#0891b2,#06b6d4)" icon="👤" />
        <KpiCard label="Blocked IPs" value={blocked}
          accent="linear-gradient(135deg,#b45309,#f59e0b)" icon="🚫" />
        <KpiCard label="Bypass Attempts" value={bypass}
          accent="linear-gradient(135deg,#7c3aed,#ec4899)" icon="🛡️" />
        <KpiCard label="Spike" value={report?.spikeActive ? report.spikeMultiplier?.toFixed(1) : 1.0} unit="×"
          sub={report?.spikeActive ? `On ${report.spikeEndpoint}` : 'Normal traffic'}
          accent={report?.spikeActive ? "linear-gradient(135deg,#dc2626,#f97316)" : "linear-gradient(135deg,#059669,#10b981)"}
          icon="🔥" />
      </div>

      {/* ── RPS + Latency Chart ── */}
      <div className="dashboard-grid grid-2" style={{ marginBottom: 20 }}>
        <div className="card">
          <div className="card-header">
            <span className="card-title">⚡ Requests per Second</span>
          </div>
          <div className="card-body">
            <div className="chart-container">
              <ResponsiveContainer width="100%" height="100%">
                <AreaChart data={rpsHistory}>
                  <defs>
                    <linearGradient id="rpsGrad" x1="0" y1="0" x2="0" y2="1">
                      <stop offset="5%"  stopColor="#3b82f6" stopOpacity={0.4}/>
                      <stop offset="95%" stopColor="#3b82f6" stopOpacity={0}/>
                    </linearGradient>
                  </defs>
                  <CartesianGrid strokeDasharray="3 3" stroke="rgba(255,255,255,0.04)" />
                  <XAxis dataKey="t" tick={{ fill: '#4a5568', fontSize: 10 }} interval={9} />
                  <YAxis tick={{ fill: '#4a5568', fontSize: 10 }} />
                  <Tooltip content={<CustomTooltip />} />
                  <Area type="monotone" dataKey="rps" name="RPS"
                    stroke="#3b82f6" fill="url(#rpsGrad)" strokeWidth={2} dot={false} />
                </AreaChart>
              </ResponsiveContainer>
            </div>
          </div>
        </div>

        <div className="card">
          <div className="card-header">
            <span className="card-title">⏱ Latency & Error Rate</span>
          </div>
          <div className="card-body">
            <div className="chart-container">
              <ResponsiveContainer width="100%" height="100%">
                <LineChart data={rpsHistory}>
                  <CartesianGrid strokeDasharray="3 3" stroke="rgba(255,255,255,0.04)" />
                  <XAxis dataKey="t" tick={{ fill: '#4a5568', fontSize: 10 }} interval={9} />
                  <YAxis yAxisId="left" tick={{ fill: '#4a5568', fontSize: 10 }} />
                  <YAxis yAxisId="right" orientation="right" tick={{ fill: '#4a5568', fontSize: 10 }} />
                  <Tooltip content={<CustomTooltip />} />
                  <Line yAxisId="left" type="monotone" dataKey="latency" name="Latency (ms)"
                    stroke="#8b5cf6" strokeWidth={2} dot={false} />
                  <Line yAxisId="right" type="monotone" dataKey="errors" name="Error %"
                    stroke="#ef4444" strokeWidth={2} dot={false} strokeDasharray="4 2" />
                </LineChart>
              </ResponsiveContainer>
            </div>
          </div>
        </div>
      </div>

      {/* ── Endpoints + Alerts ── */}
      <div className="dashboard-grid grid-3" style={{ marginBottom: 20 }}>
        <div className="card">
          <div className="card-header">
            <span className="card-title">🔗 Top Endpoints</span>
          </div>
          <div className="card-body">
            <div className="endpoint-list">
              {topEp.length === 0
                ? <div style={{ color: 'var(--text-muted)', fontSize: 13 }}>Waiting for traffic…</div>
                : topEp.map(ep => (
                  <div key={ep.endpoint} className="endpoint-row">
                    <div className="endpoint-header">
                      <span className="endpoint-name">{ep.endpoint}</span>
                      <span className="endpoint-count">{fmt(ep.requestCount)}</span>
                    </div>
                    <div className="progress-bar">
                      <div className="progress-fill"
                        style={{ width: `${(ep.requestCount / maxCount) * 100}%` }} />
                    </div>
                  </div>
                ))
              }
            </div>
          </div>
        </div>

        <div className="card">
          <div className="card-header">
            <span className="card-title">🚨 Live Alerts</span>
            <span style={{ fontSize: 12, color: 'var(--text-muted)' }}>{recentAlerts.length} recent</span>
          </div>
          <div className="card-body" style={{ padding: '12px 16px' }}>
            <div className="alert-list">
              {recentAlerts.length === 0
                ? <div style={{ color: 'var(--text-muted)', fontSize: 13 }}>No alerts yet</div>
                : recentAlerts.map(a => (
                  <div key={a.alertId} className={`alert-item ${a.severity}`}>
                    <span className={`alert-severity severity-${a.severity}`}>{a.severity}</span>
                    <div className="alert-content">
                      <div className="alert-title">{a.title}</div>
                      <div className="alert-msg">{a.message}</div>
                    </div>
                    <span className="alert-time">
                      {new Date(a.timestamp).toLocaleTimeString()}
                    </span>
                  </div>
                ))
              }
            </div>
          </div>
        </div>
      </div>

      {/* ── Region + Bypass + Error codes ── */}
      <div className="dashboard-grid grid-2">
        <div className="card">
          <div className="card-header">
            <span className="card-title">🌍 Region Distribution</span>
          </div>
          <div className="card-body">
            <div className="chart-container">
              <ResponsiveContainer width="100%" height="100%">
                <PieChart>
                  <Pie data={regionData} cx="50%" cy="50%" outerRadius={80}
                    dataKey="value" nameKey="name" label={({ name, percent }) =>
                      `${name} ${(percent * 100).toFixed(0)}%`}
                    labelLine={{ stroke: 'rgba(255,255,255,0.2)' }}>
                    {regionData.map((_, i) => (
                      <Cell key={i} fill={COLORS[i % COLORS.length]} />
                    ))}
                  </Pie>
                  <Tooltip content={<CustomTooltip />} />
                </PieChart>
              </ResponsiveContainer>
            </div>
          </div>
        </div>

        <div className="card">
          <div className="card-header">
            <span className="card-title">❌ Error Code Breakdown</span>
          </div>
          <div className="card-body">
            <div className="chart-container">
              <ResponsiveContainer width="100%" height="100%">
                <BarChart data={errData} layout="vertical">
                  <CartesianGrid strokeDasharray="3 3" stroke="rgba(255,255,255,0.04)" />
                  <XAxis type="number" tick={{ fill: '#4a5568', fontSize: 10 }} />
                  <YAxis type="category" dataKey="name" tick={{ fill: '#8899bb', fontSize: 11 }} width={40} />
                  <Tooltip content={<CustomTooltip />} />
                  <Bar dataKey="value" name="Count" radius={[0, 4, 4, 0]}>
                    {errData.map((e, i) => (
                      <Cell key={i}
                        fill={e.name >= '500' ? '#ef4444' : e.name >= '400' ? '#f59e0b' : '#3b82f6'} />
                    ))}
                  </Bar>
                </BarChart>
              </ResponsiveContainer>
            </div>
          </div>
        </div>

        {/* ── Bypass types ── */}
        <div className="card" style={{ gridColumn: '1 / -1' }}>
          <div className="card-header">
            <span className="card-title">🛡️ Bypass Type Analysis</span>
          </div>
          <div className="card-body">
            <div className="bypass-items">
              {bypassData.length === 0
                ? <div style={{ color: 'var(--text-muted)', fontSize: 13 }}>No bypass events detected yet</div>
                : bypassData.map(b => (
                  <div key={b.type} className="bypass-row">
                    <div className="bypass-type">
                      <span className={`bypass-badge badge-${b.type}`}>{b.type}</span>
                      <span style={{ fontSize: 13 }}>{getBypasDesc(b.type)}</span>
                    </div>
                    <span style={{ fontWeight: 700, color: 'var(--text-primary)' }}>{fmt(b.count)}</span>
                  </div>
                ))
              }
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}

function getBypasDesc(type) {
  const map = {
    DIRECT:   'Direct internal endpoint access (bypassing gateway)',
    RETRY:    'Repeated retries after failure responses',
    FAILOVER: 'Route switch after 503/504 (load-balancer bypass)',
    ALTERNATE:'Alternate path to same resource',
  };
  return map[type] || type;
}
