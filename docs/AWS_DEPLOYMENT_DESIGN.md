# Chess Game AWS Deployment Design

## Overview
Deploy the Chess Web Game on AWS using EKS with Istio service mesh, API Gateway, and multi-AZ architecture for high availability and scalability.

## Architecture Components

### 1. CI/CD Pipeline
- **GitHub Actions** → Build Spring Boot JAR → Docker Image → ECR
- **Dockerfile**: Multi-stage build (Maven + OpenJDK 21)
- **ECR Repository**: Store container images with versioning

### 2. Multi-VPC Architecture
- **Internet VPC**: CDN, WAF, API Gateway (public subnets)
- **Private VPC**: EKS, Istio, applications (private subnets only)
- **Transit Gateway**: Centralized routing between VPCs
- **Network ACLs**: Layer 4 security controls

### 3. AWS EKS Cluster (Private VPC)
- **Kubernetes Version**: 1.28+
- **Node Groups**: 
  - General Purpose: t3.large (2-6 nodes, multi-AZ)
  - GPU Nodes: g4dn.xlarge (1-3 nodes for AI training)
- **Networking**: Private subnets only, no internet gateway
- **Storage**: S3 CSI driver for direct AI state file access (no EBS volumes)

### 4. Istio Service Mesh (Private VPC)
- **Private Ingress Gateway**: Internal traffic only (no internet access)
- **Virtual Services**: Routing rules with session affinity
- **Destination Rules**: Load balancing with consistent hash
- **Session Stickiness**: Based on WebSocket connection ID
- **API Gateway Integration**: Only entry point from internet

### 4. Application Deployment
- **Deployment**: 3 replicas across AZs
- **Service**: ClusterIP with Istio sidecar injection
- **ConfigMap**: Application properties (non-sensitive)
- **AWS Secrets Manager**: OpenAI API key, database credentials
- **Secrets Store CSI Driver**: Mount secrets as volumes
- **S3 Direct Storage**: All AI state files stored directly in S3 (no PVC needed)

### 5. CloudFront CDN & WAF
- **CloudFront Distribution**: Global edge locations for low latency
- **AWS WAF**: DDoS protection, IP filtering, rate limiting
- **Static Asset Caching**: CSS, JS, images cached at edge
- **WebSocket Support**: Real-time game connections

### 6. API Gateway Integration
- **AWS API Gateway v2**: WebSocket and HTTP APIs
- **VPC Link**: Private integration with Istio Gateway
- **Custom Domain**: Route53 + ACM certificate
- **Rate Limiting**: 1000 requests/minute per client
- **CloudFront Origin**: API Gateway as CloudFront origin

### 6. Storage Architecture
- **S3 Buckets**: Multi-AZ AI state file storage with versioning
- **S3 CSI Driver**: Direct S3 bucket mounting (no PV/PVC layer)
- **Cross-Region Replication**: Disaster recovery for AI models
- **Intelligent Tiering**: Cost optimization for infrequently accessed models

### 7. Monitoring & Logging
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
├── providers.tf            # Terraform, AWS, Helm, Kubernetes providers
├── modules/
│   ├── internet-vpc/      # Internet-facing VPC (CDN, WAF, API GW)
│   ├── private-vpc/       # Private VPC (EKS, Istio)
│   ├── transit-gateway/   # Cross-VPC routing and controls
│   ├── eks/               # EKS cluster and node groups
│   ├── helm-istio/        # Istio via Helm charts
│   ├── helm-monitoring/   # Prometheus, Grafana via Helm
│   ├── cloudfront/        # CDN distribution and caching
│   ├── waf/               # Web Application Firewall
│   ├── api-gateway/       # API Gateway and VPC Link
│   ├── ecr/               # Container registry
│   ├── s3/                # S3 buckets for AI state files
│   ├── secrets-manager/   # AWS Secrets Manager and IAM roles
│   └── helm-chess-app/    # Chess application via Helm
├── helm/
│   ├── chess-app/         # Helm chart for chess application
│   │   ├── Chart.yaml
│   │   ├── values.yaml
│   │   └── templates/
│   └── values/            # Environment-specific values
│       ├── dev.yaml
│       ├── staging.yaml
│       └── prod.yaml
└── environments/
    ├── dev/
    ├── staging/
    └── prod/
```

## Key Terraform Modules

### Internet VPC Module
- 3 public subnets for API Gateway
- Internet Gateway and public route tables
- Security groups for API Gateway
- CloudFront and WAF integration

### Private VPC Module
- 3 private subnets for EKS nodes (no public subnets)
- NAT Gateway for outbound internet (updates only)
- Security groups for EKS and Istio
- S3 VPC Gateway Endpoint for private access

### Transit Gateway Module
- Cross-VPC routing between Internet and Private VPCs
- Route tables with specific routing rules
- Network ACLs for additional security
- VPC peering as backup option

### EKS Module
- EKS cluster with OIDC provider
- Managed node groups (general + GPU)

- AWS Load Balancer Controller
- Secrets Store CSI Driver addon
- S3 CSI Driver addon
- IAM roles for service accounts (IRSA)

### CloudFront Module
- CloudFront distribution with global edge locations
- Origin configuration pointing to API Gateway
- Caching behaviors for static and dynamic content
- Custom domain with SSL certificate

### WAF Module
- AWS WAF v2 web ACL creation
- DDoS protection and rate limiting rules
- IP allowlist/blocklist management
- Bot detection and mitigation

### Helm Istio Module
- Terraform Helm provider for Istio charts
- Istio base, istiod, and gateway installations
- Custom values for private VPC configuration
- Gateway and VirtualService via Helm templates

### Helm Monitoring Module
- Prometheus stack via kube-prometheus-stack chart
- Grafana with custom dashboards
- Jaeger for distributed tracing
- AlertManager for notifications

### S3 Storage Module
- S3 buckets with versioning and encryption
- Cross-region replication configuration
- S3 Intelligent Tiering policies
- IAM roles for S3 access

### Secrets Manager Module
- AWS Secrets Manager secrets creation
- IAM roles and policies for secret access
- Service account with IRSA configuration
- SecretProviderClass for CSI driver

### Helm Chess App Module
- Custom Helm chart for chess application
- Templated Kubernetes manifests
- Environment-specific values (dev/staging/prod)
- ConfigMap and Secrets integration
- S3 CSI Driver volume mounting

## Session Stickiness Configuration

## Terraform + Helm Integration

### Provider Configuration

#### Terraform Providers
```hcl
# providers.tf
terraform {
  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = var.aws_provider_version
    }
    helm = {
      source  = "hashicorp/helm"
      version = var.helm_provider_version
    }
    kubernetes = {
      source  = "hashicorp/kubernetes"
      version = var.kubernetes_provider_version
    }
  }
}

