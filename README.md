# BookIt Platform 🎟️

> Production-grade cinema ticket booking platform built with Java 21, Spring Boot 3, PostgreSQL, Redis, and Apache Kafka.

[![CI](https://github.com/AmanPrasadDev/bookit-platform/actions/workflows/ci.yml/badge.svg)](https://github.com/AmanPrasadDev/bookit-platform/actions/workflows/ci.yml)

---

## Architecture Overview

BookIt is a domain-driven, modular monolith designed from first principles to solve critical ticketing challenges:
- **Zero Duplicate Seat Sales:** Concurrency-safe all-or-nothing seat locking with deterministic pessimistic row-locking (`SELECT FOR UPDATE`).
- **Resilient Financial Transactions:** Outbox pattern with Apache Kafka, HMAC webhook verification, and bounded payment reconciliation.
- **High-Performance Discovery:** Multi-tiered cache-aside with Redis, sliding-window rate limiting, and cursor-paginated endpoints.
- **Enterprise Security:** OAuth 2.0 / OpenID Connect (Google), rotating cryptographic JWT tokens, and fine-grained RBAC.

---

## Tech Stack

| Component | Technology | Purpose |
|---|---|---|
| **Language** | Java 21 (LTS) | Records, sealed classes, pattern matching |
| **Framework** | Spring Boot 3.3.x | WebMVC, Security, Data JPA, Actuator |
| **Database** | PostgreSQL 16 | Relational persistence, transactional locking |
| **Migration** | Flyway | Versioned database schema evolution |
| **Cache & Limits**| Redis 7 | Cache-aside, distributed rate-limiting counters |
| **Messaging** | Apache Kafka (KRaft) | Event-driven transactional outbox & async consumers |
| **Build & CI** | Maven, GitHub Actions | Automated multi-stage testing & verification |

---

## Local Development Setup

### Prerequisites
- Java 21 JDK (Eclipse Temurin recommended)
- Docker Desktop
- Git

### 1. Clone & Configure Environment
```bash
git clone git@github.com-personal:AmanPrasadDev/bookit-platform.git
cd bookit-platform

# Copy the environment template
cp .env.example .env.local
```

### 2. Start Core Infrastructure
```bash
docker compose --env-file .env.local up -d
```

Verify services are healthy:
```bash
docker compose ps
```

### 3. Run the Application
```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=local
```

Access API Documentation & Actuator:
- Swagger UI: `http://localhost:8080/swagger-ui/index.html`
- Health Check: `http://localhost:8080/actuator/health`

### 4. Run Test Suite
```bash
./mvnw test
```

---

## Development Milestones

- [x] **M0:** Project Skeleton & Docker Infrastructure
- [ ] **M1:** Identity & Access Management (Google OIDC + JWT + RBAC)
- [ ] **M2:** Catalogue & Venue Management
- [ ] **M3:** Shows & Materialized Inventory
- [ ] **M4:** High-Concurrency Seat Holds & Invariant Verification
- [ ] **M5:** Pricing & Dynamic Quotation Snapshots
- [ ] **M6:** Booking Orchestration & Payment Reconciliation
- [ ] **M7:** QR Ticket Issuance & Cryptographic Scan Validation
- [ ] **M8:** Transactional Outbox & Event Pipeline (Kafka)
- [ ] **M9:** Background Workers (Hold Expiry & Payment Reconciliation)
- [ ] **M10:** Asynchronous Notifications
- [ ] **M11:** Redis Caching & Endpoint Rate Limiting
- [ ] **M12:** Distributed Tracing & Observability (Grafana + Prometheus + Jaeger)

---

## Author
**Aman Prasad** — [GitHub](https://github.com/AmanPrasadDev)
