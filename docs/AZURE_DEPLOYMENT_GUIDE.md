# Azure Deployment Guide for Chess AI Application

## Overview

This document provides a comprehensive guide for deploying the Chess AI application to Microsoft Azure, based on the existing AWS infrastructure architecture. The Azure deployment maintains the same architectural patterns while leveraging Azure-native services for optimal performance and cost efficiency.

## Architecture Overview

### Current AWS Architecture Analysis

The existing AWS infrastructure includes:

- **Container Orchestration**: Amazon EKS (Kubernetes 1.28)
- **Container Registry**: Amazon ECR
- **Storage**: Amazon S3 with intelligent tiering
- **API Gateway**: AWS API Gateway v2 with VPC Link
- **CDN**: CloudFront distribution
- **Security**: AWS WAF v2, Secrets Manager
- **Networking**: Dual VPC setup (Internet + Private) with Transit Gateway
- **Service Mesh**: Istio 1.20.0
- **Monitoring**: Prometheus, Grafana, Jaeger
- **Load Balancing**: AWS Load Balancer Controller

### Azure Equivalent Architecture

| AWS Service | Azure Equivalent | Purpose |
|-------------|------------------|---------|
| Amazon EKS | Azure Kubernetes Service (AKS) | Container orchestration |
| Amazon ECR | Azure Container Registry (ACR) | Container image storage |
| Amazon S3 | Azure Blob Storage | AI model storage |
| API Gateway | Azure API Management | API management and routing |
| CloudFront | Azure CDN | Content delivery network |
| AWS WAF | Azure Application Gateway WAF | Web application firewall |
| Secrets Manager | Azure Key Vault | Secret management |
| VPC | Azure Virtual Network (VNet) | Network isolation |
| Transit Gateway | Azure Virtual WAN | Network connectivity |
| CloudWatch | Azure Monitor | Monitoring and logging |

## Prerequisites

### Required Tools

- Azure CLI 2.50.0 or later
- Terraform 1.5.0 or later
- Helm 3.12.0 or later
- kubectl 1.28.0 or later
- Docker 24.0.0 or later

### Azure Resources Required

- Azure subscription with appropriate permissions
- Resource Group for the deployment
- Service Principal with Contributor role
- Azure Container Registry
- Azure Key Vault

## Infrastructure Components

### 1. Network Architecture

#### Virtual Networks
- **Hub VNet**: Central connectivity point (10.0.0.0/16)
- **Spoke VNet**: Application-specific network (10.1.0.0/16)
- **Peering**: VNet peering between Hub and Spoke
- **Network Security Groups**: Layer 4 security controls

#### Subnet Configuration
```
Hub VNet (10.0.0.0/16):
├── GatewaySubnet (10.0.1.0/24) - VPN/ExpressRoute
├── ManagementSubnet (10.0.2.0/24) - Bastion, monitoring
└── SharedServicesSubnet (10.0.3.0/24) - Shared resources

Spoke VNet (10.1.0.0/16):
├── AKSSubnet (10.1.1.0/24) - AKS node pool
├── PrivateEndpointSubnet (10.1.2.0/24) - Private endpoints
└── ApplicationGatewaySubnet (10.1.3.0/24) - Application Gateway
```

### 2. Container Orchestration

#### Azure Kubernetes Service (AKS)
- **Version**: Kubernetes 1.28
- **Node Pools**:
  - **System Pool**: 2-3 nodes (Standard_D2s_v3)
  - **User Pool**: 2-6 nodes (Standard_D4s_v3)
  - **GPU Pool**: 0-3 nodes (Standard_NC6s_v3) - for AI workloads
- **Auto-scaling**: Cluster autoscaler enabled
- **Network Plugin**: Azure CNI
- **Load Balancer**: Azure Load Balancer Standard

#### Container Registry
- **Azure Container Registry (ACR)**: Premium SKU
- **Features**: Geo-replication, vulnerability scanning
- **Authentication**: Managed identity integration

