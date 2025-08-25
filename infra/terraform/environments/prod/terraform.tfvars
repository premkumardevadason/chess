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