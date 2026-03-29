# Finance Service Template

This document provides a template for implementing the Finance service for the F&B ERP System.

## Service Responsibilities

1. Payroll processing
2. Expense management
3. Accounting projections
4. Financial reconciliation

## Implementation Components

### Core Components to Implement

1. **Payroll Management**
   - Payroll period definition
   - Payroll run processing
   - Payroll posting

2. **Expense Management**
   - Expense record creation
   - Expense approval workflows
   - Expense reimbursement

3. **Accounting Projections**
   - Revenue posting
   - Cost posting
   - Financial fact immutability

4. **Reconciliation**
   - Bank reconciliation
   - Account reconciliation
   - Variance analysis

## Database Design

### Tables

1. **Payroll Periods Table**
   - period_id (Primary Key)
   - period_name
   - start_date
   - end_date
   - status
   - created_at

2. **Payroll Runs Table**
   - run_id (Primary Key)
   - period_id (Foreign Key)
   - run_date
   - status
   - total_amount
   - processed_by

3. **Payroll Details Table**
   - payroll_detail_id (Primary Key)
   - run_id (Foreign Key)
   - employee_id (Foreign Key)
   - gross_pay
   - deductions
   - net_pay
   - payment_status

4. **Expense Records Table**
   - expense_id (Primary Key)
   - employee_id (Foreign Key)
   - outlet_id (Foreign Key)
   - expense_date
   - expense_type
   - amount
   - status
   - submitted_by
   - approved_by

5. **Accounting Postings Table**
   - posting_id (Primary Key)
   - posting_date
   - account_code
   - debit_amount
   - credit_amount
   - reference_type
   - reference_id
   - is_posted

## Kafka Events

### Outbound Events
- payroll.calculated
- payroll.posted
- expense.submitted
- expense.approved

### Inbound Events
- order.paid (revenue posting)
- goods_receipt.posted (cost posting)
- attendance.approved (payroll input)

## Implementation Requirements

### V1 Implementation
- Basic payroll processing
- Expense management
- Simple accounting projections
- Basic reconciliation

### V2 Features
- Advanced payroll calculations
- Multi-currency support
- Financial reporting integration
- Automated reconciliation