### 3. Storage and Data

#### Azure Blob Storage
- **Storage Account**: Standard LRS with hot tier
- **Lifecycle Management**: Automatic tiering to cool/archive
- **Security**: Private endpoints, encryption at rest
- **Access**: Managed identity-based authentication

#### Azure Key Vault
- **Purpose**: Secret and certificate management
- **Integration**: AKS CSI driver for secret injection
- **Access**: Managed identity with RBAC

### 4. API Management

#### Azure API Management
- **Tier**: Developer or Standard
- **Features**: Rate limiting, authentication, monitoring
- **Backend**: AKS service via private endpoint
- **Policies**: CORS, caching, transformation

### 5. Security and Monitoring

#### Security Components
- **Azure Application Gateway**: WAF v2 with OWASP rules
- **Azure Security Center**: Threat protection
- **Azure Sentinel**: SIEM capabilities
- **Network Security Groups**: Micro-segmentation

#### Monitoring Stack
- **Azure Monitor**: Logs and metrics collection
- **Application Insights**: Application performance monitoring
- **Prometheus + Grafana**: Custom metrics and dashboards
- **Jaeger**: Distributed tracing

## Deployment Guide

### Phase 1: Infrastructure Setup

#### 1.1 Prerequisites Setup

```bash
# Login to Azure
az login

# Set subscription
az account set --subscription "your-subscription-id"

# Create resource group
az group create --name chess-app-rg --location "East US"

# Create service principal
az ad sp create-for-rbac --name "chess-app-sp" --role Contributor --scopes /subscriptions/your-subscription-id/resourceGroups/chess-app-rg
```

#### 1.2 Terraform Configuration

Create `infra/azure/terraform/main.tf`:

```hcl
terraform {
  required_providers {
    azurerm = {
      source  = "hashicorp/azurerm"
      version = "~> 3.80"
    }
    helm = {
      source  = "hashicorp/helm"
      version = "~> 2.12"
    }
    kubernetes = {
      source  = "hashicorp/kubernetes"
      version = "~> 2.24"
    }
  }
}

provider "azurerm" {
  features {}
}

provider "helm" {
  kubernetes {
    host                   = azurerm_kubernetes_cluster.main.kube_config.0.host
    client_certificate     = base64decode(azurerm_kubernetes_cluster.main.kube_config.0.client_certificate)
    client_key             = base64decode(azurerm_kubernetes_cluster.main.kube_config.0.client_key)
    cluster_ca_certificate = base64decode(azurerm_kubernetes_cluster.main.kube_config.0.cluster_ca_certificate)
  }
}

provider "kubernetes" {
  host                   = azurerm_kubernetes_cluster.main.kube_config.0.host
  client_certificate     = base64decode(azurerm_kubernetes_cluster.main.kube_config.0.client_certificate)
  client_key             = base64decode(azurerm_kubernetes_cluster.main.kube_config.0.client_key)
  cluster_ca_certificate = base64decode(azurerm_kubernetes_cluster.main.kube_config.0.cluster_ca_certificate)
}
```

#### 1.3 Network Infrastructure

Create `infra/azure/terraform/modules/network/main.tf`:

```hcl
# Hub Virtual Network
resource "azurerm_virtual_network" "hub" {
  name                = "${var.project_name}-${var.environment}-hub-vnet"
  address_space       = ["10.0.0.0/16"]
  location            = var.location
  resource_group_name = var.resource_group_name
}

# Spoke Virtual Network
resource "azurerm_virtual_network" "spoke" {
  name                = "${var.project_name}-${var.environment}-spoke-vnet"
  address_space       = ["10.1.0.0/16"]
  location            = var.location
  resource_group_name = var.resource_group_name
}

# VNet Peering
resource "azurerm_virtual_network_peering" "hub_to_spoke" {
  name                      = "hub-to-spoke"
  resource_group_name       = var.resource_group_name
  virtual_network_name      = azurerm_virtual_network.hub.name
  remote_virtual_network_id = azurerm_virtual_network.spoke.id
}

resource "azurerm_virtual_network_peering" "spoke_to_hub" {
  name                      = "spoke-to-hub"
  resource_group_name       = var.resource_group_name
  virtual_network_name      = azurerm_virtual_network.spoke.name
  remote_virtual_network_id = azurerm_virtual_network.hub.id
}
```

