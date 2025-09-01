# AWS Infrastructure Scripts Execution Lifecycle Sequence Diagram

## Overview
This sequence diagram illustrates the complete execution lifecycle of AWS infrastructure scripts, showing the phased deployment approach, resource management operations, and lifecycle management flows. The diagram covers the incremental deployment strategy, resource scaling, monitoring, and cleanup operations.

## Complete Infrastructure Deployment Lifecycle

```mermaid
sequenceDiagram
    participant User as DevOps_Engineer
    participant ResourceManager as Resource_Manager_Script
    participant LifecycleManager as Lifecycle_Manager_Script
    participant Terraform as Terraform_Engine
    participant AWS as AWS_Cloud_Platform
    participant EKS as EKS_Cluster
    participant Kubernetes as Kubernetes_System
    participant Helm as Helm_Package_Manager
    participant Monitoring as Monitoring_Stack
    participant Validation as Validation_System

    Note over User,Validation: Phase 1: Foundation Deployment (VPC & Networking)
    User->>ResourceManager: ./resource-manager.sh dev install vpc
    ResourceManager->>ResourceManager: Load dev.conf configuration
    ResourceManager->>ResourceManager: Validate environment and permissions
    ResourceManager->>Terraform: terraform init
    Terraform->>AWS: Create Internet VPC
    AWS-->>Terraform: VPC Created
    Terraform->>AWS: Create Private VPC
    AWS-->>Terraform: Private VPC Created
    Terraform->>AWS: Create Transit Gateway
    AWS-->>Terraform: Transit Gateway Created
    Terraform->>AWS: Configure VPC Peering
    AWS-->>Terraform: Peering Established
    ResourceManager->>Validation: Verify VPC connectivity
    Validation-->>ResourceManager: VPC Status: HEALTHY
    ResourceManager-->>User: VPC deployment completed

    Note over User,Validation: Phase 2: Storage & Registry Deployment
    User->>ResourceManager: ./resource-manager.sh dev install storage
    ResourceManager->>Terraform: terraform apply -target=module.s3
    Terraform->>AWS: Create S3 Bucket
    AWS-->>Terraform: S3 Bucket Created
    Terraform->>AWS: Create ECR Repository
    AWS-->>Terraform: ECR Repository Created
    Terraform->>AWS: Configure S3 VPC Endpoint
    AWS-->>Terraform: VPC Endpoint Active
    ResourceManager->>Validation: Test storage access
    Validation-->>ResourceManager: Storage Status: HEALTHY
    ResourceManager-->>User: Storage deployment completed

    Note over User,Validation: Phase 3: Security Infrastructure
    User->>ResourceManager: ./resource-manager.sh dev install security
    ResourceManager->>Terraform: terraform apply -target=module.secrets_manager
    Terraform->>AWS: Create Secrets Manager
    AWS-->>Terraform: Secrets Manager Created
    Terraform->>AWS: Create WAF Rules
    AWS-->>Terraform: WAF Rules Active
    Terraform->>AWS: Configure IAM Roles
    AWS-->>Terraform: IAM Roles Created
    ResourceManager->>Validation: Test security components
    Validation-->>ResourceManager: Security Status: HEALTHY
    ResourceManager-->>User: Security deployment completed

    Note over User,Validation: Phase 4: Compute Infrastructure (EKS)
    User->>ResourceManager: ./resource-manager.sh dev install compute
    ResourceManager->>Terraform: terraform apply -target=module.eks
    Terraform->>AWS: Create EKS Cluster
    AWS-->>Terraform: EKS Cluster Created
    Terraform->>AWS: Create Node Groups
    AWS-->>Terraform: Node Groups Active
    Terraform->>AWS: Configure EKS Addons
    AWS-->>Terraform: Addons Installed
    ResourceManager->>EKS: Update kubeconfig
    EKS-->>ResourceManager: kubeconfig Updated
    ResourceManager->>Kubernetes: Verify cluster access
    Kubernetes-->>ResourceManager: Cluster Access: OK
    ResourceManager->>Validation: Test compute resources
    Validation-->>ResourceManager: Compute Status: HEALTHY
    ResourceManager-->>User: Compute deployment completed

    Note over User,Validation: Phase 5: Network Services
    User->>ResourceManager: ./resource-manager.sh dev install network
    ResourceManager->>Terraform: terraform apply -target=module.api_gateway
    Terraform->>AWS: Create API Gateway
    AWS-->>Terraform: API Gateway Created
    Terraform->>AWS: Create CloudFront Distribution
    AWS-->>Terraform: CloudFront Active
    Terraform->>AWS: Configure SSL Certificates
    AWS-->>Terraform: SSL Certificates Valid
    ResourceManager->>Validation: Test network services
    Validation-->>ResourceManager: Network Status: HEALTHY
    ResourceManager-->>User: Network deployment completed

    Note over User,Validation: Phase 6: Application Deployment
    User->>ResourceManager: ./resource-manager.sh dev install apps
    ResourceManager->>Terraform: terraform apply -target=module.helm_istio
    Terraform->>Helm: Install Istio Service Mesh
    Helm->>Kubernetes: Deploy Istio Components
    Kubernetes-->>Helm: Istio Deployed
    ResourceManager->>Terraform: terraform apply -target=module.helm_monitoring
    Terraform->>Helm: Install Monitoring Stack
    Helm->>Kubernetes: Deploy Prometheus/Grafana
    Kubernetes-->>Helm: Monitoring Deployed
    ResourceManager->>Terraform: terraform apply -target=module.helm_chess_app
    Terraform->>Helm: Install Chess Application
    Helm->>Kubernetes: Deploy Chess App
    Kubernetes-->>Helm: Chess App Deployed
    ResourceManager->>Validation: Test applications
    Validation-->>ResourceManager: Applications Status: HEALTHY
    ResourceManager-->>User: All deployments completed
```

