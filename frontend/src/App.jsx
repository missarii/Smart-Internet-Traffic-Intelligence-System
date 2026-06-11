import React, { useState, useEffect, useRef, useCallback } from 'react';
import './index.css';
import { connectWebSocket, subscribe } from './services/websocket';
import { trafficAPI } from './services/api';
import Dashboard from './pages/Dashboard';
import AlertPanel from './components/AlertPanel';
import TrafficHeatmap from './pages/TrafficHeatmap';

const NAV_ITEMS = [
  { id: 'dashboard',  icon: '📊', label: 'Live Dashboard' },
  { id: 'heatmap',    icon: '🔥', label: 'Latency Heatmap' },
  { id: 'alerts',     icon: '🚨', label: 'Alert Center' },
  { id: 'endpoints',  icon: '🔗', label: 'Endpoints' },
  { id: 'bypass',     icon: '🛡️', label: 'Bypass Detection' },
];

export default function App() {
  const [activePage, setActivePage]     = useState('dashboard');
  const [connected, setConnected]       = useState(false);
  const [report, setReport]             = useState(null);
  const [alerts, setAlerts]             = useState([]);
  const [spikeActive, setSpikeActive]   = useState(false);
  const clientRef = useRef(null);

  useEffect(() => {
    clientRef.current = connectWebSocket(() => {
      setConnected(true);
      subscribe('/topic/traffic', (data) => {
        setReport(data);
        setSpikeActive(data.spikeActive);
      });
      subscribe('/topic/alerts', (alert) => {
        setAlerts(prev => [alert, ...prev].slice(0, 100));
      });
    });

    clientRef.current.onDisconnect = () => setConnected(false);

    // Load initial alert history
    trafficAPI.getAlerts(50).then(r => setAlerts(r.data)).catch(() => {});

    return () => { if (clientRef.current) clientRef.current.deactivate(); };
  }, []);

  const triggerSpike = useCallback(async (active) => {
    await trafficAPI.triggerSpike(active);
  }, []);

  return (
    <div className="app-container">
      {/* ── Topbar ── */}
      <header className="topbar">
        <div className="topbar-brand">
          <div className="logo-icon">⚡</div>
          <span>TrafficIQ</span>
          <span style={{ color: 'var(--text-muted)', fontWeight: 400, fontSize: 13 }}>
            Intelligence Platform
          </span>
        </div>
        <div className="topbar-right">
          {spikeActive && (
            <span className="spike-badge">⚠ SPIKE ACTIVE</span>
          )}
          {report && (
            <span className="status-pill status-{report.systemStatus}">
              {report.systemStatus || 'HEALTHY'}
            </span>
          )}
          <span className={`connection-badge ${connected ? '' : 'disconnected'}`}>
            <span className="connection-dot" />
            {connected ? 'Live' : 'Disconnected'}
          </span>
        </div>
      </header>

      {/* ── Sidebar ── */}
      <aside className="sidebar">
        <div className="sidebar-label">Navigation</div>
        {NAV_ITEMS.map(item => (
          <div key={item.id} className="sidebar-section">
            <div
              className={`nav-item ${activePage === item.id ? 'active' : ''}`}
              onClick={() => setActivePage(item.id)}
            >
              <span className="nav-icon">{item.icon}</span>
              {item.label}
            </div>
          </div>
        ))}

        <div className="sidebar-label" style={{ marginTop: 24 }}>Controls</div>
        <div className="sidebar-section">
          <button className="btn btn-danger" style={{ width: '100%', justifyContent: 'center' }}
            onClick={() => triggerSpike(true)}>
            🔴 Trigger Spike
          </button>
        </div>
        <div className="sidebar-section" style={{ marginTop: 8 }}>
          <button className="btn btn-success" style={{ width: '100%', justifyContent: 'center' }}
            onClick={() => triggerSpike(false)}>
            ✅ Stop Spike
          </button>
        </div>
      </aside>

      {/* ── Main ── */}
      <main className="main-content">
        {activePage === 'dashboard' && <Dashboard report={report} alerts={alerts} />}
        {activePage === 'heatmap'   && <TrafficHeatmap report={report} />}
        {activePage === 'alerts'    && <AlertPanel alerts={alerts} />}
        {activePage === 'endpoints' && <Dashboard report={report} alerts={alerts} defaultTab="endpoints" />}
        {activePage === 'bypass'    && <Dashboard report={report} alerts={alerts} defaultTab="bypass" />}
      </main>
    </div>
  );
}