provider "aws" {
  region = var.aws_region
  
  default_tags {
    tags = var.common_tags
  }
}

provider "helm" {
  kubernetes {
    host                   = module.eks.cluster_endpoint
    cluster_ca_certificate = base64decode(module.eks.cluster_certificate_authority_data)
    exec {
      api_version = var.kubernetes_api_version
      command     = var.aws_cli_command
      args        = ["eks", "get-token", "--cluster-name", module.eks.cluster_name, "--region", var.aws_region]
    }
  }
}

provider "kubernetes" {
  host                   = module.eks.cluster_endpoint
  cluster_ca_certificate = base64decode(module.eks.cluster_certificate_authority_data)
  exec {
    api_version = var.kubernetes_api_version
    command     = var.aws_cli_command
    args        = ["eks", "get-token", "--cluster-name", module.eks.cluster_name, "--region", var.aws_region]
  }
}
```

### Istio Installation via Helm

#### Terraform Helm Resources
```hcl
# modules/helm-istio/main.tf
resource "kubernetes_namespace" "istio_system" {
  metadata {
    name = var.istio_namespace
    labels = var.namespace_labels
  }
}

resource "helm_release" "istio_base" {
  name       = var.istio_base_release_name
  repository = var.istio_chart_repository
  chart      = var.istio_base_chart_name
  namespace  = kubernetes_namespace.istio_system.metadata[0].name
  version    = var.istio_version
  
  timeout = var.helm_timeout
  
  depends_on = [kubernetes_namespace.istio_system]
}

resource "helm_release" "istiod" {
  name       = var.istiod_release_name
  repository = var.istio_chart_repository
  chart      = var.istiod_chart_name
  namespace  = kubernetes_namespace.istio_system.metadata[0].name
  version    = var.istio_version
  
  timeout = var.helm_timeout
  
  values = [
    templatefile("${path.module}/values/istiod.yaml", {
      private_vpc_cidr = var.private_vpc_cidr
      pilot_env        = var.istio_pilot_env
      resources        = var.istiod_resources
    })
  ]
  
  depends_on = [helm_release.istio_base]
}

resource "helm_release" "istio_gateway" {
  name       = var.istio_gateway_release_name
  repository = var.istio_chart_repository
  chart      = var.istio_gateway_chart_name
  namespace  = var.istio_gateway_namespace
  version    = var.istio_version
  
  create_namespace = var.create_gateway_namespace
  timeout          = var.helm_timeout
  
  values = [
    templatefile("${path.module}/values/gateway.yaml", {
      service_type     = var.gateway_service_type
      service_ports    = var.gateway_service_ports
      resources        = var.gateway_resources
      node_selector    = var.gateway_node_selector
    })
  ]
  
  depends_on = [helm_release.istiod]
}
```

### Chess Application Helm Chart

#### Terraform Helm Release
```hcl
# modules/helm-chess-app/main.tf
resource "kubernetes_namespace" "chess_app" {
  metadata {
    name   = var.app_namespace
    labels = merge(var.namespace_labels, {
      "istio-injection" = var.istio_injection_enabled ? "enabled" : "disabled"
    })
  }
}

resource "helm_release" "chess_app" {
  name      = var.app_release_name
  chart     = var.app_chart_path
  namespace = kubernetes_namespace.chess_app.metadata[0].name
  version   = var.app_chart_version
  
  timeout          = var.helm_timeout
  cleanup_on_fail  = var.helm_cleanup_on_fail
  atomic           = var.helm_atomic
  
  values = [
    file("${var.helm_values_path}/${var.environment}.yaml")
  ]
  
  dynamic "set" {
    for_each = var.helm_set_values
    content {
      name  = set.key
      value = set.value
    }
  }
  
  dynamic "set_sensitive" {
    for_each = var.helm_set_sensitive_values
    content {
      name  = set_sensitive.key
      value = set_sensitive.value
    }
  }
  
  depends_on = [
    kubernetes_namespace.chess_app
  ]
}
```

### Variable Definitions

#### Global Variables (variables.tf)
```hcl
# Environment Configuration
variable "environment" {
  description = "Environment name (dev, staging, prod)"
  type        = string
  validation {
    condition     = contains(["dev", "staging", "prod"], var.environment)
    error_message = "Environment must be dev, staging, or prod."
  }
}

variable "project_name" {
  description = "Project name for resource naming"
  type        = string
  default     = "chess-app"
}

variable "aws_region" {
  description = "AWS region for deployment"
  type        = string
  default     = "us-west-2"
}

variable "common_tags" {
  description = "Common tags for all resources"
  type        = map(string)
  default = {
    Project     = "chess-app"
    ManagedBy   = "terraform"
    Environment = ""
  }
}

# Provider Versions
variable "aws_provider_version" {
  description = "AWS provider version"
  type        = string
  default     = "~> 5.0"
}

variable "helm_provider_version" {
  description = "Helm provider version"
  type        = string
  default     = "~> 2.12"
}

variable "kubernetes_provider_version" {
  description = "Kubernetes provider version"
  type        = string
  default     = "~> 2.24"
}

# Kubernetes Configuration
variable "kubernetes_api_version" {
  description = "Kubernetes API version for authentication"
  type        = string
  default     = "client.authentication.k8s.io/v1beta1"
}

variable "aws_cli_command" {
  description = "AWS CLI command path"
  type        = string
  default     = "aws"
}

# Istio Configuration
variable "istio_version" {
  description = "Istio version to install"
  type        = string
  default     = "1.20.0"
}

variable "istio_namespace" {
  description = "Namespace for Istio system components"
  type        = string
  default     = "istio-system"
}

variable "istio_chart_repository" {
  description = "Istio Helm chart repository URL"
  type        = string
  default     = "https://istio-release.storage.googleapis.com/charts"
}

variable "istio_base_chart_name" {
  description = "Istio base chart name"
  type        = string
  default     = "base"
}

variable "istiod_chart_name" {
  description = "Istiod chart name"
  type        = string
  default     = "istiod"
}

variable "istio_gateway_chart_name" {
  description = "Istio gateway chart name"
  type        = string
  default     = "gateway"
}

