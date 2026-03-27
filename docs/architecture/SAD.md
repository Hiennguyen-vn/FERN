# Software Architecture Document

F&B ERP System — Microservices Architecture for 300 Outlet Chain

## 1. Purpose of Document

This document describes the overall software architecture for the F&B ERP system with an online-only POS model and microservices approach. The purpose of this document is to:
- Define service boundaries and data ownership
- Standardize database topology
- Describe read/write flows, event flows, authN/authZ and routing
- Provide a practical deployment roadmap from V1 to V2
- Serve as foundation for subsequent detailed documents such as:
  - API contracts
  - Event contracts
  - Schema design
  - Deployment design
  - Observability design
  - Security design

## 2. System Scope

The system serves an F&B chain with approximately:
- 300 outlets
- 10,000 employees
- Online-only POS
- Outlets serve as both:
  - Sales points
  - Local warehouses
- Organizational structure:
  - Company
  - Region
  - Outlet

The system covers the following main functional areas:
- IAM / Identity & Access
- Organization / Region / Outlet
- Catalog / Product / Recipe / Pricing / Tax
- POS / Sales / Payment
- Inventory / Stock Count / Waste / Adjustment
- Procurement / Purchase Order / Goods Receipt
- HR / Attendance / Shift
- Finance / Payroll / Expense / CoA
- Reporting / BI / Export
- Audit / Security trail
- Notification / Alert / Webhook

## 3. Architectural Goals

### 3.1 Primary Goals

The architecture must meet the following objectives:
- Support stable operation for 300 outlets
- Maintain low latency for POS critical path
- Clearly separate OLTP path and reporting path
- Support scope-based authorization by region/outlet
- Allow evolution from mid-scale to large-scale without breaking logical design
- Ensure auditability, idempotency and event traceability
- Be easier to operate and observe than an overly distributed model

### 3.2 Non-functional Goals

The architecture is optimized according to the following principles:
- Latency: POS order/payment must have short sync path
- Availability: Reporting/audit failure must not block sales transactions
- Consistency: Important business mutations must have clear source of truth
- Scalability: Scale according to region/outlet locality
- Security: JWT stateless, scope-aware authorization, revocation via Redis blacklist
- Traceability: Correlation_id throughout sync and async
- Operability: Support retry, DLQ, projection lag monitoring

## 4. Architectural Drivers

### 4.1 Business Drivers
- Outlet is the core operational unit
- Region managers need to view aggregated data from multiple outlets
- Company-level users need chain-wide reports
- POS is the most latency-sensitive workload
- Inventory and finance require high audit and correctness
- Pricing, tax, recipe are effective-dated data, must not be recalculated incorrectly over time

### 4.2 Technical Drivers
- Microservices according to bounded context
- Kafka as event backbone
- PostgreSQL as main OLTP platform and Snowflake as reporting platform
- Redis for cache and token blacklist
- API Gateway as single entry point
- Need to prepare early for read replica / shard / projection

## 5. Overall Architecture

### 5.1 Core Design Principles

The system architecture maintains microservice boundaries, but does not choose an overly simplified model where every service uses a shared PostgreSQL cluster with separate schemas as the final design.

Instead, the system is organized into 3 data layers:
1. Master / Central Data
2. Operational Data Shards
3. Reporting / Projection Data

This is the central decision of the architecture because it correctly reflects the system's characteristics:
- Catalog, IAM, org are global data
- Order, stock, attendance, PO/GR are operational data by outlet/region
- Reports, BI, summary should not directly hit OLTP

## 6. Services and Bounded Contexts

### 6.1 API Gateway

**Responsibilities**
- JWT authentication
- TLS termination
- Rate limiting
- Routing requests to appropriate service
- Generating and propagating correlation_id
- Checking token blacklist via Redis
- Implementing edge technical policies

**Not responsible for**
- Not implementing deep business authorization
- Not completely determining complex query routing
- Not maintaining business state

### 6.2 IAM Service

**Responsibilities**
- User identity
- Role
- Permission
- Role assignment
- Scope assignment
- Permission override / emergency access
- Auth policy metadata
- Login / logout orchestration if auth server is not separate

**Owned Data**
- user
- role
- permission
- role_permission
- user_role_assignment
- user_permission_override
- Token/session audit metadata

