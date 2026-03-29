# F&B ERP System - Database Design

## Overview

The F&B ERP System uses a three-layer database architecture:
1. Master Data Layer
2. Operational Data Layer
3. Reporting Data Layer

The canonical ownership map, schema inventory, and migration locations are documented in [database-topology-reconciled.md](/Users/nguyenhien/Documents/FERN/docs/database-topology-reconciled.md).

## Database Layer Implementation

### Master Data Layer
- PostgreSQL
- Contains system-wide configuration data
- Organization hierarchy information
- User and role definitions
- System configuration settings

### Operational Data Layer
- PostgreSQL
- Transactional data for daily operations
- Region and outlet specific information
- Real-time business data

### Reporting Data Layer
- Snowflake
- Aggregated and denormalized data for reporting
- Business intelligence and analytics data
- Historical data analysis

## Database Schema Strategy

### Master Data Schema
- Users and roles
- Organization structure
- System configurations
- Product catalogs
- Procurement supplier master
- HR employee master

### Operational Data Schema
- Sales transactions
- Inventory movements
- Procurement records
- Attendance and payroll source-of-truth

### Reporting Schema
- Raw event landing
- Aggregated sales data
- Performance metrics
- Historical trends
- Business intelligence reports
- Audit and notification projections

## Data Consistency Model

1. Master Data: PostgreSQL, centrally managed, strong consistency inside service boundaries
2. Operational Data: PostgreSQL, strong consistency inside service boundaries
3. Reporting Data: Snowflake, eventually consistent, optimized for read-heavy projection workloads
