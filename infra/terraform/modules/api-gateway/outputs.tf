output "api_gateway_domain" {
  description = "API Gateway domain name"
  value       = aws_apigatewayv2_api.chess_app.api_endpoint
}

output "api_url" {
  description = "API Gateway URL"
  value       = "${aws_apigatewayv2_api.chess_app.api_endpoint}/${var.environment}"
}