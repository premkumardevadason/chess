# Incremental Deployment Guide

This guide provides step-by-step instructions for deploying AWS resources incrementally, allowing for testing at each stage.

## Deployment Order

### Phase 1: Foundation (VPC & Networking)
```bash
# Install VPC infrastructure first
./resource-manager.sh dev install vpc

# Verify VPC creation
./resource-manager.sh dev status vpc
```

**What gets created:**
- Internet-facing VPC with public subnets
- Private VPC with private subnets and NAT gateways
- Transit Gateway connecting both VPCs
- Security groups and route tables

**Testing:**
- Verify VPCs are created and peered
- Check subnet configurations
- Test internet connectivity through NAT gateways

### Phase 2: Storage & Registry
```bash
# Install storage components
./resource-manager.sh dev install storage

# Check storage status
./resource-manager.sh dev status storage
```

**What gets created:**
- S3 bucket for AI model storage (private access only)
- ECR repository for container images
- S3 VPC endpoint for private access

**Testing:**
- Upload/download test files to S3 bucket
- Push test container image to ECR
- Verify S3 VPC endpoint connectivity

### Phase 3: Security
```bash
# Install security components
./resource-manager.sh dev install security

# Verify security setup
./resource-manager.sh dev status security
```

**What gets created:**
- AWS Secrets Manager for API keys
- WAF with DDoS protection and rate limiting
- IAM roles for service accounts

**Testing:**
- Store and retrieve test secrets
- Verify WAF rules are active
- Test IAM role assumptions

### Phase 4: Compute (EKS)
```bash
# Install EKS cluster
./resource-manager.sh dev install compute

# Check cluster status
./resource-manager.sh dev status compute

# Verify kubectl access
kubectl get nodes
kubectl get pods -A
```

**What gets created:**
- EKS cluster with OIDC provider
- Node groups (general and GPU)
- EKS addons (Load Balancer Controller, Secrets Store CSI)
- Lifecycle hooks and monitoring

**Testing:**
- Deploy test pod to verify cluster functionality
- Test pod-to-pod networking
- Verify node scaling works
- Test S3 access from pods

### Phase 5: Network Services
```bash
# Install API Gateway and CloudFront
./resource-manager.sh dev install network

# Check network services
./resource-manager.sh dev status network
```

**What gets created:**
- API Gateway v2 with VPC Link
- CloudFront distribution with WAF
- Custom domain and SSL certificates
- VPC Link security groups

**Testing:**
- Test API Gateway connectivity to VPC
- Verify CloudFront caching behavior
- Test WAF protection rules
- Check SSL certificate validity

### Phase 6: Applications
```bash
# Install Istio, monitoring, and chess app
./resource-manager.sh dev install apps

# Check application status
./resource-manager.sh dev status apps

# Verify applications are running
kubectl get pods -A
helm list -A
```

**What gets created:**
- Istio service mesh (base, istiod, gateway)
- Prometheus and Grafana monitoring stack
- Jaeger distributed tracing
- Chess application with all AI components

**Testing:**
- Access chess application through CloudFront
- Verify Istio traffic management
- Check monitoring dashboards
- Test AI training functionality

## Testing Commands

### Individual Resource Testing

```bash
# Test VPC connectivity
./resource-manager.sh dev health vpc

# Test storage access
./resource-manager.sh dev health storage

# Test compute resources
./resource-manager.sh dev health compute

# Test applications
./resource-manager.sh dev health apps

# Full system health check
./resource-manager.sh dev health all
```

### Incremental Scaling Tests

```bash
# Scale down applications only
./resource-manager.sh dev halt apps

# Scale down compute resources
./resource-manager.sh dev halt compute

# Restart compute resources
./resource-manager.sh dev restart compute

# Restart applications
./resource-manager.sh dev restart apps
```

## Rollback Strategy

If any phase fails, you can remove specific resources:

```bash
# Remove applications only
./resource-manager.sh dev remove apps

# Remove network services
./resource-manager.sh dev remove network

# Remove compute resources
./resource-manager.sh dev remove compute

# Remove security components
./resource-manager.sh dev remove security

# Remove storage
./resource-manager.sh dev remove storage

# Remove VPC (last)
./resource-manager.sh dev remove vpc
```

## Cost Optimization During Testing

### Minimal Testing Setup
```bash
# Install only essential components for basic testing
./resource-manager.sh dev install vpc
./resource-manager.sh dev install storage
./resource-manager.sh dev install compute

# Skip expensive components initially
# - Skip network (CloudFront/API Gateway) for internal testing
# - Skip apps until compute is validated
```

### Halt Resources When Not Testing
```bash
# Halt compute resources overnight
./resource-manager.sh dev halt compute

# Restart for next day testing
./resource-manager.sh dev restart compute
```

## Validation Checklist

### Phase 1 - VPC Validation
- [ ] Internet VPC created with public subnets
- [ ] Private VPC created with private subnets
- [ ] Transit Gateway connecting VPCs
- [ ] NAT Gateways providing internet access
- [ ] Security groups configured correctly

### Phase 2 - Storage Validation
- [ ] S3 bucket created with private access
- [ ] ECR repository accessible
- [ ] S3 VPC endpoint working
- [ ] Bucket policies preventing internet access

### Phase 3 - Security Validation
- [ ] Secrets Manager storing test secrets
- [ ] WAF rules active and blocking test attacks
- [ ] IAM roles created with correct permissions

### Phase 4 - Compute Validation
- [ ] EKS cluster in ACTIVE state
- [ ] Node groups scaled to desired capacity
- [ ] Pods can access S3 via VPC endpoint
- [ ] Secrets accessible from pods

### Phase 5 - Network Validation
- [ ] API Gateway responding to requests
- [ ] VPC Link connecting to private resources
- [ ] CloudFront distribution deployed
- [ ] WAF protecting CloudFront

### Phase 6 - Application Validation
- [ ] Istio components running
- [ ] Monitoring stack operational
- [ ] Chess application accessible
- [ ] AI training functionality working

## Troubleshooting Common Issues

### VPC Issues
```bash
# Check VPC peering status
aws ec2 describe-vpc-peering-connections

# Verify route tables
aws ec2 describe-route-tables
```

### EKS Issues
```bash
# Check cluster status
aws eks describe-cluster --name chess-app-dev-cluster

# Verify node group status
aws eks describe-nodegroup --cluster-name chess-app-dev-cluster --nodegroup-name chess-app-dev-general
```

### Application Issues
```bash
# Check pod logs
kubectl logs -n chess-app deployment/chess-app

# Verify Istio configuration
kubectl get gateway,virtualservice,destinationrule -A
```

This incremental approach allows you to:
1. **Test each layer independently**
2. **Identify issues early**
3. **Minimize costs during development**
4. **Build confidence in the infrastructure**
5. **Enable quick rollbacks if needed**