### 6.3 Org Service

**Responsibilities**
- Managing regions
- Managing outlets
- Hierarchy tree
- Mapping company → region → outlet
- Org configuration

**Owned Data**
- region
- outlet
- Hierarchy edges / closure
- Legal entity
- Org metadata
- Outlet operational configuration

### 6.4 Catalog Service

**Responsibilities**
- Product
- Ingredient
- Recipe
- Recipe version
- Price list
- Tax rule
- UOM and conversion
- Effective-dated catalog logic

**Owned Data**
- product
- ingredient
- recipe
- recipe_version
- product_price
- tax_rate / tax_rule
- unit_of_measure
- uom_conversion

### 6.5 POS Service

**Responsibilities**
- POS session
- Order
- Order line
- Payment
- Sale snapshot
- Cashier workflow
- Short, latency-sensitive order lifecycle

**Owned Data**
- pos_session
- sale_order
- sale_order_line
- sale_payment
- sale_snapshot
- POS outbox

### 6.6 Inventory Service

**Responsibilities**
- Inventory ledger
- Stock balance projection
- Stock count
- Waste
- Adjustment
- Availability read model
- Optional lot/batch

**Owned Data**
- inventory_transaction
- stock_balance
- stock_count
- waste_record
- stock_adjustment
- Availability projection
- Inventory inbox/outbox

### 6.7 Procurement Service

**Responsibilities**
- Purchase order
- Goods receipt
- Receiving workflow
- Supplier invoice business reference

**Owned Data**
- purchase_order
- purchase_order_line
- goods_receipt
- goods_receipt_line
- Receiving event outbox

### 6.8 HR Service

**Responsibilities**
- Employee assignment
- Shift scheduling
- Attendance events
- Attendance review/approval

**Owned Data**
- employee_assignment
- shift_schedule
- attendance_event
- attendance_approval

### 6.9 Finance Service

**Responsibilities**
- Payroll run
- Expense
- Reconciliation
- Accounting projection
- Immutable finance facts

**Owned Data**
- payroll_period
- payroll_run
- expense_record
- accounting_posting_projection
- Finance reconciliation models

### 6.10 Report Service

**Responsibilities**
- BI read model
- Export
- Regional/company aggregates
- Dashboard queries

**Owned Data**
- Denormalized reporting facts
- Summary tables
- Export jobs
- Report projection models

### 6.11 Audit Service

**Responsibilities**
- Append-only audit log
- Security trail
- Business event trace

**Owned Data**
- audit_event
- Request trace metadata
- Security event records

### 6.12 Notification Service

**Responsibilities**
- Alerts
- Webhook
- Ops notifications
- DLQ alerting

**Owned Data**
- Notification job
- Delivery attempts
- Webhook delivery logs
- Alert rules metadata if any

## 7. Data Ownership Model

### 7.1 Ownership Principles

Each domain object has only one service as the source of truth. Other services read through:
- Synchronous API
- Kafka events
- Projection/read model
- Cache read-through if appropriate

Multiple services are not allowed to write directly to the same business table.

### 7.2 Source-of-Truth vs Read Model

Clear distinction between:

**Source of truth**
- Located at the service that owns that domain
- Used for mutation and business correctness
- Requires higher consistency

**Read model / Projection**
- Can be denormalized
- Serves fast queries
- Can be eventual consistency
- Not used to decide original mutation unless designed as derived operational projection

## 8. Database Topology

### 8.1 Overview

Data architecture is divided into 3 layers:

A. Master / Central Data

Contains data groups:
- IAM
- Roles / permissions / scope definitions
- Org hierarchy
- Product master
- Recipe master
- Effective-dated pricing
- Tax policy
- Supplier master
- Employee master
- Numbering rules
- Policy version metadata

Characteristics:
- Updated less than operational
- Many services depend on reading
- Need stable source of truth
- Suitable for central primary + replica

DB placement:
- Master DB Primary
- Master DB Read Replicas for read-heavy path such as catalog lookup warm load, org tree reads, IAM read model when appropriate

B. Operational Data Shards

Contains data groups:
- Sale order
- Sale payment
- POS session
- Inventory ledger
- Stock balance
- Stock count
- Waste
- Adjustment
- Purchase order
- Goods receipt
- Attendance events

