variable "environment" {
  description = "Environment name"
  type        = string
}

variable "istio_version" {
  description = "Istio version"
  type        = string
}

variable "istio_namespace" {
  description = "Istio namespace"
  type        = string
}

variable "private_vpc_cidr" {
  description = "Private VPC CIDR"
  type        = string
}