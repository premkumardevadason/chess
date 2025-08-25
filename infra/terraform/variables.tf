# variables.tf

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

# Application Configuration
variable "app_namespace" {
  description = "Namespace for chess application"
  type        = string
  default     = "chess-app"
}

variable "app_version" {
  description = "Chess application version/image tag"
  type        = string
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

# Monitoring Configuration
variable "prometheus_retention_period" {
  description = "Prometheus data retention period"
  type        = string
  default     = "15d"
}

variable "prometheus_storage_size" {
  description = "Prometheus storage size"
  type        = string
  default     = "50Gi"
}