Sharding strategy

V1 uses:
- Shard by region
- Partition by outlet within shard

Reasons:
- Matches management and authorization model
- Easier to scale than 1 common cluster
- Less complex than 300 physical DBs
- Better locality support for outlet workload

DB placement:
- Operational Shard Primary by region
- Operational Shard Read Replica for local read-only query

C. Reporting / Projection Data

Contains data groups:
- Sales fact
- Payment fact
- Inventory movement fact
- Procurement fact
- Attendance/payroll fact
- Regional summary
- Company summary
- Finance posting projection
- Denormalized dashboard views

Characteristics:
- Read-heavy
- Aggregate-heavy
- Serve BI / export / dashboards
- Not mutation source

DB placement:
- Snowflake reporting database for projection writes from consumer/jobs
- Separate Snowflake warehouses for ingest and BI/export workloads

### 8.2 Why not "every service shares 1 cluster" as final architecture

A single cluster model with schema-per-service is only suitable as a very early technical startup step. For this problem, it has limitations:
- OLTP and report compete for resources
- Outlet locality is not properly reflected
- Scope-based routing is difficult to clearly define
- Sharding/replication later is difficult to clean
- Hot zone scaling is more difficult than a region-based sharding model

Therefore, this document chooses logical tiered topology from the start to ensure a clear evolution path.

## 9. Read / Write Routing

### 9.1 Write path

**Master writes**

The following mutations write to Master DB Primary:
- User / role / permission
- Org tree
- Catalog / recipe / pricing / tax
- Supplier / employee master
- Policy changes

**Operational writes**

The following mutations write to corresponding Operational Shard Primary:
- Create order
- Confirm payment
- Create inventory transaction
- Create PO / GR
- Record attendance
- Create waste / stock count / adjustment

**Reporting writes**

No business client writes directly to reporting.
Write only comes from:
- Kafka consumers
- Stream processors
- Projection jobs
- ETL jobs

### 9.2 Read path

**Outlet-local query**

Examples:
- Cashier viewing outlet session
- Outlet manager viewing outlet stock
- Staff viewing outlet orders

Route to:
- Operational shard replica
- Or shard primary if strong read-after-write needed

**Region-aggregate query**

Examples:
- Region manager viewing revenue from multiple outlets
- Total regional inventory
- Regional attendance

Route to:
- Reporting DB region-level projections

Do not recommend scanning multiple operational shards real-time for dashboards or frequently aggregated operations.

**Company-wide query**

Examples:
- Total system revenue
- Top outlets nationwide
- Chain-wide BI reports

Route to:
- Reporting DB company-level projections

### 9.3 Consistency-aware read routing

Services need to categorize queries by:
- Strong consistency read
- Eventual consistency read
- Transaction-local read
- Aggregate analytic read

Examples:
- Just created order then immediately reopened: read from primary
- Open daily revenue dashboard: read from reporting projection
- Check stock availability in POS: read from availability projection/replica, not real-time ledger scan

## 10. Event-driven Architecture

### 10.1 Kafka as async backbone

Kafka is the async boundary between:
- Synchronous transaction path
- Downstream projections
- Audit stream
- Notification stream
- Decoupled business propagation

### 10.2 Event publication model

Every important mutation must publish events through the model:
- Local transaction commits business data
- Outbox written in same transaction
- Outbox relay publishes to Kafka
- Consumers use inbox / processed_event for idempotent

### 10.3 Main topics

Recommended topics:
- order.paid
- order.cancelled
- goods_receipt.posted
- stock_count.approved
- waste.recorded
- attendance.approved
- payroll.calculated
- payroll.posted
- price.changed
- recipe.version_activated

**Partition key**

Prefer partitioning by:
- outlet_id for operational locality
- region_id for some aggregation flow if appropriate

### 10.4 Example critical path: POS order

**Sync path**
1. Client calls API Gateway
2. Gateway verifies JWT, checks blacklist, attaches correlation_id
3. POS Service validates session and scope
4. POS Service gets catalog snapshot from Redis/cache
5. POS Service checks stock availability read-only
6. POS Service writes:
   - Order
   - Payment
   - Sale snapshot
   - Outbox
7. Returns result to client

**Async path after payment**

