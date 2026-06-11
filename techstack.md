# 🧰 Technology Stack

The **Smart Internet Traffic Intelligence System** is built on a modern, highly concurrent, and scalable technology stack designed for real-time observability.

## 🧑‍💻 Backend Engine
- **Language**: Java 25
- **Framework**: Spring Boot 3.2
- **Concurrency**: Native Java `ExecutorService` with custom thread pools and `LinkedBlockingQueue` for backpressure handling.
- **REST APIs**: Spring Web MVC
- **Scheduling**: Spring `@Scheduled` tasks for background analysis and metric generation.

## ⚡ Real-Time Processing & State Management
- **In-Memory Store**: Redis 7.2
- **Client**: Lettuce (async/reactive Redis client)
- **Usage**:
  - Sliding window counters for RPS (Requests Per Second).
  - Rate limiting registries.
  - Active session tracking and caching.
  - Tracking system baselines (EMA).

## 📊 Analytics Storage Layer
- **Database**: ClickHouse 24.3
- **Engine**: `MergeTree` (optimized for time-series and large-scale log ingestion).
- **Driver**: ClickHouse JDBC Driver 0.6.0
- **Usage**:
  - Persistent storage of all HTTP request events.
  - Time-series querying.
  - Historical anomaly detection.

## 🔄 Real-Time Communication
- **Protocol**: WebSockets over STOMP (Simple Text Oriented Messaging Protocol)
- **Backend Component**: Spring WebSocket Message Broker
- **Frontend Client**: `@stomp/stompjs` and `sockjs-client`
- **Usage**: Pushing second-by-second analytics updates and instantaneous alert notifications to the frontend.

## 🌐 Frontend Dashboard
- **Framework**: React 18 (SPA)
- **Styling**: Pure CSS with CSS Variables for a modern, dark-mode glassmorphism aesthetic.
- **Data Visualization**: Recharts (Area, Line, Bar, and Pie charts).
- **HTTP Client**: Axios

## 🐳 Infrastructure & Deployment
- **Containerization**: Docker
- **Orchestration**: Docker Compose (`docker-compose.yml`)
- **Reverse Proxy / Web Server**: Nginx (serving the React SPA and proxying API/WebSocket requests to the backend).
