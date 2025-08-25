# Chess Game AWS Deployment Design

## Overview
Deploy the Chess Web Game on AWS using EKS with Istio service mesh, API Gateway, and multi-AZ architecture for high availability and scalability.

## Architecture Components

### 1. CI/CD Pipeline
- **GitHub Actions** → Build Spring Boot JAR → Docker Image → ECR
- **Dockerfile**: Multi-stage build (Maven + OpenJDK 21)
- **ECR Repository**: Store container images with versioning

### 2. AWS EKS Cluster
- **Kubernetes Version**: 1.28+
- **Node Groups**: 
  - General Purpose: t3.large (2-6 nodes, multi-AZ)
  - GPU Nodes: g4dn.xlarge (1-3 nodes for AI training)
- **Networking**: VPC with private/public subnets across 3 AZs
- **Storage**: EBS CSI driver for persistent volumes

### 3. Istio Service Mesh
- **Ingress Gateway**: External traffic entry point
- **Virtual Services**: Routing rules with session affinity
- **Destination Rules**: Load balancing with consistent hash
- **Session Stickiness**: Based on WebSocket connection ID

### 4. Application Deployment
- **Deployment**: 3 replicas across AZs
- **Service**: ClusterIP with Istio sidecar injection
- **ConfigMap**: Application properties (non-sensitive)
- **Secret**: OpenAI API key, database credentials
- **PVC**: Persistent storage for AI training data

### 5. API Gateway Integration
- **AWS API Gateway v2**: WebSocket and HTTP APIs
- **VPC Link**: Private integration with EKS
- **Custom Domain**: Route53 + ACM certificate
- **Rate Limiting**: 1000 requests/minute per client

### 6. Monitoring & Logging
- **CloudWatch**: Container insights and logs
- **Prometheus**: Metrics collection via Istio
- **Grafana**: Dashboards for application metrics
- **Jaeger**: Distributed tracing

## Terraform Structure

```
terraform/
├── main.tf                 # Provider and backend configuration
├── variables.tf            # Input variables
├── outputs.tf              # Output values
├── modules/
│   ├── vpc/               # VPC, subnets, NAT gateway
│   ├── eks/               # EKS cluster and node groups
│   ├── istio/             # Istio installation and configuration
│   ├── api-gateway/       # API Gateway and VPC Link
│   ├── ecr/               # Container registry
│   ├── monitoring/        # CloudWatch, Prometheus setup
│   └── chess-app/         # Application deployment
└── environments/
    ├── dev/
    ├── staging/
    └── prod/
```

## Key Terraform Modules

### VPC Module
- 3 public subnets (NAT gateways)
- 3 private subnets (EKS nodes)
- Internet Gateway and route tables
- Security groups for EKS and ALB

### EKS Module
- EKS cluster with OIDC provider
- Managed node groups (general + GPU)
- EBS CSI driver addon
- AWS Load Balancer Controller

### Istio Module
- Helm provider for Istio installation
- Istio operator configuration
- Gateway and VirtualService resources
- Session affinity configuration

### Chess App Module
- Kubernetes deployment manifest
- Service and Ingress resources
- ConfigMap and Secret management
- Persistent Volume Claims

## Session Stickiness Configuration

### Istio Destination Rule
```yaml
apiVersion: networking.istio.io/v1beta1
kind: DestinationRule
metadata:
  name: chess-app-sticky
spec:
  host: chess-app-service
  trafficPolicy:
    loadBalancer:
      consistentHash:
        httpHeaderName: "x-session-id"
```

### WebSocket Session Management
- Generate session ID on WebSocket connection
- Include session ID in all WebSocket messages
- Istio routes based on consistent hash

## Multi-AZ Deployment Strategy

### High Availability
- EKS nodes distributed across 3 AZs
- Application replicas with pod anti-affinity
- RDS Multi-AZ for persistent data (if needed)
- EFS for shared AI training data

### Disaster Recovery
- Cross-region ECR replication
- Automated backups of training data
- Infrastructure as Code for rapid recovery

## Security Considerations

### Network Security
- Private subnets for EKS nodes
- Security groups with least privilege
- VPC endpoints for AWS services

### Application Security
- Kubernetes RBAC
- Pod Security Standards
- Secrets management with AWS Secrets Manager
- mTLS via Istio service mesh

## Scaling Strategy

### Horizontal Pod Autoscaler
- CPU/Memory based scaling (2-10 replicas)
- Custom metrics for WebSocket connections

### Cluster Autoscaler
- Node group scaling based on pod demands
- GPU nodes scale 0-3 for AI training workloads

### Vertical Pod Autoscaler
- Right-size resource requests/limits
- Optimize cost and performance

## Cost Optimization

### Resource Management
- Spot instances for non-critical workloads
- Reserved instances for baseline capacity
- Scheduled scaling for predictable patterns

### Storage Optimization
- EBS GP3 volumes with optimized IOPS
- Lifecycle policies for training data
- Compression for AI model storage

## Deployment Pipeline

### Build Stage
1. GitHub webhook triggers build
2. Maven compile and test
3. Docker image build and scan
4. Push to ECR with semantic versioning

### Deploy Stage
1. Terraform plan and apply
2. Kubernetes manifest deployment
3. Istio configuration update
4. Health checks and rollback capability

## Monitoring and Alerting

### Key Metrics
- WebSocket connection count
- AI training job status
- Response time and error rates
- Resource utilization (CPU/Memory/GPU)

### Alerts
- Pod restart loops
- High error rates (>5%)
- Resource exhaustion
- Training job failures

## Estimated Costs (Monthly)

### Development Environment
- EKS Cluster: $73
- t3.medium nodes (2): $60
- ALB: $23
- API Gateway: $10
- **Total: ~$166/month**

### Production Environment
- EKS Cluster: $73
- t3.large nodes (3): $135
- g4dn.xlarge GPU (1): $370
- ALB + API Gateway: $50
- Monitoring: $30
- **Total: ~$658/month**

## Implementation Phases

### Phase 1: Core Infrastructure
- VPC and EKS cluster setup
- Basic application deployment
- Load balancer configuration

### Phase 2: Service Mesh
- Istio installation and configuration
- Session stickiness implementation
- Security policies

### Phase 3: API Gateway Integration
- WebSocket API setup
- Custom domain configuration
- Rate limiting and monitoring

### Phase 4: Production Hardening
- Multi-AZ deployment
- Monitoring and alerting
- Backup and disaster recovery

This design provides a production-ready, scalable, and secure deployment of the Chess game on AWS with proper session management for WebSocket connections and high availability across multiple availability zones.