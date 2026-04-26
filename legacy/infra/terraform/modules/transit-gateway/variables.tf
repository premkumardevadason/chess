variable "environment" {
  description = "Environment name"
  type        = string
}

variable "project_name" {
  description = "Project name"
  type        = string
}

variable "internet_vpc_id" {
  description = "Internet VPC ID"
  type        = string
}

variable "private_vpc_id" {
  description = "Private VPC ID"
  type        = string
}

variable "common_tags" {
  description = "Common tags"
  type        = map(string)
}