# F&B ERP System - Project Structure

## Overview

This document describes the recommended project structure for the F&B ERP System based on the microservices architecture.

## Directory Structure

```
.
├── README.md
├── docs/
│   └── architecture/
│       └── SAD.md
├── services/
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
├── infrastructure/
│   ├── kafka/
│   ├── postgres/
│   │   ├── master/
│   │   └── operational/
│   ├── snowflake/
│   │   └── reporting/
│   └── redis/
├── deployment/
│   └── kubernetes/
└── shared-libraries/
    └── common-config/
```

## Service Details

Each service directory contains:
- src/ - Source code
- config/ - Configuration files
- Dockerfile - Container configuration
- README.md - Service documentation

## Implementation Approach

1. **Phase 1 (V1)**: Core services implementation
2. **Phase 2 (V2)**: Scaling and optimization
