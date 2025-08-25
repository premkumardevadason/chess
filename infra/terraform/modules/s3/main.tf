# S3 Storage Module
resource "random_id" "bucket_suffix" {
  byte_length = 4
}

resource "aws_s3_bucket" "chess_ai_models" {
  bucket = "${var.project_name}-ai-models-${var.environment}-${random_id.bucket_suffix.hex}"
  
  tags = merge(var.common_tags, {
    Name = "${var.project_name}-${var.environment}-ai-models"
    Purpose = "AI model storage"
  })
}

resource "aws_s3_bucket_versioning" "chess_ai_models" {
  bucket = aws_s3_bucket.chess_ai_models.id
  versioning_configuration {
    status = "Enabled"
  }
}

resource "aws_s3_bucket_server_side_encryption_configuration" "chess_ai_models" {
  bucket = aws_s3_bucket.chess_ai_models.id
  
  rule {
    apply_server_side_encryption_by_default {
      sse_algorithm = "AES256"
    }
  }
}

resource "aws_s3_bucket_public_access_block" "chess_ai_models" {
  bucket = aws_s3_bucket.chess_ai_models.id
  
  block_public_acls       = true
  block_public_policy     = true
  ignore_public_acls      = true
  restrict_public_buckets = true
}

resource "aws_s3_bucket_intelligent_tiering_configuration" "chess_ai_models" {
  bucket = aws_s3_bucket.chess_ai_models.id
  name   = "chess-ai-models-tiering"
  
  tiering {
    access_tier = "DEEP_ARCHIVE_ACCESS"
    days        = 180
  }
  
  tiering {
    access_tier = "ARCHIVE_ACCESS"
    days        = 90
  }
}

resource "aws_s3_bucket_policy" "chess_ai_models" {
  bucket = aws_s3_bucket.chess_ai_models.id
  
  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Sid       = "DenyDirectInternetAccess"
        Effect    = "Deny"
        Principal = "*"
        Action    = "s3:*"
        Resource = [
          aws_s3_bucket.chess_ai_models.arn,
          "${aws_s3_bucket.chess_ai_models.arn}/*"
        ]
        Condition = {
          StringNotEquals = {
            "aws:SourceVpce" = var.s3_vpc_endpoint_id
          }
        }
      }
    ]
  })
}