#### 1.4 AKS Cluster

Create `infra/azure/terraform/modules/aks/main.tf`:

```hcl
resource "azurerm_kubernetes_cluster" "main" {
  name                = "${var.project_name}-${var.environment}-aks"
  location            = var.location
  resource_group_name = var.resource_group_name
  dns_prefix          = "${var.project_name}-${var.environment}"
  kubernetes_version  = var.kubernetes_version

  default_node_pool {
    name                = "system"
    node_count          = 2
    vm_size             = "Standard_D2s_v3"
    vnet_subnet_id      = var.aks_subnet_id
    enable_auto_scaling = true
    min_count           = 2
    max_count           = 3
  }

  identity {
    type = "SystemAssigned"
  }

  network_profile {
    network_plugin    = "azure"
    load_balancer_sku = "standard"
    service_cidr      = "10.2.0.0/24"
    dns_service_ip    = "10.2.0.10"
  }

  addon_profile {
    azure_policy {
      enabled = true
    }
    oms_agent {
      enabled                    = true
      log_analytics_workspace_id = var.log_analytics_workspace_id
    }
  }
}

# User node pool for applications
resource "azurerm_kubernetes_cluster_node_pool" "user" {
  name                  = "user"
  kubernetes_cluster_id = azurerm_kubernetes_cluster.main.id
  vm_size               = "Standard_D4s_v3"
  node_count            = 2
  enable_auto_scaling   = true
  min_count             = 2
  max_count             = 6
  vnet_subnet_id        = var.aks_subnet_id
}

# GPU node pool for AI workloads
resource "azurerm_kubernetes_cluster_node_pool" "gpu" {
  name                  = "gpu"
  kubernetes_cluster_id = azurerm_kubernetes_cluster.main.id
  vm_size               = "Standard_NC6s_v3"
  node_count            = 0
  enable_auto_scaling   = true
  min_count             = 0
  max_count             = 3
  vnet_subnet_id        = var.aks_subnet_id
}
```

### Phase 2: Application Deployment

#### 2.1 Container Registry Setup

```bash
# Create Azure Container Registry
az acr create --resource-group chess-app-rg --name chessappacr --sku Premium

# Enable admin user
az acr update -n chessappacr --admin-enabled true

# Build and push image
docker build -t chessappacr.azurecr.io/chess-app:latest .
docker push chessappacr.azurecr.io/chess-app:latest
```

#### 2.2 Helm Chart Adaptation

Update `infra/helm/chess-app/values.yaml` for Azure:

```yaml
global:
  image:
    repository: "chessappacr.azurecr.io/chess-app"
    tag: "latest"
    pullPolicy: IfNotPresent
  
  service:
    type: ClusterIP
    port: 8081
  
  serviceAccount:
    create: true
    annotations:
      azure.workload.identity/client-id: "your-managed-identity-client-id"
  
  storage:
    type: "azure-blob"
    accountName: "chessappstorage"
    containerName: "ai-models"
  
  resources:
    limits:
      cpu: 2000m
      memory: 4Gi
    requests:
      cpu: 1000m
      memory: 2Gi
```

#### 2.3 Istio Service Mesh

```bash
# Install Istio
helm repo add istio https://istio-release.storage.googleapis.com/charts
helm repo update

# Install Istio base
helm install istio-base istio/base -n istio-system --create-namespace

# Install Istiod
helm install istiod istio/istiod -n istio-system

# Install Istio gateway
helm install istio-gateway istio/gateway -n istio-ingress --create-namespace
```

#### 2.4 Monitoring Stack

