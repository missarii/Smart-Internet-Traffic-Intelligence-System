# ⚡ Smart Internet Traffic Intelligence System

> **Production-grade real-time internet traffic monitoring, bypass analytics, and load intelligence platform.**
> *Inspired by Cloudflare / AWS / Datadog internals.*

---

## 📖 About

The Smart Internet Traffic Intelligence System is an advanced, real-time web traffic monitoring and analytics platform designed to analyze the flow of users inside web systems. It is not about vehicle logistics, but rather the deep inspection of HTTP requests, endpoint usage, and user routing behavior.

By ingesting traffic events and processing them through a high-performance multithreaded engine, the system detects anomalies, bypass attempts, and load bottlenecks in real-time, providing DevOps and Security teams with immediate visibility and actionable alerts.

## 🌟 How It's Helpful

- **Security & Protection**: Automatically detects malicious behavior like direct endpoint bypasses, retry floods, and failover abuse.
- **System Reliability**: Identifies backend load bottlenecks and slow endpoints before they cause cascading failures.
- **DDoS Mitigation**: Uses Cloudflare-style Exponential Moving Average (EMA) to detect unnatural traffic spikes and suspected DDoS attacks.
- **Deep Observability**: Provides a live, second-by-second view of request rates, latencies, and error distributions across the entire platform.

---

## 🏗️ Architecture Pattern

The system follows a **Stream Processing & Real-Time Analytics Architecture**, leveraging a fast in-memory data store for live state and a columnar database for historical analytics.

```text
User Request (Simulated)
       ↓
Spring Boot API Gateway (8080)
       ↓
Multithreaded Event Processor (8 threads, 10K queue)
       ↓
Traffic Analyzer Engine
   ├── SpikeDetectionEngine   (EMA-based, 3× threshold)
   ├── BypassDetectionService (DIRECT / RETRY / FAILOVER)
   ├── LoadAnalyzer           (P95/P99 percentiles)
   └── SessionTrackingService (full request path + risk score)
       ↓
Redis (real-time state)          ClickHouse (analytics history)
   ├── RPS counters                 ├── traffic_events table
   ├── Rate limiting                ├── traffic_alerts table
   ├── Session tracking             ├── Time-series queries
   └── Spike baseline (EMA)         └── Anomaly detection queries
       ↓
WebSocket STOMP → React Dashboard (live updates every 1s)
```

---

## ⚙️ Features & Functionalities

| Feature | Description |
|---------|-------------|
| **Real-Time RPS Monitoring** | Tracks requests per second using sliding window counters in Redis. |
| **Spike & DDoS Detection** | Employs an Exponential Moving Average (EMA) baseline to detect 3x spikes and escalates to DDoS alerts if sustained. |
| **Bypass & Route Analysis** | Detects when users directly access internal APIs, retry excessively on failures, or abuse failover mechanisms. |
| **User Session Flow Tracking** | Maps the entire path of a user session (Entry → API calls → Exit) and assigns dynamic risk scores. |
| **Load & Stress Analysis** | Identifies bottleneck endpoints by tracking P50/P95/P99 latencies and error rates. |
| **Rate Limiting & Blocking** | Tracks per-IP request rates and automatically blocks abusive IP addresses. |
| **Live WebSocket Dashboard** | A stunning React-based dashboard that updates every second with traffic charts, heatmaps, and live alerts. |
| **Synthetic Traffic Simulation** | Includes a built-in simulator that generates normal traffic, bursts, and bypass attempts for demonstration purposes. |

---

## 🛠️ Used Technologies

- **Backend Development**: Java 25, Spring Boot 3.2
- **Concurrency**: Multithreading via `ExecutorService` and bounded blocking queues.
- **Real-Time State Management**: Redis 7
- **Analytics Database**: ClickHouse 24
- **Real-Time Communication**: WebSockets with STOMP protocol
- **Frontend Development**: React 18, Recharts for data visualization
- **Containerization**: Docker & Docker Compose
- **Web Server / Proxy**: Nginx

*(See `techstack.md` for a deeper breakdown).*

---

## 🚀 Quick Start

```bash
# Clone the repository
git clone https://github.com/missarii/Smart-Internet-Traffic-Intelligence-System.git
cd Smart-Internet-Traffic-Intelligence-System

# Run the entire stack
docker-compose up --build
```

| Service | Access URL |
|---------|------------|
| **Live Dashboard** | `http://localhost:3000` |
| **Backend API Gateway** | `http://localhost:8080` |
| **ClickHouse HTTP Interface**| `http://localhost:8123` |
