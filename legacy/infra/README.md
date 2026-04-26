# Chess App Infrastructure

⚠️ **WARNING: UNTESTED SCRIPTS** ⚠️

**IMPORTANT NOTICE**: All infrastructure scripts, Terraform modules, Helm charts, and automation tools in this repository are **NOT YET TESTED** in actual AWS environments. These scripts are provided as a comprehensive infrastructure design and should be thoroughly tested in a development environment before any production use.

**Recommended Testing Approach**:
1. Start with the incremental deployment guide (`scripts/deployment-order.md`)
2. Use the granular resource manager (`scripts/resource-manager.sh`) to deploy one component at a time
3. Run automated test scenarios (`scripts/test-scenarios.sh`) to validate each phase
4. Monitor costs closely during testing phases
5. Have rollback procedures ready for each deployment phase

This directory contains all infrastructure code for deploying the Chess Web Game on AWS using Terraform and Helm.

## Directory Structure

```
infra/
├── terraform/                 # Terraform infrastructure code
│   ├── providers.tf           # Provider configurations
│   ├── variables.tf           # Variable definitions
│   ├── main.tf               # Main configuration
│   ├── outputs.tf            # Output values
│   ├── modules/              # Terraform modules
│   │   ├── internet-vpc/     # Internet-facing VPC
│   │   ├── private-vpc/      # Private VPC for EKS
│   │   ├── transit-gateway/  # Cross-VPC routing
│   │   ├── eks/              # EKS cluster
│   │   ├── helm-istio/       # Istio via Helm
│   │   ├── helm-monitoring/  # Monitoring stack
│   │   ├── cloudfront/       # CDN
│   │   ├── waf/              # Web Application Firewall
│   │   ├── api-gateway/      # API Gateway
│   │   ├── ecr/              # Container registry
│   │   ├── s3/               # S3 storage
│   │   ├── secrets-manager/  # Secrets management
│   │   └── helm-chess-app/   # Chess app deployment
│   └── environments/         # Environment-specific configs
│       ├── dev/
│       ├── staging/
│       └── prod/
└── helm/                     # Helm charts
    ├── chess-app/            # Chess application chart
    │   ├── Chart.yaml
    │   ├── values.yaml
    │   └── templates/
    └── values/               # Environment-specific values
        ├── dev.yaml
        ├── staging.yaml
        └── prod.yaml
```

## Prerequisites

- AWS CLI configured with appropriate permissions
- Terraform >= 1.5.0
- Helm >= 3.12.0
- kubectl >= 1.28.0

## Deployment

### 1. Initialize Terraform

```bash
cd infra/terraform
terraform init
```

### 2. Deploy Infrastructure

#### Development Environment
```bash
terraform plan -var-file="environments/dev/terraform.tfvars"
terraform apply -var-file="environments/dev/terraform.tfvars"
```

#### Production Environment
```bash
terraform plan -var-file="environments/prod/terraform.tfvars"
terraform apply -var-file="environments/prod/terraform.tfvars"
```

### 3. Configure kubectl

```bash
aws eks update-kubeconfig --region us-west-2 --name chess-app-dev-cluster
```

### 4. Verify Deployment

```bash
kubectl get nodes
kubectl get pods -A
helm list -A
```

## Configuration

All configurations are environment-specific and stored in:
- `terraform/environments/{env}/terraform.tfvars` - Infrastructure config
- `helm/values/{env}.yaml` - Application config

## Architecture

- **Multi-VPC**: Separate VPCs for internet-facing and private components
- **EKS**: Kubernetes cluster in private VPC
- **Istio**: Service mesh for traffic management
- **CloudFront + WAF**: CDN and security at edge
- **API Gateway**: Single entry point from internet
- **S3**: AI model storage with VPC endpoints
- **Secrets Manager**: Secure credential management

## Security

- Private EKS cluster with no internet access
- VPC endpoints for AWS services
- WAF protection at CloudFront edge
- Secrets managed via AWS Secrets Manager
- IAM roles for service accounts (IRSA)

## Monitoring

- Prometheus for metrics collection
- Grafana for visualization
- Jaeger for distributed tracing
- CloudWatch for AWS service monitoring

## Scaling

- Horizontal Pod Autoscaler for application pods
- Cluster Autoscaler for EKS nodes
- CloudFront for global edge caching
- Multi-AZ deployment for high availability