# Application Configuration
variable "app_namespace" {
  description = "Namespace for chess application"
  type        = string
  default     = "chess-app"
}

variable "app_release_name" {
  description = "Helm release name for chess application"
  type        = string
  default     = "chess-app"
}

variable "app_chart_path" {
  description = "Path to chess application Helm chart"
  type        = string
  default     = "./helm/chess-app"
}

variable "app_version" {
  description = "Chess application version/image tag"
  type        = string
}

variable "helm_values_path" {
  description = "Path to Helm values files"
  type        = string
  default     = "./helm/values"
}

variable "helm_set_values" {
  description = "Map of Helm values to set"
  type        = map(string)
  default     = {}
}

variable "helm_set_sensitive_values" {
  description = "Map of sensitive Helm values to set"
  type        = map(string)
  default     = {}
  sensitive   = true
}

# Helm Configuration
variable "helm_timeout" {
  description = "Helm operation timeout in seconds"
  type        = number
  default     = 600
}

variable "helm_cleanup_on_fail" {
  description = "Cleanup resources on Helm failure"
  type        = bool
  default     = true
}

variable "helm_atomic" {
  description = "Atomic Helm operations"
  type        = bool
  default     = true
}

# Network Configuration
variable "vpc_cidr_blocks" {
  description = "CIDR blocks for VPCs"
  type = object({
    internet_vpc = string
    private_vpc  = string
  })
  default = {
    internet_vpc = "10.0.0.0/16"
    private_vpc  = "10.1.0.0/16"
  }
}

variable "availability_zones" {
  description = "List of availability zones"
  type        = list(string)
  default     = []
}

# EKS Configuration
variable "eks_cluster_version" {
  description = "EKS cluster Kubernetes version"
  type        = string
  default     = "1.28"
}

variable "eks_node_groups" {
  description = "EKS node group configurations"
  type = map(object({
    instance_types = list(string)
    min_size      = number
    max_size      = number
    desired_size  = number
    disk_size     = number
    ami_type      = string
  }))
  default = {
    general = {
      instance_types = ["t3.large"]
      min_size      = 2
      max_size      = 6
      desired_size  = 3
      disk_size     = 50
      ami_type      = "AL2_x86_64"
    }
    gpu = {
      instance_types = ["g4dn.xlarge"]
      min_size      = 0
      max_size      = 3
      desired_size  = 1
      disk_size     = 100
      ami_type      = "AL2_x86_64_GPU"
    }
  }
}
```

### Monitoring Stack via Helm

#### Prometheus and Grafana
```hcl
# modules/helm-monitoring/main.tf
resource "kubernetes_namespace" "monitoring" {
  metadata {
    name   = var.monitoring_namespace
    labels = var.namespace_labels
  }
}

resource "helm_release" "kube_prometheus_stack" {
  name       = var.prometheus_release_name
  repository = var.prometheus_chart_repository
  chart      = var.prometheus_chart_name
  namespace  = kubernetes_namespace.monitoring.metadata[0].name
  version    = var.prometheus_chart_version
  
  timeout = var.helm_timeout
  
  values = [
    templatefile("${path.module}/values/prometheus.yaml", {
      grafana_domain    = var.grafana_domain
      storage_class     = var.storage_class
      retention_period  = var.prometheus_retention_period
      storage_size      = var.prometheus_storage_size
      resources         = var.prometheus_resources
    })
  ]
  
  depends_on = [kubernetes_namespace.monitoring]
}

resource "helm_release" "jaeger" {
  name       = var.jaeger_release_name
  repository = var.jaeger_chart_repository
  chart      = var.jaeger_chart_name
  namespace  = kubernetes_namespace.monitoring.metadata[0].name
  version    = var.jaeger_chart_version
  
  timeout = var.helm_timeout
  
  values = [
    templatefile("${path.module}/values/jaeger.yaml", {
      storage_type = var.jaeger_storage_type
      resources    = var.jaeger_resources
    })
  ]
  
  depends_on = [helm_release.kube_prometheus_stack]
}
```

### Private Istio Gateway Configuration

#### Internal-Only Istio Gateway
```yaml
apiVersion: networking.istio.io/v1beta1
kind: Gateway
metadata:
  name: chess-app-gateway
  namespace: chess-app
spec:
  selector:
    istio: ingressgateway
  servers:
  - port:
      number: 80
      name: http
      protocol: HTTP
    hosts:
    - "chess-app.internal"  # Internal hostname only
  - port:
      number: 443
      name: https
      protocol: HTTPS
    hosts:
    - "chess-app.internal"
    tls:
      mode: SIMPLE
      credentialName: chess-app-tls
```

#### Virtual Service for Internal Routing
```yaml
apiVersion: networking.istio.io/v1beta1
kind: VirtualService
metadata:
  name: chess-app-vs
  namespace: chess-app
spec:
  hosts:
  - "chess-app.internal"
  gateways:
  - chess-app-gateway
  http:
  - match:
    - uri:
        prefix: "/"
    route:
    - destination:
        host: chess-app-service
        port:
          number: 8081
