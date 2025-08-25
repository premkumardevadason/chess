# CloudFront Distribution Module
resource "aws_cloudfront_distribution" "chess_app" {
  origin {
    domain_name = replace(var.api_gateway_domain, "https://", "")
    origin_id   = "${var.project_name}-${var.environment}-api-gateway"
    
    custom_origin_config {
      http_port              = 80
      https_port             = 443
      origin_protocol_policy = "https-only"
      origin_ssl_protocols   = ["TLSv1.2"]
    }
  }
  
  enabled         = true
  is_ipv6_enabled = true
  comment         = "${var.project_name} ${var.environment} distribution"
  
  # Static assets caching behavior
  ordered_cache_behavior {
    path_pattern     = "/static/*"
    allowed_methods  = ["GET", "HEAD"]
    cached_methods   = ["GET", "HEAD"]
    target_origin_id = "${var.project_name}-${var.environment}-api-gateway"
    
    forwarded_values {
      query_string = false
      cookies {
        forward = "none"
      }
    }
    
    viewer_protocol_policy = "redirect-to-https"
    min_ttl                = 86400   # 1 day
    default_ttl            = 604800  # 1 week
    max_ttl                = 2592000 # 30 days
    compress               = true
  }
  
  # WebSocket and API behavior
  default_cache_behavior {
    allowed_methods        = ["DELETE", "GET", "HEAD", "OPTIONS", "PATCH", "POST", "PUT"]
    cached_methods         = ["GET", "HEAD"]
    target_origin_id       = "${var.project_name}-${var.environment}-api-gateway"
    viewer_protocol_policy = "redirect-to-https"
    
    forwarded_values {
      query_string = true
      headers      = ["Authorization", "x-session-id", "Upgrade", "Connection"]
      
      cookies {
        forward = "all"
      }
    }
    
    min_ttl     = 0
    default_ttl = 0
    max_ttl     = 0
  }
  
  viewer_certificate {
    cloudfront_default_certificate = true
  }
  
  # WAF association
  web_acl_id = var.waf_web_acl_arn
  
  restrictions {
    geo_restriction {
      restriction_type = "none"
    }
  }
  
  tags = merge(var.common_tags, {
    Name = "${var.project_name}-${var.environment}-cdn"
  })
}