variable "environment" {
  description = "Environment name"
  type        = string
}

variable "project_name" {
  description = "Project name"
  type        = string
}

variable "vpc_id" {
  description = "VPC ID"
  type        = string
}

variable "route_table_ids" {
  description = "Route table IDs for VPC endpoint"
  type        = list(string)
}

variable "s3_vpc_endpoint_id" {
  description = "S3 VPC endpoint ID"
  type        = string
  default     = ""
}

variable "common_tags" {
  description = "Common tags"
  type        = map(string)
}