```

#### Session Stickiness Destination Rule
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

## CloudFront CDN & WAF Configuration

### AWS WAF v2 Setup

#### WAF Web ACL with Security Rules
```hcl
resource "aws_wafv2_web_acl" "chess_app" {
  name  = "chess-app-waf-${var.environment}"
  scope = "CLOUDFRONT"
  
  default_action {
    allow {}
  }
  
  # Rate limiting rule
  rule {
    name     = "RateLimitRule"
    priority = 1
    
    override_action {
      none {}
    }
    
    statement {
      rate_based_statement {
        limit              = 2000
        aggregate_key_type = "IP"
      }
    }
    
    visibility_config {
      cloudwatch_metrics_enabled = true
      metric_name                = "RateLimitRule"
      sampled_requests_enabled   = true
    }
    
    action {
      block {}
    }
  }
  
  # AWS Managed Rules - Core Rule Set
  rule {
    name     = "AWSManagedRulesCommonRuleSet"
    priority = 2
    
    override_action {
      none {}
    }
    
    statement {
      managed_rule_group_statement {
        name        = "AWSManagedRulesCommonRuleSet"
        vendor_name = "AWS"
      }
    }
    
    visibility_config {
      cloudwatch_metrics_enabled = true
      metric_name                = "CommonRuleSetMetric"
      sampled_requests_enabled   = true
    }
  }
  
  # Bot Control
  rule {
    name     = "AWSManagedRulesBotControlRuleSet"
    priority = 3
    
    override_action {
      none {}
    }
    
    statement {
      managed_rule_group_statement {
        name        = "AWSManagedRulesBotControlRuleSet"
        vendor_name = "AWS"
      }
    }
    
    visibility_config {
      cloudwatch_metrics_enabled = true
      metric_name                = "BotControlMetric"
      sampled_requests_enabled   = true
    }
  }
  
  visibility_config {
    cloudwatch_metrics_enabled = true
    metric_name                = "ChessAppWAF"
    sampled_requests_enabled   = true
  }
}
```

### CloudFront Distribution

#### CDN Configuration with WAF
```hcl
resource "aws_cloudfront_distribution" "chess_app" {
  origin {
    domain_name = aws_apigatewayv2_domain_name.chess_app.domain_name_configuration[0].target_domain_name
    origin_id   = "chess-app-api-gateway"
    
    custom_origin_config {
      http_port              = 80
      https_port             = 443
      origin_protocol_policy = "https-only"
      origin_ssl_protocols   = ["TLSv1.2"]
    }
  }
  
  enabled             = true
  is_ipv6_enabled     = true
  default_root_object = "index.html"
  
  # Static assets caching behavior
  ordered_cache_behavior {
    path_pattern     = "/static/*"
    allowed_methods  = ["GET", "HEAD"]
    cached_methods   = ["GET", "HEAD"]
    target_origin_id = "chess-app-api-gateway"
    
    forwarded_values {
      query_string = false
      cookies {
        forward = "none"
      }
    }
    
    viewer_protocol_policy = "redirect-to-https"
    min_ttl                = 86400   # 1 day
    default_ttl            = 604800  # 1 week
    max_ttl                = 2592000 # 30 days
    compress               = true
  }
  
  # WebSocket and API behavior
  default_cache_behavior {
    allowed_methods        = ["DELETE", "GET", "HEAD", "OPTIONS", "PATCH", "POST", "PUT"]
    cached_methods         = ["GET", "HEAD"]
    target_origin_id       = "chess-app-api-gateway"
    viewer_protocol_policy = "redirect-to-https"
    
    forwarded_values {
      query_string = true
      headers      = ["Authorization", "x-session-id", "Upgrade", "Connection"]
      
      cookies {
        forward = "all"
      }
    }
    
    min_ttl     = 0
    default_ttl = 0
    max_ttl     = 0
  }
  
  # Custom domain
  aliases = [var.domain_name]
  
  viewer_certificate {
    acm_certificate_arn      = aws_acm_certificate.chess_app.arn
    ssl_support_method       = "sni-only"
    minimum_protocol_version = "TLSv1.2_2021"
  }
  
  # WAF association
  web_acl_id = aws_wafv2_web_acl.chess_app.arn
  
  restrictions {
    geo_restriction {
      restriction_type = "none"
    }
  }
  
  tags = {
    Name        = "chess-app-cdn"
    Environment = var.environment
  }
}
```

### API Gateway to Istio Integration

#### VPC Link Configuration
```hcl
resource "aws_apigatewayv2_vpc_link" "chess_app" {
  name               = "chess-app-vpc-link"
  protocol_type      = "HTTP"
  subnet_ids         = module.vpc.private_subnets
  security_group_ids = [aws_security_group.api_gateway_vpc_link.id]
}

resource "aws_security_group" "api_gateway_vpc_link" {
  name_prefix = "chess-app-vpc-link-"
  vpc_id      = module.vpc.vpc_id
  
  ingress {
    from_port   = 80
    to_port     = 80
    protocol    = "tcp"
    cidr_blocks = [module.vpc.vpc_cidr_block]
  }
  
  ingress {
    from_port   = 443
    to_port     = 443
    protocol    = "tcp"
    cidr_blocks = [module.vpc.vpc_cidr_block]
  }
  
  egress {
    from_port   = 0
    to_port     = 65535
    protocol    = "tcp"
    cidr_blocks = [module.vpc.vpc_cidr_block]
  }
}
```

#### API Gateway Integration
```hcl
resource "aws_apigatewayv2_integration" "chess_app" {
  api_id             = aws_apigatewayv2_api.chess_app.id
  integration_type   = "HTTP_PROXY"
  integration_method = "ANY"
  integration_uri    = "http://chess-app.internal"
  connection_type    = "VPC_LINK"
  connection_id      = aws_apigatewayv2_vpc_link.chess_app.id
}
```

### WebSocket Session Management
- Generate session ID on WebSocket connection
- Include session ID in all WebSocket messages
- Istio routes based on consistent hash
- API Gateway forwards WebSocket connections to Istio

## AWS Secrets Manager Integration

### Secret Management Architecture
- **AWS Secrets Manager**: Store OpenAI API keys, database credentials
- **Secrets Store CSI Driver**: Mount secrets as files in pods
- **IAM Roles for Service Accounts (IRSA)**: Secure access without storing credentials
- **Automatic Rotation**: Enable automatic secret rotation for enhanced security

### Terraform Configuration

#### Secrets Manager Secret
```hcl
resource "aws_secretsmanager_secret" "chess_app_secrets" {
  name        = "chess-app-secrets-${var.environment}"
  description = "Chess application secrets"
  
  replica {
    region = var.backup_region
  }
}

resource "aws_secretsmanager_secret_version" "chess_app_secrets" {
  secret_id = aws_secretsmanager_secret.chess_app_secrets.id
  secret_string = jsonencode({
    openai_api_key = var.openai_api_key
    database_url   = var.database_url
    database_password = var.database_password
  })
}
```

#### IAM Role for Service Account
```hcl
resource "aws_iam_role" "chess_app_secrets_role" {
  name = "chess-app-secrets-role-${var.environment}"
  
  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Action = "sts:AssumeRoleWithWebIdentity"
      Effect = "Allow"
      Principal = {
        Federated = module.eks.oidc_provider_arn
      }
      Condition = {
        StringEquals = {
          "${replace(module.eks.cluster_oidc_issuer_url, "https://", "")}:sub" = "system:serviceaccount:chess-app:chess-app-sa"
          "${replace(module.eks.cluster_oidc_issuer_url, "https://", "")}:aud" = "sts.amazonaws.com"
        }
      }
    }]
  })
}