## Resource Lifecycle Management Operations

```mermaid
sequenceDiagram
    participant User as DevOps_Engineer
    participant LifecycleManager as Lifecycle_Manager_Script
    participant ResourceManager as Resource_Manager_Script
    participant EKS as EKS_Cluster
    participant Kubernetes as Kubernetes_System
    participant AWS as AWS_Cloud_Platform
    participant Monitoring as Monitoring_Stack

    Note over User,Monitoring: Resource Scaling Operations
    alt Scale Down (Halt Resources)
        User->>LifecycleManager: ./lifecycle-management.sh dev halt
        LifecycleManager->>ResourceManager: Scale down applications
        ResourceManager->>Kubernetes: Scale deployments to 0 replicas
        Kubernetes-->>ResourceManager: Applications Scaled Down
        LifecycleManager->>ResourceManager: Scale down compute
        ResourceManager->>EKS: Scale node groups to 0
        EKS->>AWS: Terminate EC2 instances
        AWS-->>EKS: Instances Terminated
        LifecycleManager-->>User: Resources halted (80% cost savings)
    else Scale Up (Start Resources)
        User->>LifecycleManager: ./lifecycle-management.sh dev start
        LifecycleManager->>ResourceManager: Scale up compute
        ResourceManager->>EKS: Scale node groups to desired capacity
        EKS->>AWS: Launch EC2 instances
        AWS-->>EKS: Instances Running
        LifecycleManager->>ResourceManager: Wait for cluster readiness
        ResourceManager->>EKS: Check cluster status
        EKS-->>ResourceManager: Cluster Ready
        LifecycleManager->>ResourceManager: Scale up applications
        ResourceManager->>Kubernetes: Scale deployments to desired replicas
        Kubernetes-->>ResourceManager: Applications Scaled Up
        LifecycleManager-->>User: Resources started successfully
    end

    Note over User,Monitoring: Health Monitoring
    User->>LifecycleManager: ./lifecycle-management.sh dev health
    LifecycleManager->>ResourceManager: Check compute health
    ResourceManager->>EKS: Verify cluster status
    EKS-->>ResourceManager: Cluster Status: ACTIVE
    LifecycleManager->>ResourceManager: Check application health
    ResourceManager->>Kubernetes: Verify pod readiness
    Kubernetes-->>ResourceManager: Pods Status: READY
    LifecycleManager->>Monitoring: Check monitoring stack
    Monitoring-->>LifecycleManager: Monitoring Status: HEALTHY
    LifecycleManager-->>User: Health check completed
```

## Resource Management Operations

