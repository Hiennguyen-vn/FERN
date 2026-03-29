# HR Service Template

This document provides a template for implementing the HR service for the F&B ERP System.

## Service Responsibilities

1. Employee assignment management
2. Shift scheduling
3. Attendance tracking
4. Attendance review and approval

## Implementation Components

### Core Components to Implement

1. **Employee Management**
   - Employee profile management
   - Employee assignment to outlets
   - Role and position management

2. **Shift Scheduling**
   - Shift definition and creation
   - Employee shift assignment
   - Schedule publishing

3. **Attendance Management**
   - Attendance event recording
   - Clock in/clock out processing
   - Overtime calculation

4. **Approval Workflows**
   - Attendance review
   - Schedule approval
   - Exception handling

## Database Design

### Tables

1. **Employees Table**
   - employee_id (Primary Key)
   - employee_number
   - first_name
   - last_name
   - email
   - phone
   - hire_date
   - status

2. **Employee Assignments Table**
   - assignment_id (Primary Key)
   - employee_id (Foreign Key)
   - outlet_id (Foreign Key)
   - position
   - start_date
   - end_date
   - is_primary

3. **Shift Schedules Table**
   - schedule_id (Primary Key)
   - outlet_id (Foreign Key)
   - shift_date
   - start_time
   - end_time
   - shift_name
   - status

4. **Schedule Assignments Table**
   - assignment_id (Primary Key)
   - schedule_id (Foreign Key)
   - employee_id (Foreign Key)
   - assigned_role

5. **Attendance Events Table**
   - attendance_id (Primary Key)
   - employee_id (Foreign Key)
   - outlet_id (Foreign Key)
   - shift_date
   - clock_in_time
   - clock_out_time
   - actual_hours
   - status
   - recorded_by

6. **Attendance Approvals Table**
   - approval_id (Primary Key)
   - attendance_id (Foreign Key)
   - approved_by
   - approval_date
   - status
   - comments

## Kafka Events

### Outbound Events
- attendance.recorded
- attendance.approved
- attendance.rejected
- shift.schedule_published

### Inbound Events
- employee.created (from master data)
- outlet.created (from org service)

## Implementation Requirements

### V1 Implementation
- Basic employee management
- Shift scheduling
- Attendance recording
- Approval workflows

### V2 Features
- Advanced scheduling optimization
- Time and attendance analytics
- Integration with payroll
- Mobile clock in/clock out