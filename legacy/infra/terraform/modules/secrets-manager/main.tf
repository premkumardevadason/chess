# Secrets Manager Module
resource "aws_secretsmanager_secret" "chess_app_secrets" {
  name        = "${var.project_name}-${var.environment}-secrets"
  description = "Chess application secrets"
  
  tags = var.common_tags
}

resource "aws_secretsmanager_secret_version" "chess_app_secrets" {
  secret_id = aws_secretsmanager_secret.chess_app_secrets.id
  secret_string = jsonencode({
    openai_api_key = "your-openai-api-key-here"
  })
  
  lifecycle {
    ignore_changes = [secret_string]
  }
}

data "aws_caller_identity" "current" {}

resource "aws_iam_role" "chess_app_secrets_role" {
  name = "${var.project_name}-${var.environment}-secrets-role"
  
  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Action = "sts:AssumeRoleWithWebIdentity"
        Effect = "Allow"
        Principal = {
          Federated = "arn:aws:iam::${data.aws_caller_identity.current.account_id}:oidc-provider/oidc.eks.${data.aws_region.current.name}.amazonaws.com/id/EXAMPLE"
        }
        Condition = {
          StringEquals = {
            "oidc.eks.${data.aws_region.current.name}.amazonaws.com/id/EXAMPLE:sub" = "system:serviceaccount:chess-app:chess-app"
            "oidc.eks.${data.aws_region.current.name}.amazonaws.com/id/EXAMPLE:aud" = "sts.amazonaws.com"
          }
        }
      }
    ]
  })
  
  tags = var.common_tags
}

resource "aws_iam_role_policy" "chess_app_secrets_policy" {
  name = "${var.project_name}-${var.environment}-secrets-policy"
  role = aws_iam_role.chess_app_secrets_role.id
  
  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Effect = "Allow"
        Action = [
          "secretsmanager:GetSecretValue",
          "secretsmanager:DescribeSecret"
        ]
        Resource = aws_secretsmanager_secret.chess_app_secrets.arn
      }
    ]
  })
}

data "aws_region" "current" {}