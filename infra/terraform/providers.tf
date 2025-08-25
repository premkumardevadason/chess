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