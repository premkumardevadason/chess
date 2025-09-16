# Azure Terraform Modules Reference

## Overview

This document provides detailed reference for Azure Terraform modules that mirror the functionality of the existing AWS infrastructure. Each module is designed to be reusable and follows Azure best practices.

## Module Structure

```
infra/azure/terraform/
├── main.tf
├── variables.tf
├── outputs.tf
├── providers.tf
├── environments/
│   ├── dev/
│   │   └── terraform.tfvars
│   └── prod/
│       └── terraform.tfvars
└── modules/
    ├── aks/
    ├── container-registry/
    ├── storage/
    ├── key-vault/
    ├── api-management/
    ├── cdn/
    ├── application-gateway/
    ├── monitoring/
    ├── networking/
    └── helm-chess-app/
```

## Core Modules

### 1. AKS Module (`modules/aks/`)

**Purpose**: Deploy and configure Azure Kubernetes Service cluster

**Key Features**:
- Multiple node pools (system, user, GPU)
- Auto-scaling configuration
- Network integration with Azure CNI
- Managed identity integration
- Add-ons: Azure Policy, OMS Agent

**Variables**:
```hcl
variable "cluster_name" {
  description = "Name of the AKS cluster"
  type        = string
}

variable "location" {
  description = "Azure region for deployment"
  type        = string
}

variable "resource_group_name" {
  description = "Resource group name"
  type        = string
}

variable "kubernetes_version" {
  description = "Kubernetes version"
  type        = string
  default     = "1.28"
}

variable "node_pools" {
  description = "Node pool configurations"
  type = map(object({
    vm_size             = string
    node_count          = number
    min_count           = number
    max_count           = number
    enable_auto_scaling = bool
    node_taints         = list(string)
  }))
}
```

**Outputs**:
```hcl
output "cluster_id" {
  description = "AKS cluster ID"
  value       = azurerm_kubernetes_cluster.main.id
}

output "cluster_fqdn" {
  description = "AKS cluster FQDN"
  value       = azurerm_kubernetes_cluster.main.fqdn
}

output "kube_config" {
  description = "Kubernetes configuration"
  value       = azurerm_kubernetes_cluster.main.kube_config_raw
  sensitive   = true
}
```

### 2. Container Registry Module (`modules/container-registry/`)

**Purpose**: Deploy Azure Container Registry with security features

**Key Features**:
- Premium SKU for geo-replication
- Vulnerability scanning enabled
- Managed identity authentication
- Private endpoint support

**Variables**:
```hcl
variable "registry_name" {
  description = "Name of the container registry"
  type        = string
}

variable "sku" {
  description = "ACR SKU"
  type        = string
  default     = "Premium"
}

variable "admin_enabled" {
  description = "Enable admin user"
  type        = bool
  default     = false
}

variable "georeplications" {
  description = "List of georeplication locations"
  type        = list(string)
  default     = []
}
```

### 3. Storage Module (`modules/storage/`)

**Purpose**: Deploy Azure Blob Storage for AI model storage

**Key Features**:
- Lifecycle management policies
- Private endpoint configuration
- Encryption at rest
- Access tier optimization

**Variables**:
```hcl
variable "storage_account_name" {
  description = "Name of the storage account"
  type        = string
}

variable "container_name" {
  description = "Name of the blob container"
  type        = string
  default     = "ai-models"
}

variable "access_tier" {
  description = "Storage access tier"
  type        = string
  default     = "Hot"
}

variable "lifecycle_rules" {
  description = "Lifecycle management rules"
  type = list(object({
    name    = string
    enabled = bool
    filters = object({
      prefix_match = list(string)
    })
    actions = object({
      base_blob = object({
        tier_to_cool_after_days    = number
        tier_to_archive_after_days = number
        delete_after_days          = number
      })
    })
  }))
  default = []
}
```

### 4. Key Vault Module (`modules/key-vault/`)

**Purpose**: Deploy Azure Key Vault for secret management

**Key Features**:
- Soft delete enabled
- Purge protection
- Access policies and RBAC
- Private endpoint support

**Variables**:
```hcl
variable "vault_name" {
  description = "Name of the Key Vault"
  type        = string
}

variable "sku_name" {
  description = "Key Vault SKU"
  type        = string
  default     = "standard"
}

variable "enabled_for_disk_encryption" {
  description = "Enable for disk encryption"
  type        = bool
  default     = true
}

variable "purge_protection_enabled" {
  description = "Enable purge protection"
  type        = bool
  default     = true
}
```