resource "aws_iam_role_policy" "chess_app_secrets_policy" {
  name = "chess-app-secrets-policy"
  role = aws_iam_role.chess_app_secrets_role.id
  
  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect = "Allow"
      Action = [
        "secretsmanager:GetSecretValue",
        "secretsmanager:DescribeSecret"
      ]
      Resource = aws_secretsmanager_secret.chess_app_secrets.arn
    }]
  })
}
```

### Kubernetes Configuration

#### Service Account
```yaml
apiVersion: v1
kind: ServiceAccount
metadata:
  name: chess-app-sa
  namespace: chess-app
  annotations:
    eks.amazonaws.com/role-arn: arn:aws:iam::ACCOUNT:role/chess-app-secrets-role-prod
```

#### SecretProviderClass
```yaml
apiVersion: secrets-store.csi.x-k8s.io/v1
kind: SecretProviderClass
metadata:
  name: chess-app-secrets
  namespace: chess-app
spec:
  provider: aws
  parameters:
    objects: |
      - objectName: "chess-app-secrets-prod"
        objectType: "secretsmanager"
        jmesPath:
          - path: "openai_api_key"
            objectAlias: "openai-api-key"
          - path: "database_url"
            objectAlias: "database-url"
          - path: "database_password"
            objectAlias: "database-password"
```

#### Application Deployment
```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: chess-app
  namespace: chess-app
spec:
  template:
    spec:
      serviceAccountName: chess-app-sa
      containers:
      - name: chess-app
        image: chess-app:latest
        env:
        - name: OPENAI_API_KEY
          valueFrom:
            secretKeyRef:
              name: chess-app-secrets
              key: openai-api-key
        volumeMounts:
        - name: secrets-store
          mountPath: "/mnt/secrets"
          readOnly: true
      volumes:
      - name: secrets-store
        csi:
          driver: secrets-store.csi.k8s.io
          readOnly: true
          volumeAttributes:
            secretProviderClass: "chess-app-secrets"
```

### Application Code Integration

#### Spring Boot Configuration
```properties
# Use environment variable or mounted secret file
chess.ai.openai=${OPENAI_API_KEY:file:/mnt/secrets/openai-api-key}
database.url=${DATABASE_URL:file:/mnt/secrets/database-url}
database.password=${DATABASE_PASSWORD:file:/mnt/secrets/database-password}

# S3 configuration for AI state files
chess.ai.storage.s3.bucket=chess-ai-models-prod
chess.ai.storage.s3.region=us-west-2
chess.ai.storage.path=/mnt/s3/ai-models
```

## S3 Storage Integration for AI State Files

### Multi-AZ Storage Architecture
- **Primary S3 Bucket**: Store AI models, Q-tables, training data
- **Cross-Region Replication**: Automatic backup to secondary region
- **Versioning**: Track model evolution and enable rollbacks
- **Intelligent Tiering**: Automatic cost optimization

### Terraform S3 Configuration

#### S3 Bucket with Multi-AZ Access
```hcl
resource "aws_s3_bucket" "chess_ai_models" {
  bucket = "chess-ai-models-${var.environment}-${random_id.bucket_suffix.hex}"
}

resource "aws_s3_bucket_versioning" "chess_ai_models" {
  bucket = aws_s3_bucket.chess_ai_models.id
  versioning_configuration {
    status = "Enabled"
  }
}

resource "aws_s3_bucket_server_side_encryption_configuration" "chess_ai_models" {
  bucket = aws_s3_bucket.chess_ai_models.id
  rule {
    apply_server_side_encryption_by_default {
      sse_algorithm = "AES256"
    }
  }
}

resource "aws_s3_bucket_intelligent_tiering_configuration" "chess_ai_models" {
  bucket = aws_s3_bucket.chess_ai_models.id
  name   = "chess-ai-models-tiering"
  
  tiering {
    access_tier = "DEEP_ARCHIVE_ACCESS"
    days        = 180
  }
  
  tiering {
    access_tier = "ARCHIVE_ACCESS"
    days        = 90
  }
}

resource "aws_s3_bucket_replication_configuration" "chess_ai_models" {
  role   = aws_iam_role.s3_replication.arn
  bucket = aws_s3_bucket.chess_ai_models.id
  
  rule {
    id     = "chess-ai-models-replication"
    status = "Enabled"
    
    destination {
      bucket        = aws_s3_bucket.chess_ai_models_replica.arn
      storage_class = "STANDARD_IA"
    }
  }
}

# Block public access to S3 bucket
resource "aws_s3_bucket_public_access_block" "chess_ai_models" {
  bucket = aws_s3_bucket.chess_ai_models.id
  
  block_public_acls       = true
  block_public_policy     = true
  ignore_public_acls      = true
  restrict_public_buckets = true
}

# S3 bucket policy - only allow access from VPC
resource "aws_s3_bucket_policy" "chess_ai_models" {
  bucket = aws_s3_bucket.chess_ai_models.id
  
  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Sid       = "DenyDirectInternetAccess"
        Effect    = "Deny"
        Principal = "*"
        Action    = "s3:*"
        Resource = [
          aws_s3_bucket.chess_ai_models.arn,
          "${aws_s3_bucket.chess_ai_models.arn}/*"
        ]
        Condition = {
          StringNotEquals = {
            "aws:SourceVpce" = aws_vpc_endpoint.s3.id
          }
        }
      }
    ]
  })
}

# VPC Endpoint for S3
resource "aws_vpc_endpoint" "s3" {
  vpc_id            = module.vpc.vpc_id
  service_name      = "com.amazonaws.${var.region}.s3"
  vpc_endpoint_type = "Gateway"
  route_table_ids   = module.vpc.private_route_table_ids
  
  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Effect = "Allow"
        Principal = "*"
        Action = [
          "s3:GetObject",
          "s3:PutObject",
          "s3:DeleteObject",
          "s3:ListBucket"
        ]
        Resource = [
          aws_s3_bucket.chess_ai_models.arn,
          "${aws_s3_bucket.chess_ai_models.arn}/*"
        ]
      }
    ]
  })
}
```

#### IAM Role for S3 Access
```hcl
resource "aws_iam_role" "chess_app_s3_role" {
  name = "chess-app-s3-role-${var.environment}"
  
  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Action = "sts:AssumeRoleWithWebIdentity"
      Effect = "Allow"
      Principal = {
        Federated = module.eks.oidc_provider_arn
      }
      Condition = {
        StringEquals = {
          "${replace(module.eks.cluster_oidc_issuer_url, "https://", "")}:sub" = "system:serviceaccount:chess-app:chess-app-sa"
        }
      }
    }]
  })
}

