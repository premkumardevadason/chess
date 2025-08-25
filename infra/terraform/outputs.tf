# outputs.tf

output "eks_cluster_name" {
  description = "EKS cluster name"
  value       = module.eks.cluster_name
}

output "eks_cluster_endpoint" {
  description = "EKS cluster endpoint"
  value       = module.eks.cluster_endpoint
}

output "ecr_repository_url" {
  description = "ECR repository URL"
  value       = module.ecr.repository_url
}

output "s3_bucket_name" {
  description = "S3 bucket name for AI models"
  value       = module.s3.bucket_name
}

output "cloudfront_domain_name" {
  description = "CloudFront distribution domain name"
  value       = module.cloudfront.domain_name
}

output "api_gateway_url" {
  description = "API Gateway URL"
  value       = module.api_gateway.api_url
}

output "private_vpc_id" {
  description = "Private VPC ID"
  value       = module.private_vpc.vpc_id
}

output "internet_vpc_id" {
  description = "Internet VPC ID"
  value       = module.internet_vpc.vpc_id
}