### 5. API Management Module (`modules/api-management/`)

**Purpose**: Deploy Azure API Management for API gateway functionality

**Key Features**:
- Developer or Standard tier
- Custom domain configuration
- Backend service integration
- Rate limiting and policies

**Variables**:
```hcl
variable "api_management_name" {
  description = "Name of the API Management instance"
  type        = string
}

variable "sku_name" {
  description = "API Management SKU"
  type        = string
  default     = "Developer_1"
}

variable "publisher_name" {
  description = "Publisher name"
  type        = string
}

variable "publisher_email" {
  description = "Publisher email"
  type        = string
}

variable "backend_url" {
  description = "Backend service URL"
  type        = string
}
```

### 6. CDN Module (`modules/cdn/`)

**Purpose**: Deploy Azure CDN for content delivery

**Key Features**:
- Microsoft CDN profile
- Custom domain support
- Caching rules
- HTTPS enforcement

**Variables**:
```hcl
variable "cdn_profile_name" {
  description = "Name of the CDN profile"
  type        = string
}

variable "sku" {
  description = "CDN SKU"
  type        = string
  default     = "Standard_Microsoft"
}

variable "origin_hostname" {
  description = "Origin hostname"
  type        = string
}

variable "custom_domain" {
  description = "Custom domain name"
  type        = string
  default     = null
}
```

### 7. Application Gateway Module (`modules/application-gateway/`)

**Purpose**: Deploy Azure Application Gateway with WAF

**Key Features**:
- WAF v2 with OWASP rules
- SSL termination
- Backend health probes
- URL path-based routing

**Variables**:
```hcl
variable "gateway_name" {
  description = "Name of the Application Gateway"
  type        = string
}

variable "sku_name" {
  description = "Gateway SKU"
  type        = string
  default     = "WAF_v2"
}

variable "sku_tier" {
  description = "Gateway SKU tier"
  type        = string
  default     = "WAF_v2"
}

variable "capacity" {
  description = "Gateway capacity"
  type        = number
  default     = 2
}

variable "backend_pools" {
  description = "Backend pool configurations"
  type = list(object({
    name  = string
    fqdns = list(string)
  }))
}
```

### 8. Monitoring Module (`modules/monitoring/`)

**Purpose**: Deploy Azure Monitor and Log Analytics workspace

**Key Features**:
- Log Analytics workspace
- Application Insights
- Diagnostic settings
- Alert rules

**Variables**:
```hcl
variable "workspace_name" {
  description = "Name of the Log Analytics workspace"
  type        = string
}

variable "application_insights_name" {
  description = "Name of the Application Insights instance"
  type        = string
}

variable "retention_days" {
  description = "Log retention in days"
  type        = number
  default     = 30
}

variable "alert_rules" {
  description = "Alert rule configurations"
  type = list(object({
    name        = string
    description = string
    severity    = number
    frequency   = string
    window_size = string
    criteria = object({
      metric_name      = string
      operator         = string
      threshold        = number
      aggregation_type = string
    })
  }))
  default = []
}
```

### 9. Networking Module (`modules/networking/`)

**Purpose**: Deploy virtual networks and connectivity

**Key Features**:
- Hub and spoke architecture
- VNet peering
- Network Security Groups
- Private endpoints

**Variables**:
```hcl
variable "hub_vnet_name" {
  description = "Name of the hub VNet"
  type        = string
}

variable "spoke_vnet_name" {
  description = "Name of the spoke VNet"
  type        = string
}

variable "hub_address_space" {
  description = "Hub VNet address space"
  type        = list(string)
  default     = ["10.0.0.0/16"]
}

variable "spoke_address_space" {
  description = "Spoke VNet address space"
  type        = list(string)
  default     = ["10.1.0.0/16"]
}

variable "subnets" {
  description = "Subnet configurations"
  type = map(object({
    address_prefixes = list(string)
    service_endpoints = list(string)
    private_endpoint_network_policies_enabled = bool
  }))
}
```

### 10. Helm Chess App Module (`modules/helm-chess-app/`)

**Purpose**: Deploy chess application via Helm

**Key Features**:
- Helm chart deployment
- Environment-specific values
- Azure-specific configurations
- Resource limits and requests

