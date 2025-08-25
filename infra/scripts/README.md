# AWS Resource Lifecycle Management Scripts

This directory contains scripts for managing the complete lifecycle of AWS resources for the Chess application.

## Available Scripts

### 1. lifecycle-management.sh (Linux/macOS)
Bash script for comprehensive resource lifecycle management.

```bash
# Usage
./lifecycle-management.sh <environment> <action>

# Examples
./lifecycle-management.sh dev start      # Start all resources
./lifecycle-management.sh prod status    # Check resource status
./lifecycle-management.sh dev halt       # Temporarily halt resources
./lifecycle-management.sh prod restart   # Restart halted resources
./lifecycle-management.sh dev stop       # Stop compute resources
./lifecycle-management.sh prod remove    # Remove all resources
./lifecycle-management.sh dev health     # Health check
```

### 2. resource-monitor.ps1 (Windows)
PowerShell script with the same functionality for Windows environments.

```powershell
# Usage
.\resource-monitor.ps1 -Environment <env> -Action <action>

# Examples
.\resource-monitor.ps1 -Environment dev -Action start
.\resource-monitor.ps1 -Environment prod -Action status
.\resource-monitor.ps1 -Environment dev -Action halt
```

## Lifecycle Actions Explained

### 🚀 START
- **Purpose**: Create and start all AWS resources
- **What it does**:
  - Runs `terraform apply` to create infrastructure
  - Updates kubeconfig for EKS access
  - Scales node groups to desired capacity
  - Deploys applications via Helm
- **Use case**: Initial deployment or full environment startup

### 📊 STATUS
- **Purpose**: Check current status of all resources
- **What it does**:
  - Shows Terraform resource states
  - Displays EKS cluster status
  - Lists node groups and their status
  - Shows Kubernetes pods and Helm releases
  - Checks CloudFront distribution status
- **Use case**: Regular monitoring and troubleshooting

### ⏸️ HALT
- **Purpose**: Temporarily halt resources to save costs
- **What it does**:
  - Scales node groups to 0 instances
  - Scales application deployments to 0 replicas
  - Keeps infrastructure intact
- **Use case**: Overnight/weekend cost savings
- **Cost savings**: ~80% reduction (only pay for EKS control plane)

### 🔄 RESTART
- **Purpose**: Restart previously halted resources
- **What it does**:
  - Scales node groups back to desired capacity
  - Waits for nodes to be ready
  - Scales applications back to desired replicas
  - Waits for applications to be healthy
- **Use case**: Resume operations after halt

### 🛑 STOP
- **Purpose**: Stop compute resources but keep infrastructure
- **What it does**:
  - Terminates all EC2 instances in node groups
  - Keeps EKS cluster, VPCs, and other infrastructure
  - Applications become unavailable
- **Use case**: Longer-term shutdown while preserving configuration

### 🗑️ REMOVE/DELETE
- **Purpose**: Completely remove all AWS resources
- **What it does**:
  - Runs `terraform destroy` to delete everything
  - Requires confirmation to prevent accidental deletion
  - Cannot be undone without full redeployment
- **Use case**: Environment cleanup, cost elimination

### 🏥 HEALTH
- **Purpose**: Perform comprehensive health checks
- **What it does**:
  - Checks EKS cluster health
  - Verifies application pod readiness
  - Tests application endpoints
  - Reports overall system health
- **Use case**: Automated monitoring, troubleshooting

## Resource Lifecycle States

```
┌─────────────┐    start     ┌─────────────┐
│   STOPPED   │ ──────────► │   RUNNING   │
│             │             │             │
└─────────────┘             └─────────────┘
       ▲                           │
       │                           │ halt
       │ remove                    ▼
       │                    ┌─────────────┐
       │                    │   HALTED    │
       │                    │             │
       │                    └─────────────┘
       │                           │
       │                           │ restart
       │                           ▼
       │                    ┌─────────────┐
       └────────────────────│   RUNNING   │
                            │             │
                            └─────────────┘
```

## Cost Implications

| State | EKS Control Plane | EC2 Instances | Load Balancers | Storage | Total Cost |
|-------|------------------|---------------|----------------|---------|------------|
| RUNNING | ✅ $73/month | ✅ $505/month | ✅ $50/month | ✅ $20/month | ~$648/month |
| HALTED | ✅ $73/month | ❌ $0/month | ✅ $50/month | ✅ $20/month | ~$143/month |
| STOPPED | ✅ $73/month | ❌ $0/month | ✅ $50/month | ✅ $20/month | ~$143/month |
| REMOVED | ❌ $0/month | ❌ $0/month | ❌ $0/month | ❌ $0/month | $0/month |

## Prerequisites

- AWS CLI configured with appropriate permissions
- Terraform >= 1.5.0
- kubectl >= 1.28.0
- Helm >= 3.12.0
- jq (for JSON parsing in bash script)

## Security Considerations

- Scripts require AWS permissions for EKS, EC2, CloudFront, S3
- Confirmation required for destructive operations (remove)
- All operations are logged with timestamps
- Scripts validate prerequisites before execution

## Automation Integration

These scripts can be integrated with:
- **CI/CD Pipelines**: Automated deployment and testing
- **Cron Jobs**: Scheduled halt/restart for cost optimization
- **Monitoring Systems**: Health checks and alerting
- **ChatOps**: Slack/Teams integration for operations

## Example Automation Scenarios

### Cost Optimization Schedule
```bash
# Halt resources at 6 PM weekdays
0 18 * * 1-5 /path/to/lifecycle-management.sh prod halt

# Restart resources at 8 AM weekdays  
0 8 * * 1-5 /path/to/lifecycle-management.sh prod restart
```

### Health Monitoring
```bash
# Check health every 5 minutes
*/5 * * * * /path/to/lifecycle-management.sh prod health
```