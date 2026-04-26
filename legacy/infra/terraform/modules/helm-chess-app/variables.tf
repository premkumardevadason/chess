variable "environment" {
  description = "Environment name"
  type        = string
}

variable "app_namespace" {
  description = "Application namespace"
  type        = string
}

variable "app_release_name" {
  description = "Helm release name"
  type        = string
  default     = "chess-app"
}

variable "app_version" {
  description = "Application version"
  type        = string
}

variable "s3_bucket_name" {
  description = "S3 bucket name"
  type        = string
}

variable "service_account_role_arn" {
  description = "Service account IAM role ARN"
  type        = string
}

variable "helm_set_values" {
  description = "Helm values to set"
  type        = map(string)
  default     = {}
}

variable "helm_set_sensitive_values" {
  description = "Sensitive Helm values to set"
  type        = map(string)
  default     = {}
  sensitive   = true
}

variable "helm_timeout" {
  description = "Helm timeout"
  type        = number
  default     = 600
}