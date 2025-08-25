output "secret_arn" {
  description = "Secrets Manager secret ARN"
  value       = aws_secretsmanager_secret.chess_app_secrets.arn
}

output "service_account_role_arn" {
  description = "Service account IAM role ARN"
  value       = aws_iam_role.chess_app_secrets_role.arn
}