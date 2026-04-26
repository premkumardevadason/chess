# Transit Gateway Module
resource "aws_ec2_transit_gateway" "main" {
  description                     = "${var.project_name}-${var.environment}-tgw"
  default_route_table_association = "enable"
  default_route_table_propagation = "enable"
  
  tags = merge(var.common_tags, {
    Name = "${var.project_name}-${var.environment}-tgw"
  })
}

resource "aws_ec2_transit_gateway_vpc_attachment" "internet" {
  subnet_ids         = [data.aws_subnets.internet_public.ids[0]]
  transit_gateway_id = aws_ec2_transit_gateway.main.id
  vpc_id             = var.internet_vpc_id
  
  tags = merge(var.common_tags, {
    Name = "${var.project_name}-${var.environment}-tgw-internet-attachment"
  })
}

resource "aws_ec2_transit_gateway_vpc_attachment" "private" {
  subnet_ids         = [data.aws_subnets.private_subnets.ids[0]]
  transit_gateway_id = aws_ec2_transit_gateway.main.id
  vpc_id             = var.private_vpc_id
  
  tags = merge(var.common_tags, {
    Name = "${var.project_name}-${var.environment}-tgw-private-attachment"
  })
}

data "aws_subnets" "internet_public" {
  filter {
    name   = "vpc-id"
    values = [var.internet_vpc_id]
  }
  
  filter {
    name   = "tag:Type"
    values = ["public"]
  }
}

data "aws_subnets" "private_subnets" {
  filter {
    name   = "vpc-id"
    values = [var.private_vpc_id]
  }
  
  filter {
    name   = "tag:Type"
    values = ["private"]
  }
}