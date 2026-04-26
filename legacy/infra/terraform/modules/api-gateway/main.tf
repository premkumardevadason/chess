# API Gateway Module
resource "aws_apigatewayv2_api" "chess_app" {
  name          = "${var.project_name}-${var.environment}-api"
  protocol_type = "HTTP"
  description   = "Chess App API Gateway"
  
  cors_configuration {
    allow_credentials = false
    allow_headers     = ["content-type", "x-amz-date", "authorization", "x-api-key", "x-amz-security-token", "x-session-id"]
    allow_methods     = ["*"]
    allow_origins     = ["*"]
    expose_headers    = ["date", "keep-alive"]
    max_age          = 86400
  }
  
  tags = var.common_tags
}

resource "aws_apigatewayv2_vpc_link" "chess_app" {
  name               = "${var.project_name}-${var.environment}-vpc-link"
  protocol_type      = "HTTP"
  subnet_ids         = var.subnet_ids
  security_group_ids = [aws_security_group.vpc_link.id]
  
  tags = var.common_tags
}

resource "aws_security_group" "vpc_link" {
  name_prefix = "${var.project_name}-${var.environment}-vpc-link-"
  vpc_id      = var.vpc_id
  
  ingress {
    from_port   = 80
    to_port     = 80
    protocol    = "tcp"
    cidr_blocks = ["10.0.0.0/8"]
  }
  
  ingress {
    from_port   = 443
    to_port     = 443
    protocol    = "tcp"
    cidr_blocks = ["10.0.0.0/8"]
  }
  
  egress {
    from_port   = 0
    to_port     = 65535
    protocol    = "tcp"
    cidr_blocks = ["10.0.0.0/8"]
  }
  
  tags = merge(var.common_tags, {
    Name = "${var.project_name}-${var.environment}-vpc-link-sg"
  })
}

resource "aws_apigatewayv2_integration" "chess_app" {
  api_id             = aws_apigatewayv2_api.chess_app.id
  integration_type   = "HTTP_PROXY"
  integration_method = "ANY"
  integration_uri    = "http://chess-app.internal"
  connection_type    = "VPC_LINK"
  connection_id      = aws_apigatewayv2_vpc_link.chess_app.id
}

resource "aws_apigatewayv2_route" "chess_app" {
  api_id    = aws_apigatewayv2_api.chess_app.id
  route_key = "ANY /{proxy+}"
  target    = "integrations/${aws_apigatewayv2_integration.chess_app.id}"
}

resource "aws_apigatewayv2_stage" "chess_app" {
  api_id      = aws_apigatewayv2_api.chess_app.id
  name        = var.environment
  auto_deploy = true
  
  access_log_settings {
    destination_arn = aws_cloudwatch_log_group.api_gateway.arn
    format = jsonencode({
      requestId      = "$context.requestId"
      ip            = "$context.identity.sourceIp"
      requestTime   = "$context.requestTime"
      httpMethod    = "$context.httpMethod"
      routeKey      = "$context.routeKey"
      status        = "$context.status"
      protocol      = "$context.protocol"
      responseLength = "$context.responseLength"
    })
  }
  
  tags = var.common_tags
}

resource "aws_cloudwatch_log_group" "api_gateway" {
  name              = "/aws/apigateway/${var.project_name}-${var.environment}"
  retention_in_days = 14
  
  tags = var.common_tags
}