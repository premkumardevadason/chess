# EKS Lifecycle Management Resources

# Auto Scaling Group Lifecycle Hooks
resource "aws_autoscaling_lifecycle_hook" "node_termination" {
  for_each = var.node_groups
  
  name                 = "${var.project_name}-${var.environment}-${each.key}-termination-hook"
  autoscaling_group_name = aws_eks_node_group.main[each.key].resources[0].autoscaling_groups[0].name
  default_result       = "ABANDON"
  heartbeat_timeout    = 300
  lifecycle_transition = "autoscaling:EC2_INSTANCE_TERMINATING"
  
  notification_target_arn = aws_sns_topic.node_lifecycle.arn
  role_arn               = aws_iam_role.lifecycle_hook.arn
}

# SNS Topic for Lifecycle Events
resource "aws_sns_topic" "node_lifecycle" {
  name = "${var.project_name}-${var.environment}-node-lifecycle"
  
  tags = var.common_tags
}

# IAM Role for Lifecycle Hook
resource "aws_iam_role" "lifecycle_hook" {
  name = "${var.project_name}-${var.environment}-lifecycle-hook-role"
  
  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Action = "sts:AssumeRole"
        Effect = "Allow"
        Principal = {
          Service = "autoscaling.amazonaws.com"
        }
      }
    ]
  })
  
  tags = var.common_tags
}

resource "aws_iam_role_policy" "lifecycle_hook" {
  name = "${var.project_name}-${var.environment}-lifecycle-hook-policy"
  role = aws_iam_role.lifecycle_hook.id
  
  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Effect = "Allow"
        Action = [
          "sns:Publish"
        ]
        Resource = aws_sns_topic.node_lifecycle.arn
      }
    ]
  })
}

# CloudWatch Alarms for Node Health
resource "aws_cloudwatch_metric_alarm" "node_cpu_high" {
  for_each = var.node_groups
  
  alarm_name          = "${var.project_name}-${var.environment}-${each.key}-cpu-high"
  comparison_operator = "GreaterThanThreshold"
  evaluation_periods  = "2"
  metric_name         = "CPUUtilization"
  namespace           = "AWS/EC2"
  period              = "300"
  statistic           = "Average"
  threshold           = "80"
  alarm_description   = "This metric monitors ec2 cpu utilization"
  
  dimensions = {
    AutoScalingGroupName = aws_eks_node_group.main[each.key].resources[0].autoscaling_groups[0].name
  }
  
  alarm_actions = [aws_sns_topic.node_lifecycle.arn]
  
  tags = var.common_tags
}

# Lambda Function for Graceful Node Termination
resource "aws_lambda_function" "node_drainer" {
  filename         = "node_drainer.zip"
  function_name    = "${var.project_name}-${var.environment}-node-drainer"
  role            = aws_iam_role.lambda_drainer.arn
  handler         = "index.handler"
  runtime         = "python3.9"
  timeout         = 300
  
  environment {
    variables = {
      CLUSTER_NAME = aws_eks_cluster.main.name
    }
  }
  
  tags = var.common_tags
}

# IAM Role for Lambda Drainer
resource "aws_iam_role" "lambda_drainer" {
  name = "${var.project_name}-${var.environment}-lambda-drainer-role"
  
  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Action = "sts:AssumeRole"
        Effect = "Allow"
        Principal = {
          Service = "lambda.amazonaws.com"
        }
      }
    ]
  })
  
  tags = var.common_tags
}

resource "aws_iam_role_policy_attachment" "lambda_drainer_basic" {
  policy_arn = "arn:aws:iam::aws:policy/service-role/AWSLambdaBasicExecutionRole"
  role       = aws_iam_role.lambda_drainer.name
}

resource "aws_iam_role_policy" "lambda_drainer" {
  name = "${var.project_name}-${var.environment}-lambda-drainer-policy"
  role = aws_iam_role.lambda_drainer.id
  
  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Effect = "Allow"
        Action = [
          "eks:DescribeCluster",
          "autoscaling:CompleteLifecycleAction",
          "ec2:DescribeInstances"
        ]
        Resource = "*"
      }
    ]
  })
}

# SNS Subscription for Lambda
resource "aws_sns_topic_subscription" "lambda_drainer" {
  topic_arn = aws_sns_topic.node_lifecycle.arn
  protocol  = "lambda"
  endpoint  = aws_lambda_function.node_drainer.arn
}

resource "aws_lambda_permission" "sns_invoke" {
  statement_id  = "AllowExecutionFromSNS"
  action        = "lambda:InvokeFunction"
  function_name = aws_lambda_function.node_drainer.function_name
  principal     = "sns.amazonaws.com"
  source_arn    = aws_sns_topic.node_lifecycle.arn
}