order.paid published to Kafka

Consumers:
- Inventory Service: Deduct stock via ledger + update stock balance projection
- Finance Service: Create revenue/posting projection
- Audit Service: Write audit log
- Report Service: Update sales facts and aggregates
- Notification Service: Send alert/webhook if needed

### 10.5 DLQ strategy

Each consumer group has its own DLQ.

Examples:
- order.paid.inventory.dlq
- order.paid.finance.dlq

When consumer fails after retry policy:
- Message moves to DLQ
- Notification/Ops flow raises alert
- Dashboard monitors poison messages and lag

## 11. AuthN / AuthZ Architecture

### 11.1 Authentication

System uses:
- JWT stateless access token
- Redis blacklist for revoked token
- API Gateway verifies JWT signature
- Service does not need to call IAM for every regular request

Minimum JWT claims:
- sub
- jti
- roles
- scope_roots
- policy_version
- scope_version
- auth_time
- exp

Example:

{
  "sub": "user_123",
  "roles": ["region_manager"],
  "scope_roots": {
    "regions": ["region_03"],
    "outlets": []
  },
  "policy_version": 42,
  "scope_version": 18,
  "jti": "9d4f..."
}

### 11.2 Authorization

Authorization does not rely only on role name. It relies on:
- Principal
- Permission
- Scope
- Hierarchy expansion
- Action + resource
- Optional condition rules

Example permissions:
- sales.order.create
- sales.order.read
- inventory.stock.read
- inventory.adjustment.approve
- report.region_summary.read

### 11.3 Scope tree

Org Service maintains hierarchy:
- Company
- Region
- Outlet

Should use one of:
- Closure table
- Materialized path
- Descendant mapping projection

Goals:
- Quickly resolve region parent → outlet child
- Support cache scope expansion
- Do not put all descendants in JWT

### 11.4 Routing and authorization interaction

Gateway does:
- Verify token
- Parse scope roots
- Basic route metadata

Service does:
- Resolve effective scope using token + cached org hierarchy
- Determine resource/action
- Determine query class
- Select appropriate DB target:
  - Shard primary
  - Shard replica
  - Reporting DB

Do not place all business authorization at Gateway.

## 12. Caching Strategy

### 12.1 Redis usage

Redis is used for:
- JWT blacklist
- Catalog hot snapshot
- Org scope expansion cache
- Permission/policy cache if needed
- Hot price lookup
- Optional availability cache

Do not use Redis as source of truth

### 12.2 Catalog caching

POS lookup price/recipe/tax uses cache with short TTL or event-driven invalidation.

When price changes:
- Catalog publishes price.changed
- Related cache is invalidated or refreshed

### 12.3 Scope caching

Org hierarchy and scope expansion can cache to reduce IAM/Org read path load. Cache must be versioned by scope_version or invalidated when org changes.

## 13. Data Consistency and Integrity Rules

### 13.1 Effective-dated records

Entities such as:
- product_price
- recipe_version
- tax_rate

Must use:
- effective_from
- effective_to
- Uniqueness/exclusion rules against overlap

### 13.2 Sale snapshot

When paid, sale record must snapshot:
- price_at_sale
- tax_at_sale
- recipe_version_id or equivalent reference
- Promotion effect if any

Do not recalculate from current catalog.

### 13.3 Inventory ledger

Inventory uses append-only ledger:
- Do not update history movement
- Errors corrected by reversal/adjustment entries
- Stock balance uses projection, do not SUM ledger real-time for every POS request

### 13.4 Finance immutability

States like paid payroll are immutable by business rule.
Correction must go through:
- Reversal
- Adjustment
- Compensating workflow

## 14. Observability and Operations

### 14.1 Correlation ID

correlation_id generated at API Gateway and propagated through:
- HTTP headers
- Kafka headers
- Async jobs
- Audit events
- Logs/traces

### 14.2 Logging

Every service must log structured minimum with:
- Timestamp
- Service_name
- Correlation_id
- Request_id
- User_id if any
- Outlet_id / region_id when appropriate
- Event_id if async handler

### 14.3 Metrics

Monitor minimum:
- Request latency by endpoint
- Kafka consumer lag
- DLQ volume
- Outbox relay lag
- Projection freshness
- Cache hit rate
- DB replica lag
- Stock projection delay
- Failed auth / blacklist hits