**Variables**:
```hcl
variable "chart_name" {
  description = "Helm chart name"
  type        = string
  default     = "chess-app"
}

variable "chart_version" {
  description = "Helm chart version"
  type        = string
  default     = "1.0.0"
}

variable "namespace" {
  description = "Kubernetes namespace"
  type        = string
  default     = "chess-app"
}

variable "values" {
  description = "Helm values"
  type        = map(any)
  default     = {}
}

variable "set_values" {
  description = "Additional Helm set values"
  type        = map(string)
  default     = {}
}
```

## Environment Configurations

### Development Environment (`environments/dev/terraform.tfvars`)

```hcl
environment = "dev"
project_name = "chess-app"
location = "East US"

# AKS Configuration
kubernetes_version = "1.28"
node_pools = {
  system = {
    vm_size             = "Standard_B2s"
    node_count          = 1
    min_count           = 1
    max_count           = 2
    enable_auto_scaling = true
    node_taints         = []
  }
  user = {
    vm_size             = "Standard_D2s_v3"
    node_count          = 1
    min_count           = 1
    max_count           = 3
    enable_auto_scaling = true
    node_taints         = []
  }
}

# Storage Configuration
storage_sku = "Standard_LRS"
access_tier = "Hot"

# API Management
api_management_sku = "Developer_1"

# Monitoring
log_retention_days = 7
```

### Production Environment (`environments/prod/terraform.tfvars`)

```hcl
environment = "prod"
project_name = "chess-app"
location = "East US"

# AKS Configuration
kubernetes_version = "1.28"
node_pools = {
  system = {
    vm_size             = "Standard_D2s_v3"
    node_count          = 2
    min_count           = 2
    max_count           = 3
    enable_auto_scaling = true
    node_taints         = ["CriticalAddonsOnly=true:NoSchedule"]
  }
  user = {
    vm_size             = "Standard_D4s_v3"
    node_count          = 3
    min_count           = 3
    max_count           = 10
    enable_auto_scaling = true
    node_taints         = []
  }
  gpu = {
    vm_size             = "Standard_NC6s_v3"
    node_count          = 0
    min_count           = 0
    max_count           = 5
    enable_auto_scaling = true
    node_taints         = ["nvidia.com/gpu=true:NoSchedule"]
  }
}

# Storage Configuration
storage_sku = "Standard_GRS"
access_tier = "Hot"

# API Management
api_management_sku = "Standard_2"

# Monitoring
log_retention_days = 90
```

## Usage Examples

### Deploying Infrastructure

```bash
# Initialize Terraform
cd infra/azure/terraform
terraform init

# Plan deployment
terraform plan -var-file="environments/dev/terraform.tfvars"

# Apply deployment
terraform apply -var-file="environments/dev/terraform.tfvars"
```

### Updating Configuration

```bash
# Update specific module
terraform apply -target=module.aks -var-file="environments/dev/terraform.tfvars"

# Destroy specific resources
terraform destroy -target=module.monitoring -var-file="environments/dev/terraform.tfvars"
```

## Best Practices

### 1. Module Design

- **Single Responsibility**: Each module handles one specific service
- **Reusability**: Modules are environment-agnostic
- **Consistency**: Standardized variable and output naming
- **Documentation**: Comprehensive variable and output descriptions

### 2. State Management

- **Remote State**: Use Azure Storage for state backend
- **State Locking**: Enable state locking to prevent conflicts
- **State Separation**: Separate state files per environment

### 3. Security

- **Least Privilege**: Minimal required permissions
- **Sensitive Variables**: Mark sensitive outputs appropriately
- **Private Endpoints**: Use private connectivity where possible

### 4. Cost Optimization

- **Right-sizing**: Appropriate resource sizes for workload
- **Reserved Instances**: Use for predictable workloads
- **Lifecycle Management**: Automatic resource cleanup

## Troubleshooting

### Common Issues

1. **Provider Version Conflicts**: Ensure consistent provider versions
2. **Resource Dependencies**: Check resource dependencies and timing
3. **Permission Issues**: Verify service principal permissions
4. **Network Connectivity**: Check NSG rules and routing

### Debugging Commands

```bash
# Validate configuration
terraform validate

# Format configuration
terraform fmt -recursive

# Show current state
terraform show

# List resources
terraform state list

# Import existing resources
terraform import azurerm_resource_group.main /subscriptions/.../resourceGroups/...
```

## Conclusion

This module reference provides a comprehensive foundation for deploying the Chess AI application on Azure. Each module is designed to be production-ready with proper security, monitoring, and cost optimization features. The modular approach allows for easy customization and maintenance across different environments.
