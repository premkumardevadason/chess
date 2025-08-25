variable "environment" {
  description = "Environment name"
  type        = string
}

variable "project_name" {
  description = "Project name"
  type        = string
}

variable "api_gateway_domain" {
  description = "API Gateway domain name"
  type        = string
}

variable "waf_web_acl_arn" {
  description = "WAF Web ACL ARN"
  type        = string
}

variable "common_tags" {
  description = "Common tags"
  type        = map(string)
}