### 14.4 Tracing

OpenTelemetry or equivalent should be used to trace:
- Gateway → POS → Kafka → Inventory / Finance / Audit

## 15. Security Considerations

### 15.1 Token revocation

Logout and lock account use Redis blacklist with TTL equal to JWT expiry.

### 15.2 Least privilege

Permission must be fine-grained enough to not depend on general role name.

### 15.3 Service-to-service security

Internal services should use:
- mTLS or service identity
- Signed internal tokens if needed

### 15.4 Sensitive data handling

Do not put unnecessary sensitive data in Kafka event payload or logs. Audit trail must have appropriate masking.

## 16. Deployment Topology

### 16.1 V1 deployment recommendation

Runtime:
- Containerized services
- Kubernetes or equivalent container orchestration platform
- API Gateway in front
- Kafka cluster
- Redis cluster
- PostgreSQL clusters in 2 OLTP layers:
  - Master
  - Operational
- Snowflake reporting warehouse

Operational strategy:
- V1 does not need 300 physical DBs
- Operational layer logical sharding by region
- Hot region can be split shard earlier

### 16.2 V2 evolution

When load increases:
- Split operational shard logic into physical shard by region
- Add read replicas for hot shards
- Add specialized region-level projections
- Scale report/BI independently
- Refine routing policy by consistency class

## 17. Decision Log

### 17.1 Accepted
- Microservices according to bounded context
- Kafka event-driven backbone
- JWT stateless + Redis blacklist
- Inventory append-only ledger
- Outbox/inbox
- Projection-based reporting
- 3-layer data topology
- Shard by region first, partition by outlet

### 17.2 Rejected
- Report reads live directly from OLTP as default solution
- 300 outlets = 300 physical DBs from V1
- Pack every service into 1 PostgreSQL cluster as final architecture
- Put all scope descendants in JWT

## 18. Trade-offs

### 18.1 Advantages
- Correctly reflects outlet locality
- Separates transaction path from reporting path
- Scales appropriately by region
- Suitable for scope-based routing
- Still practical for V1
- Has clear evolution path for V2

### 18.2 Disadvantages
- More complex than 1 cluster model
- Needs good projection discipline
- Need monitoring event lag and replica lag
- Need clear data governance between source-of-truth and projections

## 19. Implementation Roadmap

### 19.1 V1

Priorities:
1. API Gateway
2. IAM + Org
3. Catalog
4. POS
5. Inventory
6. Procurement
7. HR
8. Finance projection
9. Reporting
10. Audit / Notification

V1 implements:
- 3 DB layers
- Kafka + outbox/inbox
- Stock balance projection
- Region/outlet scope-aware routing at basic level
- Dashboard reads reporting DB

V1 does not do too early:
- Per-outlet physical DB
- Distributed cross-shard query engine
- Overly dynamic policy engine
- Active-active multi-region

### 19.2 V2
- Physical shard by region
- Add strategic replicas
- Specialized region summary projections
- More sophisticated policy-driven routing
- Separate runtime/infrastructure for hot services

## 20. Deployment and Risk Mitigation

### 20.1 Projection lag

Risk: Dashboard or regional summary is delayed
Mitigation: Monitor consumer lag, projection freshness, retry/DLQ

### 20.2 Replica stale read

Risk: Just written but replica not seen yet
Mitigation: Consistency-aware routing, read primary for read-after-write

### 20.3 Scope resolution complexity

Risk: Authorization/routing logic is complex
Mitigation: Scope roots in JWT + org tree cache + clear versioning

### 20.4 Shared event contract drift

Risk: Consumer understands differently from producer
Mitigation: Schema registry / versioned contracts / compatibility policy

## 21. Conclusion

The recommended architecture for this F&B ERP system is:
- Microservices according to bounded context
- Kafka as async backbone
- JWT stateless + Redis blacklist
- Inventory append-only ledger
- Reporting/projection separate from OLTP
- 3-layer data: Master / Operational Shards / Reporting
- Operational shard by region, partition by outlet
- Scope-based authorization attached to query routing

This is a balanced approach between:
- Business correctness
- POS performance
- Scalability
- Operability
- Acceptable complexity for V1
