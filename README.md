# TrueSignal

**Distributed Uptime Monitoring Platform**

A production-grade, distributed monitoring system that checks website availability from multiple independent nodes, uses **quorum-based consensus** to eliminate false positives, and provides real-time dashboards, alerting, and public status pages.

> *"If 2 out of 3 monitoring nodes agree your site is down, TrueSignal creates an incident and fires alerts. One node having a network blip won't page you at 3 AM."*

---


## Project Structure

```
TrueSignal/
├── pom.xml                          # Parent POM (multi-module)
├── README.md
├── truesignal-common/               # Shared models, DTOs, enums
│   └── com.truesignal.common
│       ├── dto/                     # CreateMonitorRequest, MonitorResponse, ...
│       ├── enums/                   # MonitorType, CheckStatus, NodeStatus, ...
│       └── protocol/               # NodeRegistration, Heartbeat, CheckResult
├── truesignal-server/               # Central server + coordinator
│   └── com.truesignal.server
│       ├── controller/              # REST API endpoints
│       ├── service/                 # Business logic
│       ├── entity/                  # JPA entities
│       ├── repository/              # Data access
│       ├── coordinator/             # ConsistentHashRing, ClusterCoordinator
│       ├── quorum/                  # QuorumEngine (majority vote)
│       ├── diagnosis/               # DiagnosisEngine, AiDiagnosisService, rules/
│       ├── alerting/                # AlertEngine, Webhook/Email/Console channels
│       ├── sse/                     # SseService (real-time events)
│       └── config/                  # TrueSignalProperties, WebConfig
├── truesignal-monitor/              # Distributed monitor node
│   └── com.truesignal.monitor
│       ├── checker/                 # HTTP, TCP, SSL, DNS health checkers
│       ├── scheduler/               # Check scheduling with jitter
│       ├── reporter/                # Result reporting to server
│       └── heartbeat/               # Heartbeat + node registration
├── truesignal-cli/                  # Command-line interface
│   └── com.truesignal.cli
│       ├── client/                  # API client (java.net.http)
│       └── formatter/               # ANSI table formatting
└── truesignal-server/src/main/resources/static/
    ├── index.html                   # Dashboard SPA
    ├── status.html                  # Public status page
    ├── css/dashboard.css
    └── js/
        ├── api.js                   # REST + SSE client
        ├── charts.js                # Chart.js helpers
        └── app.js                   # Dashboard logic
```

---

## Getting Started

### Prerequisites
- Java 17+
- Maven 3.8+

### Build

```bash
cd TrueSignal
mvn clean package -DskipTests
```

### Run the Server

```bash
java -jar truesignal-server/target/truesignal-server-1.0.0.jar
```

Server starts on `http://localhost:8080`. Open the dashboard at [http://localhost:8080](http://localhost:8080).

### Run Monitor Nodes

Start 3 monitor nodes in separate terminals to form a cluster:

```bash
# Node 1 — US region
java -jar truesignal-monitor/target/truesignal-monitor-1.0.0.jar \
  --truesignal.monitor.region=us-east \
  --truesignal.monitor.server-url=http://localhost:8080

# Node 2 — EU region
java -jar truesignal-monitor/target/truesignal-monitor-1.0.0.jar \
  --truesignal.monitor.region=eu-west \
  --truesignal.monitor.server-url=http://localhost:8080

# Node 3 — AP region
java -jar truesignal-monitor/target/truesignal-monitor-1.0.0.jar \
  --truesignal.monitor.region=ap-south \
  --truesignal.monitor.server-url=http://localhost:8080
```

### Add Monitors via CLI

```bash
# Show help
java -jar truesignal-cli/target/truesignal-cli-1.0.0.jar help

# Add an HTTP monitor
java -jar truesignal-cli/target/truesignal-cli-1.0.0.jar add \
  --name "Google" --url "https://www.google.com" --type HTTP --interval 60

# Add an SSL certificate monitor
java -jar truesignal-cli/target/truesignal-cli-1.0.0.jar add \
  --name "GitHub SSL" --url "github.com" --type SSL --interval 300

# List monitors
java -jar truesignal-cli/target/truesignal-cli-1.0.0.jar list

# Check cluster status
java -jar truesignal-cli/target/truesignal-cli-1.0.0.jar nodes

# View dashboard overview
java -jar truesignal-cli/target/truesignal-cli-1.0.0.jar status
```

### Or Use the REST API Directly

```bash
# Create a monitor
curl -X POST http://localhost:8080/api/monitors \
  -H "Content-Type: application/json" \
  -d '{"name":"Example","url":"https://example.com","type":"HTTP","intervalSeconds":60}'

# List monitors
curl http://localhost:8080/api/monitors

# View cluster nodes
curl http://localhost:8080/api/nodes

# View public status page
curl http://localhost:8080/api/status/default
```

---

## How to Demo

1. **Start the server** and open the dashboard at `http://localhost:8080`
2. **Start 3 monitor nodes** — watch them register in the Cluster view
3. **Add monitors** — use the CLI or dashboard to add `https://httpbin.org/status/200`
4. **Watch real-time checks** — see results flowing in from 3 regions simultaneously
5. **Simulate an outage** — add `https://httpbin.org/status/500` and watch the quorum detect it, create an incident, and fire alerts
6. **Kill a node** — Ctrl+C one monitor node, watch the heartbeat failure detection mark it SUSPECT then DEAD, and reassign its monitors
7. **Check the status page** — open `http://localhost:8080/status.html?project=default`

---

## Configuration

### Server (`application.yml`)

```yaml
truesignal:
  quorum:
    threshold: 0.5              # >50% nodes must agree on DOWN
    evaluation-interval-ms: 5000
  heartbeat:
    timeout-suspect-ms: 15000   # 15s without heartbeat = SUSPECT
    timeout-dead-ms: 30000      # 30s = DEAD, monitors reassigned
    check-interval-ms: 10000
  coordinator:
    virtual-nodes: 150          # Virtual nodes per physical node in hash ring
  ai:
    enabled: false              # Set to true + provide API key to enable AI diagnosis
    api-key: ${GEMINI_API_KEY:} # Google Gemini API key (free tier available)
    model: gemini-2.0-flash     # Fast, cost-effective model
    base-url: https://generativelanguage.googleapis.com
```

### Monitor Node

```yaml
truesignal:
  monitor:
    server-url: http://localhost:8080
    region: us-east
    heartbeat-interval-ms: 5000
```

---

## Running Tests

```bash
mvn test
```

Tests cover:
- **ConsistentHashRingTest** (9 tests) — Distribution uniformity, assignment consistency, node add/remove, replication (multi-node assignment), empty ring edge case, full monitor accounting
- **QuorumLogicTest** (10 tests) — All-up, all-down, majority-down, majority-up, exact threshold boundary, degraded-counts-as-down, single-node scenarios, high threshold, five-node cluster

---

