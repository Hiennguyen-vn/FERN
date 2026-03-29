# Report Service Template

This document provides a template for implementing the Report service for the F&B ERP System.

## Service Responsibilities

1. Business intelligence read models
2. Data export functionality
3. Regional and company aggregates
4. Dashboard query support

## Implementation Components

### Core Components to Implement

1. **Data Aggregation**
   - Sales fact aggregation
   - Inventory movement aggregation
   - Financial fact aggregation

2. **Report Generation**
   - Standard report templates
   - Custom report creation
   - Scheduled report generation

3. **Dashboard Support**
   - Real-time dashboard data
   - Historical trend analysis
   - KPI calculations

4. **Export Functionality**
   - CSV/Excel export
   - PDF report generation
   - API data export

## Database Design

### Tables

1. **Sales Facts Table**
   - fact_id (Primary Key)
   - outlet_id
   - product_id
   - sale_date
   - quantity
   - amount
   - tax_amount
   - discount_amount

2. **Inventory Movement Facts Table**
   - fact_id (Primary Key)
   - outlet_id
   - product_id
   - movement_date
   - movement_type
   - quantity
   - reference_type
   - reference_id

3. **Regional Summaries Table**
   - summary_id (Primary Key)
   - region_id
   - summary_date
   - total_sales
   - total_cost
   - total_profit
   - transaction_count

4. **Company Summaries Table**
   - summary_id (Primary Key)
   - summary_date
   - total_sales
   - total_cost
   - total_profit
   - outlet_count

5. **Export Jobs Table**
   - job_id (Primary Key)
   - report_type
   - format
   - status
   - file_path
   - created_by
   - completed_at

## Kafka Events

### Inbound Events
- order.paid (sales aggregation)
- order.cancelled (sales adjustment)
- goods_receipt.posted (inventory aggregation)
- waste.recorded (inventory adjustment)
- payroll.posted (financial aggregation)

## Implementation Requirements

### V1 Implementation
- Basic sales reporting
- Inventory reporting
- Standard dashboard queries
- CSV/Excel export

### V2 Features
- Advanced analytics
- Real-time dashboards
- Custom report builder
- Automated report scheduling
- BI tool integration