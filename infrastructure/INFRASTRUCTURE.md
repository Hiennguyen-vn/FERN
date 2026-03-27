# Infrastructure Setup Guide

This document provides guidance for setting up the infrastructure components for the F&B ERP System.

## 1. Database Setup

### Unified Migration Command

Use the repo-level migration script when you want one command to apply every owned schema migration:

```bash
cp infrastructure/migration.env.example .env.migrations
./scripts/migrate-platform.sh all
```

`all` now applies every owned PostgreSQL schema, including reporting, audit, notification, and finance projection schemas in `fern_master`.
The Docker PostgreSQL service is exposed on host port `55432` by default to avoid collisions with a developer's local PostgreSQL on `5432`.

Useful variants:

```bash
./scripts/migrate-platform.sh master
./scripts/migrate-platform.sh operational
./scripts/migrate-platform.sh master --action validate
```

For one-command local bootstrap with Docker, PostgreSQL migrations, and the gateway/IAM/Org smoke flow:

```bash
./scripts/bootstrap-local.sh
```

### 1.1 Master Database (PostgreSQL)

The Master database contains system-wide configuration data including:
- User and role definitions
- Organization hierarchy
- System configurations
- Reference data

#### Setup Steps:
1. Create PostgreSQL database instance
2. Configure database users and permissions
3. Create required schemas
4. Set up backup and recovery procedures

### 1.2 Operational Database (PostgreSQL Shards)

Operational databases contain transactional data for daily operations:
- Sales transactions
- Inventory movements
- Employee records

#### Setup Steps:
1. Configure regional database shards
2. Set up replication for read scalability
3. Configure backup and recovery

### 1.3 Reporting And Projection Schemas (PostgreSQL Master)

The PostgreSQL master database also contains aggregated and projection data for analytics and operational observability:
- Sales reports
- Performance metrics
- Business intelligence data
- Audit and notification projections

#### Setup Steps:
1. Create PostgreSQL master database `fern_master`
2. Run master migrations to create `raw_events`, `report`, `finance_projection`, `audit`, and `notification`
3. Configure Kafka-based projection consumers
4. Configure retention, vacuum, and backup policy

## 2. Messaging Infrastructure (Kafka)

### 2.1 Kafka Cluster Setup

Kafka serves as the event streaming backbone for the system.

#### Setup Steps:
1. Install and configure Kafka cluster
2. Create required topics
3. Configure replication and retention policies
4. Set up monitoring and alerting

### 2.2 Kafka Topics

Required topics for V1:
- order.paid
- order.cancelled
- goods_receipt.posted
- stock_count.approved
- attendance.approved
- payroll.calculated
- payroll.posted
- price.changed
- recipe.version_activated

## 3. Caching Infrastructure (Redis)

### 3.1 Redis Setup

Redis is used for:
- Token blacklisting
- Session management
- Caching frequently accessed data

#### Setup Steps:
1. Install and configure Redis cluster
2. Configure persistence settings
3. Set up monitoring and alerting

## 4. API Gateway Setup

### 4.1 API Gateway Configuration

The API Gateway handles:
- Request routing
- Authentication and authorization
- Rate limiting
- Security policies

#### Setup Steps:
1. Configure JWT validation
2. Set up service routing rules
3. Configure rate limiting policies
4. Set up monitoring and logging

## 5. Deployment Configuration

### 5.1 Kubernetes Deployment

All services are deployed using Kubernetes with the following components:

#### Core Services:
- API Gateway
- IAM Service
- Org Service
- Catalog Service
- POS Service
- Inventory Service
- Procurement Service
- HR Service
- Finance Service
- Report Service
- Audit Service
- Notification Service

### 5.2 Environment Configuration

Each service requires the following environment variables:
- DATABASE_URL - Database connection string
- KAFKA_BOOTSTRAP_SERVERS - Kafka bootstrap servers
- REDIS_HOST - Redis server hostname
- JWT_SECRET_KEY - JWT secret key
- SERVICE_DISCOVERY_URL - Service discovery endpoint
- FERN_ID_GENERATOR_NODE_ID - Snowflake ID generator node identifier for reporting/projection writes

## 6. Monitoring and Logging

### 6.1 Application Monitoring

Required monitoring components:
- Health check endpoints for all services
- Performance metrics collection
- Error tracking and alerting
- Database performance monitoring
- Kafka consumer lag monitoring

### 6.2 Logging Configuration

All services must implement structured logging with:
- Timestamp
- Service name
- Correlation ID
- Request ID
- User ID (when available)
- Outlet/Region ID (when applicable)
- Event ID (for async handlers)