```bash
# Install Prometheus and Grafana
helm repo add prometheus-community https://prometheus-community.github.io/helm-charts
helm repo update

helm install prometheus prometheus-community/kube-prometheus-stack \
  --namespace monitoring \
  --create-namespace \
  --set prometheus.prometheusSpec.retention=15d \
  --set grafana.adminPassword=admin

# Install Jaeger
helm repo add jaegertracing https://jaegertracing.github.io/helm-charts
helm install jaeger jaegertracing/jaeger -n monitoring
```

### Phase 3: Security and Networking

#### 3.1 Azure Key Vault Integration

```bash
# Create Key Vault
az keyvault create --name chess-app-kv --resource-group chess-app-rg --location "East US"

# Create secrets
az keyvault secret set --vault-name chess-app-kv --name "database-password" --value "your-password"
az keyvault secret set --vault-name chess-app-kv --name "api-key" --value "your-api-key"
```

#### 3.2 Application Gateway Setup

Create `infra/azure/terraform/modules/application-gateway/main.tf`:

```hcl
resource "azurerm_application_gateway" "main" {
  name                = "${var.project_name}-${var.environment}-agw"
  resource_group_name = var.resource_group_name
  location            = var.location

  sku {
    name     = "WAF_v2"
    tier     = "WAF_v2"
    capacity = 2
  }

  gateway_ip_configuration {
    name      = "gateway-ip-configuration"
    subnet_id = var.application_gateway_subnet_id
  }

  frontend_port {
    name = "http"
    port = 80
  }

  frontend_port {
    name = "https"
    port = 443
  }

  frontend_ip_configuration {
    name                 = "frontend-ip"
    public_ip_address_id = azurerm_public_ip.main.id
  }

  backend_address_pool {
    name  = "aks-backend-pool"
    fqdns = [var.aks_fqdn]
  }

  backend_http_settings {
    name                  = "aks-http-settings"
    cookie_based_affinity = "Disabled"
    port                  = 80
    protocol              = "Http"
    request_timeout       = 60
  }

  http_listener {
    name                           = "http-listener"
    frontend_ip_configuration_name = "frontend-ip"
    frontend_port_name             = "http"
    protocol                       = "Http"
  }

  request_routing_rule {
    name                       = "aks-routing-rule"
    rule_type                  = "Basic"
    http_listener_name         = "http-listener"
    backend_address_pool_name  = "aks-backend-pool"
    backend_http_settings_name = "aks-http-settings"
    priority                   = 100
  }

  waf_configuration {
    enabled          = true
    firewall_mode    = "Prevention"
    rule_set_type    = "OWASP"
    rule_set_version = "3.2"
  }
}
```

### Phase 4: CI/CD Pipeline

#### 4.1 Azure DevOps Pipeline

Create `azure-pipelines.yml`:

```yaml
trigger:
  branches:
    include:
    - main
    - develop

variables:
  azureServiceConnection: 'chess-app-connection'
  imageRepository: 'chess-app'
  containerRegistry: 'chessappacr.azurecr.io'
  dockerfilePath: '$(Build.SourcesDirectory)/infra/Dockerfile'
  tag: '$(Build.BuildId)'

stages:
- stage: Build
  displayName: Build and push image
  jobs:
  - job: Build
    displayName: Build
    pool:
      vmImage: 'ubuntu-latest'
    steps:
    - task: Docker@2
      displayName: Build and push image
      inputs:
        command: buildAndPush
        repository: $(imageRepository)
        dockerfile: $(dockerfilePath)
        containerRegistry: $(azureServiceConnection)
        tags: |
          $(tag)
          latest

- stage: Deploy
  displayName: Deploy to AKS
  dependsOn: Build
  condition: and(succeeded(), eq(variables['Build.SourceBranch'], 'refs/heads/main'))
  jobs:
  - deployment: Deploy
    displayName: Deploy
    pool:
      vmImage: 'ubuntu-latest'
    environment: 'production'
    strategy:
      runOnce:
        deploy:
          steps:
          - task: KubernetesManifest@0
            displayName: Deploy to AKS
            inputs:
              action: deploy
              kubernetesServiceConnection: 'chess-app-aks'
              namespace: 'chess-app'
              manifests: |
                $(Pipeline.Workspace)/manifests/*
```