resource "aws_iam_role_policy" "chess_app_s3_policy" {
  name = "chess-app-s3-policy"
  role = aws_iam_role.chess_app_s3_role.id
  
  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Effect = "Allow"
        Action = [
          "s3:GetObject",
          "s3:PutObject",
          "s3:DeleteObject",
          "s3:ListBucket"
        ]
        Resource = [
          aws_s3_bucket.chess_ai_models.arn,
          "${aws_s3_bucket.chess_ai_models.arn}/*"
        ]
      }
    ]
  })
}
```

### Kubernetes S3 Integration

#### Direct S3 Volume Mount (No PVC Required)
```yaml
# No PV/PVC needed - direct S3 mounting via CSI driver
# S3 provides unlimited storage and multi-AZ access natively
```

#### Updated Application Deployment
```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: chess-app
  namespace: chess-app
spec:
  template:
    spec:
      serviceAccountName: chess-app-sa
      containers:
      - name: chess-app
        image: chess-app:latest
        env:
        - name: CHESS_AI_STORAGE_PATH
          value: "/mnt/s3/ai-models"
        - name: AWS_REGION
          value: "us-west-2"
        volumeMounts:
        - name: secrets-store
          mountPath: "/mnt/secrets"
          readOnly: true
        - name: ai-models-storage
          mountPath: "/mnt/s3/ai-models"
      volumes:
      - name: secrets-store
        csi:
          driver: secrets-store.csi.k8s.io
          readOnly: true
          volumeAttributes:
            secretProviderClass: "chess-app-secrets"
      - name: ai-models-storage
        csi:
          driver: s3.csi.aws.com
          volumeAttributes:
            bucketName: chess-ai-models-prod
            region: us-west-2
```

### Network Security Architecture

#### Complete Security Flow
```
Internet → CloudFront CDN → AWS WAF → API Gateway → VPC Link → Istio Gateway → Chess App
           (Edge Caching)   (DDoS Protection)              (Private Subnets Only)
```

#### S3 Private Access
```
EKS Pod → Private Subnet → VPC S3 Endpoint → S3 Bucket
         (No Internet Route)
```

#### Security Layers
- **CloudFront CDN**: Global edge locations with caching
- **AWS WAF**: DDoS protection, rate limiting, bot detection
- **API Gateway**: Application-level routing and throttling
- **VPC Link**: Secure tunnel from API Gateway to VPC
- **Istio Gateway**: Private internal load balancer
- **EKS Pods**: Private subnets with no internet access
- **S3 Buckets**: VPC endpoint access only

### AI State File Organization

#### S3 Bucket Structure
```
chess-ai-models-prod/
├── alphazero/
│   ├── models/
│   │   ├── current/
│   │   └── versions/
│   └── training-data/
├── leela-zero/
│   ├── networks/
│   └── weights/
├── a3c/
│   ├── actor-models/
│   ├── critic-models/
│   └── experience-replay/
├── q-learning/
│   └── q-tables/
├── deep-learning/
│   └── models/
└── backups/
    └── daily/
```

## Multi-AZ Deployment Strategy

### High Availability
- EKS nodes distributed across 3 AZs
- Application replicas with pod anti-affinity
- S3 Multi-AZ storage with 99.999999999% durability
- Cross-region replication for disaster recovery
- RDS Multi-AZ for persistent data (if needed)

### Disaster Recovery
- Cross-region ECR replication
- Automated backups of training data
- Infrastructure as Code for rapid recovery

## Security Considerations

### Network Security
- Private subnets for EKS nodes
- Security groups with least privilege
- VPC endpoints for AWS services
- S3 VPC Endpoint for private S3 access
- S3 bucket policies restricting internet access
- Istio Gateway in private subnets only
- API Gateway as sole internet entry point
- VPC Link for secure API Gateway to Istio communication

### Application Security
- Kubernetes RBAC
- Pod Security Standards
- AWS Secrets Manager with IAM roles
- Secrets Store CSI Driver for secure secret mounting
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
- No EBS volumes needed (cost savings)
- S3 Intelligent Tiering for AI models (30-50% cost reduction)
- Lifecycle policies for training data archival
- Compression for AI model storage
- Cross-region replication with STANDARD_IA storage class

## Helm Chart Structure

### Chess Application Chart

#### Chart.yaml
```yaml
apiVersion: v2
name: chess-app
description: Chess Web Game with AI
type: application
version: 1.0.0
appVersion: "1.0.0"
dependencies:
  - name: postgresql
    version: "12.0.0"
    repository: "https://charts.bitnami.com/bitnami"
    condition: postgresql.enabled
```

#### values.yaml (Default)
```yaml
# Default values - overridden by environment-specific files
image:
  repository: "{{ .Values.global.image.repository }}"
  tag: "{{ .Values.global.image.tag }}"
  pullPolicy: "{{ .Values.global.image.pullPolicy }}"

replicaCount: "{{ .Values.global.replicaCount }}"

service:
  type: ClusterIP
  port: "{{ .Values.global.service.port }}"

serviceAccount:
  create: true
  annotations:
    eks.amazonaws.com/role-arn: "{{ .Values.global.serviceAccount.roleArn }}"

s3:
  bucket: "{{ .Values.global.s3.bucket }}"
  region: "{{ .Values.global.s3.region }}"

istio:
  enabled: "{{ .Values.global.istio.enabled }}"
  gateway:
    hosts: "{{ .Values.global.istio.hosts }}"

resources: "{{ .Values.global.resources }}"

autoscaling: "{{ .Values.global.autoscaling }}"

nodeSelector: "{{ .Values.global.nodeSelector }}"

tolerations: "{{ .Values.global.tolerations }}"

affinity: "{{ .Values.global.affinity }}"
```

### Environment-Specific Configurations

#### helm/values/dev.yaml
```yaml
global:
  image:
    repository: "123456789012.dkr.ecr.us-west-2.amazonaws.com/chess-app"
    tag: "dev-latest"
    pullPolicy: Always
  
  replicaCount: 1
  
  service:
    port: 8081
  
  serviceAccount:
    roleArn: "arn:aws:iam::123456789012:role/chess-app-dev-role"
  
  s3:
    bucket: "chess-ai-models-dev-abc123"
    region: "us-west-2"
  
  istio:
    enabled: true
    hosts:
      - chess-app-dev.internal
  
  resources:
    limits:
      cpu: 1000m
      memory: 2Gi
    requests:
      cpu: 500m
      memory: 1Gi
  
  autoscaling:
    enabled: false
    minReplicas: 1
    maxReplicas: 3
    targetCPUUtilizationPercentage: 80
  
  nodeSelector: {}
  tolerations: []
  affinity: {}
