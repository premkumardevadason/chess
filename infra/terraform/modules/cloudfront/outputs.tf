output "domain_name" {
  description = "CloudFront distribution domain name"
  value       = aws_cloudfront_distribution.chess_app.domain_name
}

output "distribution_id" {
  description = "CloudFront distribution ID"
  value       = aws_cloudfront_distribution.chess_app.id
}