## Cost Optimization

### 1. Resource Sizing

- **Development**: Use smaller VM sizes (Standard_B2s)
- **Production**: Right-size based on actual usage patterns
- **GPU Nodes**: Scale to zero when not in use

### 2. Storage Optimization

- **Blob Storage**: Use lifecycle management for AI models
- **Premium Storage**: Only for high-performance requirements
- **Archive Tier**: For long-term model storage

### 3. Network Optimization

- **CDN**: Cache static assets effectively
- **Private Endpoints**: Reduce data transfer costs
- **VNet Peering**: More cost-effective than VPN Gateway

## Monitoring and Alerting

### 1. Azure Monitor

- **Application Insights**: Application performance monitoring
- **Log Analytics**: Centralized logging
- **Metrics**: Resource utilization tracking

### 2. Custom Dashboards

- **Grafana**: Custom metrics and visualizations
- **Prometheus**: Kubernetes metrics collection
- **Jaeger**: Distributed tracing

### 3. Alerting Rules

- **High CPU/Memory usage**
- **Pod restart frequency**
- **API response time degradation**
- **Storage capacity warnings**

## Security Best Practices

### 1. Network Security

- **Network Security Groups**: Micro-segmentation
- **Private Endpoints**: Secure service access
- **Application Gateway WAF**: Web application protection

### 2. Identity and Access

- **Managed Identities**: No secrets in code
- **RBAC**: Principle of least privilege
- **Azure AD Integration**: Centralized authentication

### 3. Data Protection

- **Encryption at Rest**: All storage encrypted
- **Encryption in Transit**: TLS 1.2+
- **Key Vault**: Secure secret management

## Disaster Recovery

### 1. Backup Strategy

- **AKS Cluster**: Regular configuration backups
- **Blob Storage**: Geo-redundant storage
- **Key Vault**: Soft delete enabled

### 2. Multi-Region Deployment

- **Primary Region**: East US
- **Secondary Region**: West US
- **Traffic Manager**: Automatic failover

### 3. Recovery Procedures

- **RTO**: 4 hours
- **RPO**: 1 hour
- **Testing**: Quarterly DR drills

## Migration from AWS

### 1. Data Migration

- **S3 to Blob Storage**: Azure Data Factory
- **ECR to ACR**: Container image migration
- **Secrets**: Manual migration to Key Vault

### 2. Application Migration

- **Kubernetes Manifests**: Minimal changes required
- **Helm Charts**: Update image repositories
- **Service Discovery**: Update DNS configurations

### 3. Network Migration

- **VPC to VNet**: Recreate network topology
- **Security Groups**: Convert to NSGs
- **Load Balancers**: Migrate to Application Gateway

## Troubleshooting

### Common Issues

1. **Image Pull Failures**: Check ACR authentication
2. **Network Connectivity**: Verify NSG rules
3. **Pod Scheduling**: Check node capacity and taints
4. **Service Discovery**: Verify DNS configuration

### Debugging Tools

- **kubectl**: Kubernetes debugging
- **Azure CLI**: Resource management
- **Azure Portal**: Visual troubleshooting
- **Application Insights**: Application debugging

## Conclusion

This Azure deployment guide provides a comprehensive approach to migrating and deploying the Chess AI application on Microsoft Azure. The architecture maintains the same high availability, security, and scalability characteristics as the original AWS deployment while leveraging Azure-native services for optimal performance and cost efficiency.

The modular Terraform configuration allows for easy customization and scaling, while the Helm charts ensure consistent application deployment across environments. The monitoring and security implementations provide enterprise-grade observability and protection for the application.