```mermaid
sequenceDiagram
    participant User as DevOps_Engineer
    participant ResourceManager as Resource_Manager_Script
    participant Terraform as Terraform_Engine
    participant AWS as AWS_Cloud_Platform
    participant Validation as Validation_System
    participant Rollback as Rollback_System

    Note over User,Rollback: Resource Installation with Rollback
    User->>ResourceManager: ./resource-manager.sh dev install <resource>
    ResourceManager->>ResourceManager: Parse command line arguments
    ResourceManager->>ResourceManager: Load environment configuration
    ResourceManager->>ResourceManager: Check prerequisites
    ResourceManager->>ResourceManager: Validate AWS permissions
    
    alt Dry Run Mode
        ResourceManager->>Terraform: terraform plan (dry run)
        Terraform-->>ResourceManager: Plan Summary
        ResourceManager-->>User: Dry run completed
    else Actual Installation
        ResourceManager->>Rollback: Register rollback operation
        Rollback-->>ResourceManager: Rollback registered
        ResourceManager->>Terraform: terraform apply
        Terraform->>AWS: Create/Update resources
        
        alt Installation Successful
            AWS-->>Terraform: Resources Created
            Terraform-->>ResourceManager: Apply Successful
            ResourceManager->>Validation: Post-install validation
            Validation-->>ResourceManager: Validation Passed
            ResourceManager->>Rollback: Clear rollback operations
            ResourceManager-->>User: Installation completed successfully
        else Installation Failed
            AWS-->>Terraform: Resource Creation Failed
            Terraform-->>ResourceManager: Apply Failed
            ResourceManager->>Rollback: Execute rollback operations
            Rollback->>AWS: Remove created resources
            AWS-->>Rollback: Resources Removed
            ResourceManager-->>User: Installation failed, rollback completed
        end
    end
```

## Status and Monitoring Operations

```mermaid
sequenceDiagram
    participant User as DevOps_Engineer
    participant ResourceManager as Resource_Manager_Script
    participant AWS as AWS_Cloud_Platform
    participant EKS as EKS_Cluster
    participant Kubernetes as Kubernetes_System
    participant Terraform as Terraform_Engine

    Note over User,Terraform: Comprehensive Status Checking
    User->>ResourceManager: ./resource-manager.sh dev status all
    ResourceManager->>ResourceManager: Load environment configuration
    
    par VPC Status Check
        ResourceManager->>AWS: Check VPC status
        AWS-->>ResourceManager: VPC Status: ACTIVE
    and Storage Status Check
        ResourceManager->>AWS: Check S3/ECR status
        AWS-->>ResourceManager: Storage Status: AVAILABLE
    and Security Status Check
        ResourceManager->>AWS: Check Secrets Manager/WAF
        AWS-->>ResourceManager: Security Status: ACTIVE
    and Compute Status Check
        ResourceManager->>EKS: Check cluster status
        EKS-->>ResourceManager: Cluster Status: ACTIVE
        ResourceManager->>EKS: Check node groups
        EKS-->>ResourceManager: Node Groups: ACTIVE
    and Network Status Check
        ResourceManager->>AWS: Check API Gateway/CloudFront
        AWS-->>ResourceManager: Network Status: DEPLOYED
    and Application Status Check
        ResourceManager->>Kubernetes: Check pod status
        Kubernetes-->>ResourceManager: Pods: RUNNING
        ResourceManager->>Kubernetes: Check Helm releases
        Kubernetes-->>ResourceManager: Helm Releases: DEPLOYED
    end
    
    ResourceManager->>ResourceManager: Aggregate status information
    ResourceManager-->>User: Complete status report
```

## Resource Removal and Cleanup

