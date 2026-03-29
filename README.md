# F&B ERP System

Microservices Architecture for 300 Outlet Chain

## Overview

This is a comprehensive enterprise resource planning (ERP) system designed for a large F&B chain with approximately 300 outlets and 10,000 employees. The system implements an online-only POS architecture with microservices, event-driven design, and a PostgreSQL-centered data topology.

## Key Features

- **Multi-outlet Management**: Support for 300 outlets organized in Company → Region → Outlet hierarchy
- **Online-only POS**: Low-latency point of sale system optimized for high transaction volume
- **Microservices Architecture**: 12 bounded-context services with clear data ownership
- **Event-Driven Design**: Kafka-based event backbone for async communication
- **Data Topology**: PostgreSQL Master for master/projection/reporting schemas and PostgreSQL Operational for shard-ready transactional workloads
- **Scope-Based Authorization**: JWT stateless tokens with Redis blacklist
- **Comprehensive ERP Coverage**: POS, Inventory, Procurement, HR, Finance, Reporting

## Project Structure

```
.
├── docs/                    # Architecture and implementation documentation
├── services/                # 12 microservices
│   ├── api-gateway/
│   ├── iam-service/
│   ├── org-service/
│   ├── catalog-service/
│   ├── pos-service/
│   ├── inventory-service/
│   ├── procurement-service/
│   ├── hr-service/
│   ├── finance-service/
│   ├── report-service/
│   ├── audit-service/
│   └── notification-service/
├── infrastructure/          # Infrastructure configuration
│   ├── kafka/
│   ├── postgres/
│   └── redis/
├── deployment/              # Kubernetes and deployment configs
└── shared-libraries/        # Common configurations and libraries
```

## Architecture Highlights

### Data Topology

1. **Master Data Layer**: Central PostgreSQL for IAM, Org, Catalog, Procurement Master, HR Master, Configuration, audit, notification, and reporting projections
2. **Operational Data Layer**: PostgreSQL transactional schemas with region/outlet routing keys and shard-ready design
3. **Identifier Strategy**: Reporting/projection tables use Snowflake ID strategy as `BIGINT` primary keys while APIs serialize them as strings

### Core Services

| Service | Responsibility |
|---------|---------------|
| API Gateway | JWT validation, routing, rate limiting |
| IAM Service | Identity, authentication, authorization |
| Org Service | Region, outlet, hierarchy management |
| Catalog Service | Products, recipes, pricing, tax |
| POS Service | Orders, payments, sessions |
| Inventory Service | Stock ledger, balance, movements |
| Procurement Service | Purchase orders, goods receipt |
| HR Service | Employees, shifts, attendance |
| Finance Service | Payroll, expenses, accounting |
| Report Service | BI, aggregates, exports |
| Audit Service | Security trail, event logging |
| Notification Service | Alerts, webhooks, DLQ monitoring |

## Technology Stack

- **Language**: Java 21
- **Framework**: Spring Boot
- **Database**: PostgreSQL (Master/Operational)
- **Messaging**: Apache Kafka
- **Cache**: Redis
- **Deployment**: Docker, Kubernetes
- **Observability**: OpenTelemetry, structured logging

## Implementation Phases

### Phase 1: V1 (MVP) - 3 Months
- Core services implementation
- 3-layer database topology
- Kafka event backbone
- Basic reporting and dashboards

### Phase 2: V2 - 6 Months
- Physical sharding by region
- Performance optimization
- Advanced reporting capabilities
- Enhanced monitoring and alerting

## Documentation

Comprehensive documentation is available in the `docs/` directory:

- [Software Architecture Document](docs/architecture/SAD.md) - Complete architecture specification
- [Implementation Plan](docs/implementation-plan.md) - High-level implementation strategy
- [Detailed Implementation Plan](docs/detailed-implementation-plan.md) - Step-by-step implementation guide
- [Implementation Roadmap](docs/implementation-roadmap.md) - Timeline and milestones
- [Database Design](docs/database-design.md) - Data architecture and schema design
- [Development Workflow](docs/development-workflow.md) - Development processes and guidelines
- [Deployment Guide](deployment/DEPLOYMENT.md) - Deployment procedures
- [Infrastructure Setup](infrastructure/INFRASTRUCTURE.md) - Infrastructure configuration
- [Load Test Harness](tests/load/README.md) - Staging-first k6 scenarios, fixtures, and orchestration scripts

## Getting Started

1. **Prerequisites**
   - Java 21
   - Maven
   - Docker
   - Kubernetes cluster (minikube/k3s for development)

2. **Setup Development Environment**
   ```bash
   # Clone the repository
   git clone <repository-url>

   # Navigate to project directory
   cd fern-erp-system

   # Set up local infrastructure (Docker Compose)
   docker compose up -d kafka redis postgres

   # Prepare database migration settings
   cp infrastructure/migration.env.example .env.migrations

   # Run all PostgreSQL migrations
   ./scripts/migrate-platform.sh all

   # One-command local bootstrap + smoke flow
   ./scripts/bootstrap-local.sh
   ```

   Local Docker PostgreSQL is exposed on `127.0.0.1:55432` by default to avoid clashing with a host PostgreSQL already using `5432`.

3. **Build and Run Services**
   ```bash
   # Build all services
   ./mvnw clean install

   # Run individual service
   cd services/api-gateway
   ../../mvnw spring-boot:run
   ```

## Key Architectural Principles

1. **Bounded Context**: Each service owns its data and business logic
2. **Event-Driven**: Async communication via Kafka for decoupling
3. **CQRS**: Separate read/write models for performance
4. **Event Sourcing**: Append-only ledgers for auditability
5. **Scope-Based Authorization**: JWT with region/outlet scoping
6. **Consistency-Aware Routing**: Smart routing based on consistency requirements

## Risk Mitigation

- **Projection Lag**: Monitor consumer lag and projection freshness
- **Replica Stale Read**: Consistency-aware routing strategies
- **Scope Resolution**: JWT scope roots + org tree caching
- **Event Contract Drift**: Schema registry with versioning

## Contributing

See [Development Workflow](docs/development-workflow.md) for development guidelines and processes.

## License

Proprietary - All rights reserved

## Contact

For questions or support, contact the development team.# FERN