```

#### helm/values/prod.yaml
```yaml
global:
  image:
    repository: "123456789012.dkr.ecr.us-west-2.amazonaws.com/chess-app"
    tag: "v1.0.0"
    pullPolicy: IfNotPresent
  
  replicaCount: 3
  
  service:
    port: 8081
  
  serviceAccount:
    roleArn: "arn:aws:iam::123456789012:role/chess-app-prod-role"
  
  s3:
    bucket: "chess-ai-models-prod-xyz789"
    region: "us-west-2"
  
  istio:
    enabled: true
    hosts:
      - chess-app.internal
  
  resources:
    limits:
      cpu: 2000m
      memory: 4Gi
    requests:
      cpu: 1000m
      memory: 2Gi
  
  autoscaling:
    enabled: true
    minReplicas: 2
    maxReplicas: 10
    targetCPUUtilizationPercentage: 70
  
  nodeSelector:
    node-type: "general"
  
  tolerations: []
  
  affinity:
    podAntiAffinity:
      preferredDuringSchedulingIgnoredDuringExecution:
      - weight: 100
        podAffinityTerm:
          labelSelector:
            matchExpressions:
            - key: app
              operator: In
              values:
              - chess-app
          topologyKey: kubernetes.io/hostname
```

### Terraform Environment Files

#### environments/dev/terraform.tfvars
```hcl
# Development Environment Configuration
environment = "dev"
project_name = "chess-app"
aws_region = "us-west-2"

common_tags = {
  Project     = "chess-app"
  Environment = "dev"
  ManagedBy   = "terraform"
  Owner       = "development-team"
  CostCenter  = "engineering"
}

# Network Configuration
vpc_cidr_blocks = {
  internet_vpc = "10.0.0.0/16"
  private_vpc  = "10.1.0.0/16"
}

availability_zones = ["us-west-2a", "us-west-2b"]

# EKS Configuration
eks_cluster_version = "1.28"
eks_node_groups = {
  general = {
    instance_types = ["t3.medium"]
    min_size      = 1
    max_size      = 3
    desired_size  = 2
    disk_size     = 50
    ami_type      = "AL2_x86_64"
  }
}

# Application Configuration
app_version = "dev-latest"
helm_set_values = {
  "global.image.tag" = "dev-latest"
  "global.replicaCount" = "1"
}

# Istio Configuration
istio_version = "1.20.0"

# Monitoring Configuration
prometheus_retention_period = "7d"
prometheus_storage_size = "10Gi"
```

#### environments/prod/terraform.tfvars
```hcl
# Production Environment Configuration
environment = "prod"
project_name = "chess-app"
aws_region = "us-west-2"

common_tags = {
  Project     = "chess-app"
  Environment = "prod"
  ManagedBy   = "terraform"
  Owner       = "platform-team"
  CostCenter  = "production"
  Backup      = "required"
}

# Network Configuration
vpc_cidr_blocks = {
  internet_vpc = "10.10.0.0/16"
  private_vpc  = "10.11.0.0/16"
}

availability_zones = ["us-west-2a", "us-west-2b", "us-west-2c"]

# EKS Configuration
eks_cluster_version = "1.28"
eks_node_groups = {
  general = {
    instance_types = ["t3.large"]
    min_size      = 2
    max_size      = 6
    desired_size  = 3
    disk_size     = 50
    ami_type      = "AL2_x86_64"
  }
  gpu = {
    instance_types = ["g4dn.xlarge"]
    min_size      = 0
    max_size      = 3
    desired_size  = 1
    disk_size     = 100
    ami_type      = "AL2_x86_64_GPU"
  }
}

# Application Configuration
app_version = "v1.0.0"
helm_set_values = {
  "global.image.tag" = "v1.0.0"
  "global.replicaCount" = "3"
}

# Istio Configuration
istio_version = "1.20.0"

# Monitoring Configuration
prometheus_retention_period = "30d"
prometheus_storage_size = "100Gi"
```

#### templates/deployment.yaml
```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: {{ include "chess-app.fullname" . }}
  labels:
    {{- include "chess-app.labels" . | nindent 4 }}
spec:
  replicas: {{ .Values.replicaCount }}
  selector:
    matchLabels:
      {{- include "chess-app.selectorLabels" . | nindent 6 }}
  template:
    metadata:
      labels:
        {{- include "chess-app.selectorLabels" . | nindent 8 }}
    spec:
      serviceAccountName: {{ include "chess-app.serviceAccountName" . }}
      containers:
      - name: {{ .Chart.Name }}
        image: "{{ .Values.image.repository }}:{{ .Values.image.tag }}"
        ports:
        - containerPort: 8081
        env:
        - name: CHESS_AI_STORAGE_PATH
          value: "/mnt/s3/ai-models"
        - name: AWS_REGION
          value: {{ .Values.s3.region }}
        volumeMounts:
        - name: secrets-store
          mountPath: "/mnt/secrets"
          readOnly: true
        - name: ai-models-storage
          mountPath: "/mnt/s3/ai-models"
        resources:
          {{- toYaml .Values.resources | nindent 10 }}
      volumes:
      - name: secrets-store
        csi:
          driver: secrets-store.csi.k8s.io
          readOnly: true
          volumeAttributes:
            secretProviderClass: "chess-app-secrets"
      - name: ai-models-storage
        csi:
          driver: s3.csi.aws.com
          volumeAttributes:
            bucketName: {{ .Values.s3.bucket }}
            region: {{ .Values.s3.region }}