```mermaid
sequenceDiagram
    participant User as DevOps_Engineer
    participant ResourceManager as Resource_Manager_Script
    participant Terraform as Terraform_Engine
    participant AWS as AWS_Cloud_Platform
    participant Confirmation as Confirmation_System

    Note over User,Confirmation: Resource Removal Process
    User->>ResourceManager: ./resource-manager.sh dev remove <resource>
    ResourceManager->>ResourceManager: Load environment configuration
    ResourceManager->>ResourceManager: Check resource dependencies
    
    alt Resource Dependencies Exist
        ResourceManager->>ResourceManager: Check dependency order
        ResourceManager->>User: Warning: Dependencies detected
        User->>ResourceManager: Confirm removal order
    end
    
    ResourceManager->>Confirmation: Request user confirmation
    Confirmation->>User: "This will DELETE resources. Are you sure? (y/N)"
    
    alt User Confirms
        User->>Confirmation: "y" (confirm)
        Confirmation-->>ResourceManager: Confirmation received
        ResourceManager->>Terraform: terraform destroy
        Terraform->>AWS: Remove resources
        
        loop Resource Removal
            AWS->>AWS: Terminate instances
            AWS->>AWS: Delete volumes
            AWS->>AWS: Remove security groups
            AWS->>AWS: Delete VPCs
        end
        
        AWS-->>Terraform: Resources Removed
        Terraform-->>ResourceManager: Destroy completed
        ResourceManager-->>User: Resources removed successfully
    else User Cancels
        User->>Confirmation: "N" (cancel)
        Confirmation-->>ResourceManager: Operation cancelled
        ResourceManager-->>User: Operation cancelled by user
    end
```

## Error Handling and Recovery

```mermaid
sequenceDiagram
    participant User as DevOps_Engineer
    participant ResourceManager as Resource_Manager_Script
    participant ErrorHandler as Error_Handler_System
    participant Rollback as Rollback_System
    participant Logging as Logging_System
    participant Notification as Notification_System

    Note over User,Notification: Error Handling Flow
    alt Error During Operation
        ResourceManager->>ErrorHandler: Handle error
        ErrorHandler->>ErrorHandler: Capture error details
        ErrorHandler->>Logging: Log error with stack trace
        Logging-->>ErrorHandler: Error logged
        
        alt Rollback Available
            ErrorHandler->>Rollback: Execute rollback operations
            Rollback->>Rollback: Perform rollback steps
            Rollback-->>ErrorHandler: Rollback completed
            ErrorHandler->>Notification: Send rollback notification
            Notification-->>User: Rollback notification
        else No Rollback Available
            ErrorHandler->>Notification: Send error notification
            Notification-->>User: Error notification
        end
        
        ErrorHandler-->>ResourceManager: Error handled
        ResourceManager-->>User: Operation failed with error details
    end
```

## Infrastructure Scripts Architecture Summary

### **Script Components:**
- **Resource Manager Scripts**: `resource-manager.sh` and `resource-manager.ps1`
- **Lifecycle Manager**: `lifecycle-management.sh`
- **Resource Monitor**: `resource-monitor.ps1`
- **Test Scenarios**: `test-scenarios.sh`
- **Shared Libraries**: `lib/common-functions.sh` and `lib/aws-utils.sh`

### **Deployment Phases:**
1. **Foundation (VPC & Networking)**: Internet VPC, Private VPC, Transit Gateway
2. **Storage & Registry**: S3 buckets, ECR repositories, VPC endpoints
3. **Security**: Secrets Manager, WAF, IAM roles
4. **Compute (EKS)**: EKS cluster, node groups, addons
5. **Network Services**: API Gateway, CloudFront, SSL certificates
6. **Applications**: Istio, monitoring stack, chess application

### **Key Operations:**
- **Install**: Phased resource creation with Terraform
- **Start/Halt**: Resource scaling for cost optimization
- **Status**: Comprehensive health checking
- **Health**: Detailed resource validation
- **Remove**: Clean resource cleanup with confirmation

### **Error Handling Features:**
- **Automatic Rollback**: On installation failures
- **Comprehensive Logging**: With stack traces and context
- **Progress Tracking**: For long-running operations
- **Timeout Protection**: For hanging operations
- **User Confirmation**: For destructive operations

### **Monitoring & Validation:**
- **Real-time Status**: AWS resource states
- **Health Checks**: Resource availability and performance
- **Metrics Collection**: Performance and usage data
- **Alert Notifications**: For critical issues

## File Storage Location

This sequence diagram is stored at:
```
CHESS/docs/AWS_INFRA_Sequence_Diagram.md
```

## Related Documentation

For more detailed information about AWS infrastructure:
- **Deployment Guide**: `infra/scripts/deployment-order.md`
- **Resource Management**: `infra/scripts/resource-manager.sh`
- **Lifecycle Management**: `infra/scripts/lifecycle-management.sh`
- **Configuration**: `infra/scripts/config/` directory
- **Shared Libraries**: `infra/scripts/lib/` directory
- **Terraform Configuration**: `infra/terraform/` directory
