# main.tf

# Internet-facing VPC
module "internet_vpc" {
  source = "./modules/internet-vpc"
  
  environment    = var.environment
  project_name   = var.project_name
  vpc_cidr       = var.vpc_cidr_blocks.internet_vpc
  azs           = var.availability_zones
  common_tags   = var.common_tags
}

# Private VPC
module "private_vpc" {
  source = "./modules/private-vpc"
  
  environment    = var.environment
  project_name   = var.project_name
  vpc_cidr       = var.vpc_cidr_blocks.private_vpc
  azs           = var.availability_zones
  common_tags   = var.common_tags
}

# Transit Gateway
module "transit_gateway" {
  source = "./modules/transit-gateway"
  
  environment      = var.environment
  project_name     = var.project_name
  internet_vpc_id  = module.internet_vpc.vpc_id
  private_vpc_id   = module.private_vpc.vpc_id
  common_tags      = var.common_tags
}

# ECR Repository
module "ecr" {
  source = "./modules/ecr"
  
  environment    = var.environment
  project_name   = var.project_name
  common_tags    = var.common_tags
}

# S3 Storage
module "s3" {
  source = "./modules/s3"
  
  environment    = var.environment
  project_name   = var.project_name
  vpc_id         = module.private_vpc.vpc_id
  route_table_ids = module.private_vpc.private_route_table_ids
  common_tags    = var.common_tags
}

# Secrets Manager
module "secrets_manager" {
  source = "./modules/secrets-manager"
  
  environment    = var.environment
  project_name   = var.project_name
  common_tags    = var.common_tags
}

# EKS Cluster
module "eks" {
  source = "./modules/eks"
  
  environment       = var.environment
  project_name      = var.project_name
  vpc_id           = module.private_vpc.vpc_id
  subnet_ids       = module.private_vpc.private_subnet_ids
  cluster_version  = var.eks_cluster_version
  node_groups      = var.eks_node_groups
  common_tags      = var.common_tags
}

# WAF
module "waf" {
  source = "./modules/waf"
  
  environment    = var.environment
  project_name   = var.project_name
  common_tags    = var.common_tags
}

# API Gateway
module "api_gateway" {
  source = "./modules/api-gateway"
  
  environment    = var.environment
  project_name   = var.project_name
  vpc_id         = module.internet_vpc.vpc_id
  subnet_ids     = module.internet_vpc.public_subnet_ids
  common_tags    = var.common_tags
}

# CloudFront
module "cloudfront" {
  source = "./modules/cloudfront"
  
  environment           = var.environment
  project_name          = var.project_name
  api_gateway_domain    = module.api_gateway.api_gateway_domain
  waf_web_acl_arn      = module.waf.web_acl_arn
  common_tags          = var.common_tags
}

# Istio via Helm
module "helm_istio" {
  source = "./modules/helm-istio"
  
  environment      = var.environment
  istio_version    = var.istio_version
  istio_namespace  = var.istio_namespace
  private_vpc_cidr = var.vpc_cidr_blocks.private_vpc
  
  depends_on = [module.eks]
}

# Monitoring via Helm
module "helm_monitoring" {
  source = "./modules/helm-monitoring"
  
  environment               = var.environment
  prometheus_retention_period = var.prometheus_retention_period
  prometheus_storage_size   = var.prometheus_storage_size
  
  depends_on = [module.eks]
}

# Chess App via Helm
module "helm_chess_app" {
  source = "./modules/helm-chess-app"
  
  environment           = var.environment
  app_namespace         = var.app_namespace
  app_version          = var.app_version
  s3_bucket_name       = module.s3.bucket_name
  service_account_role_arn = module.secrets_manager.service_account_role_arn
  helm_set_values      = var.helm_set_values
  helm_set_sensitive_values = var.helm_set_sensitive_values
  
  depends_on = [module.helm_istio]
}