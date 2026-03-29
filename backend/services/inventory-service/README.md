# Inventory Service Template

This document provides a template for implementing the Inventory service for the F&B ERP System.

## Service Responsibilities

1. Inventory ledger management
2. Stock balance projection
3. Stock count, waste, and adjustment processing
4. Availability read model management

## Implementation Components

### Core Components to Implement

1. **Inventory Ledger**
   - Append-only inventory transaction logging
   - Stock movement tracking
   - Transaction validation

2. **Stock Balance Management**
   - Real-time stock balance projection
   - Stock level calculations
   - Availability tracking

3. **Inventory Operations**
   - Stock count processing
   - Waste recording
   - Stock adjustments
   - Reconciliation workflows

## Database Design

### Tables

1. **Inventory Transactions Table**
   - transaction_id (Primary Key)
   - outlet_id (Foreign Key)
   - product_id (Foreign Key)
   - transaction_type
   - quantity
   - reference_type
   - reference_id
   - transaction_date
   - created_by

2. **Stock Balance Table**
   - balance_id (Primary Key)
   - outlet_id (Foreign Key)
   - product_id (Foreign Key)
   - quantity_on_hand
   - quantity_available
   - last_updated

3. **Stock Count Table**
   - count_id (Primary Key)
   - outlet_id (Foreign Key)
   - count_date
   - status
   - created_by
   - approved_by

4. **Stock Count Lines Table**
   - count_line_id (Primary Key)
   - count_id (Foreign Key)
   - product_id (Foreign Key)
   - counted_quantity
   - system_quantity
   - variance

5. **Waste Records Table**
   - waste_id (Primary Key)
   - outlet_id (Foreign Key)
   - product_id (Foreign Key)
   - quantity
   - waste_reason
   - waste_date
   - recorded_by

6. **Stock Adjustments Table**
   - adjustment_id (Primary Key)
   - outlet_id (Foreign Key)
   - product_id (Foreign Key)
   - adjustment_quantity
   - adjustment_reason
   - adjustment_date
   - approved_by

## Kafka Events

### Outbound Events
- inventory.transaction.created
- stock.balance.updated
- stock.count.approved
- waste.recorded
- stock.adjusted

### Inbound Events
- order.paid (deduct stock)
- goods_receipt.posted (add stock)
- price.changed (update valuation)

## Implementation Requirements

### V1 Implementation
- Basic inventory ledger
- Stock balance projection
- Stock count workflows
- Waste recording
- Stock adjustments

### V2 Features
- Lot/batch tracking
- Advanced availability calculations
- Multi-outlet inventory transfers
- Predictive stock optimization