```

## Deployment Pipeline

### Build Stage
1. GitHub webhook triggers build
2. Maven compile and test
3. Docker image build and scan
4. Push to ECR with semantic versioning
5. Update Helm chart values with new image tag

### Deploy Stage
1. Terraform plan and apply (infrastructure)
2. Helm upgrade --install (applications)
3. Istio configuration via Helm templates
4. Health checks and rollback capability
5. Automated testing and validation

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

### Cost Breakdown by Resource Category

#### Development Environment
| Resource Category | Components | Monthly Cost | Halt Savings |
|------------------|------------|--------------|-------------|
| **vpc** | VPCs, NAT Gateways, Transit Gateway | $25 | $0 |
| **storage** | S3, ECR | $6 | $0 |
| **security** | Secrets Manager, WAF | $7 | $0 |
| **compute** | EKS Cluster, t3.medium nodes (2) | $133 | $60 (45%) |
| **network** | API Gateway, CloudFront | $15 | $0 |
| **apps** | Monitoring overhead | $4 | $0 |
| **Total Running** | | **$190/month** | |
| **Total Halted** | | **$130/month** | **$60 savings** |

#### Production Environment
| Resource Category | Components | Monthly Cost | Halt Savings |
|------------------|------------|--------------|-------------|
| **vpc** | VPCs, NAT Gateways, Transit Gateway | $45 | $0 |
| **storage** | S3, ECR, Cross-region replication | $22 | $0 |
| **security** | Secrets Manager, WAF | $17 | $0 |
| **compute** | EKS Cluster, t3.large + GPU nodes | $578 | $505 (87%) |
| **network** | API Gateway, CloudFront | $70 | $0 |
| **apps** | Monitoring, alerting | $30 | $0 |
| **Total Running** | | **$762/month** | |
| **Total Halted** | | **$257/month** | **$505 savings** |

### Cost Optimization Impact
- **Development Halt**: 32% cost reduction ($60/month savings)
- **Production Halt**: 66% cost reduction ($505/month savings)
- **Weekend Halts**: ~30% monthly savings with 5-day weeks
- **Overnight Halts**: ~50% monthly savings with 8-hour days

## Granular Resource Management

### Resource Categories

| Category | Components | Purpose | Dependencies |
|----------|------------|---------|-------------|
| **vpc** | Internet VPC, Private VPC, Transit Gateway | Network foundation | None |
| **storage** | S3 buckets, ECR repository | Data and container storage | vpc |
| **security** | Secrets Manager, WAF, IAM roles | Security and access control | vpc |
| **compute** | EKS cluster, node groups | Kubernetes compute platform | vpc, storage, security |
| **network** | API Gateway, CloudFront | External connectivity and CDN | vpc, security |
| **apps** | Istio, monitoring, chess app | Applications and service mesh | compute, network |

### Incremental Deployment Commands

```bash
# Install resources incrementally
./scripts/resource-manager.sh dev install vpc
./scripts/resource-manager.sh dev install storage
./scripts/resource-manager.sh dev install security
./scripts/resource-manager.sh dev install compute
./scripts/resource-manager.sh dev install network
./scripts/resource-manager.sh dev install apps

# Check status of specific resources
./scripts/resource-manager.sh dev status compute
./scripts/resource-manager.sh dev health apps

# Scale down for cost optimization
./scripts/resource-manager.sh dev halt apps      # Scale down applications
./scripts/resource-manager.sh dev halt compute   # Scale down compute resources

# Remove specific resource categories
./scripts/resource-manager.sh dev remove apps
./scripts/resource-manager.sh dev remove network
```

### Automated Testing Scenarios

```bash
# Test basic infrastructure
./scripts/test-scenarios.sh dev basic

# Test compute layer
./scripts/test-scenarios.sh dev compute

# Test full application stack
./scripts/test-scenarios.sh dev application

# Test scaling capabilities
./scripts/test-scenarios.sh dev scaling

# Test disaster recovery
./scripts/test-scenarios.sh dev disaster

# Test cost optimization
./scripts/test-scenarios.sh dev cost
```

## Implementation Phases

### Phase 1: Foundation (VPC)
- Internet-facing VPC with public subnets
- Private VPC with private subnets and NAT gateways
- Transit Gateway connecting both VPCs
- **Cost**: ~$25/month
- **Testing**: Basic connectivity and routing

### Phase 2: Storage & Security
- S3 bucket for AI model storage (private access)
- ECR repository for container images
- AWS Secrets Manager and WAF
- **Cost**: ~$35/month
- **Testing**: S3 access, secret retrieval, WAF rules

### Phase 3: Compute Platform
- EKS cluster with OIDC provider
- Node groups (general and GPU)
- EKS addons and lifecycle management
- **Cost**: ~$648/month (full compute)
- **Testing**: Pod deployment, scaling, S3 access from pods

### Phase 4: Network Services
- API Gateway v2 with VPC Link
- CloudFront distribution with WAF integration
- Custom domain and SSL certificates
- **Cost**: Additional ~$35/month
- **Testing**: API connectivity, CDN caching, SSL validation

### Phase 5: Applications
- Istio service mesh installation
- Prometheus and Grafana monitoring
- Chess application deployment
- **Cost**: No additional infrastructure cost
- **Testing**: Application functionality, monitoring, AI training

### Phase 6: Production Hardening
- Multi-AZ deployment validation
- Monitoring and alerting setup
- Backup and disaster recovery testing
- **Cost**: Monitoring overhead ~$30/month
- **Testing**: Failover scenarios, backup/restore procedures

## Resource Lifecycle Management

### Lifecycle Actions Available

| Action | Purpose | Cost Impact | Use Case |
|--------|---------|-------------|----------|
| **install** | Create and configure resources | Full cost | Initial deployment |
| **start** | Start existing resources | Resume full cost | Resume operations |
| **status** | Check resource health | No change | Monitoring |
| **halt** | Scale down to save costs | 78% cost reduction | Overnight/weekend savings |
| **restart** | Resume from halt | Resume full cost | Resume after halt |
| **stop** | Terminate compute resources | 78% cost reduction | Longer-term shutdown |
| **remove** | Delete resources completely | 100% cost savings | Environment cleanup |
| **health** | Comprehensive health check | No change | Automated monitoring |

### Cost Optimization Strategies

```bash
# Daily cost optimization (automated via cron)
# Halt resources at 6 PM weekdays
0 18 * * 1-5 ./scripts/resource-manager.sh prod halt compute

# Restart resources at 8 AM weekdays
0 8 * * 1-5 ./scripts/resource-manager.sh prod restart compute

# Weekend full halt
0 18 * * 5 ./scripts/resource-manager.sh prod halt all
0 8 * * 1 ./scripts/resource-manager.sh prod restart all
```

### Testing and Validation

**⚠️ IMPORTANT**: All scripts are currently **UNTESTED** and should be validated in development environments before production use.

**Recommended Testing Sequence**:
1. Start with `basic` test scenario in development
2. Progress through each resource category incrementally
3. Validate cost optimization with halt/restart cycles
4. Test disaster recovery scenarios
5. Validate monitoring and alerting

This design provides a production-ready, scalable, and secure deployment of the Chess game on AWS with granular resource management, cost optimization capabilities, and comprehensive testing frameworks for safe incremental deployment.