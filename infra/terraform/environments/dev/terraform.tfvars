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