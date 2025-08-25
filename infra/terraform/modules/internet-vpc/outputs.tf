output "vpc_id" {
  description = "Internet VPC ID"
  value       = aws_vpc.internet.id
}

output "public_subnet_ids" {
  description = "Public subnet IDs"
  value       = aws_subnet.public[*].id
}

output "security_group_id" {
  description = "API Gateway security group ID"
  value       = aws_security_group.api_gateway.id
}