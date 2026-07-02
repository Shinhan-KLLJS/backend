# ============================================================
# modules/ec2/main.tf
# EC2: private subnet, ALB에서만 접근, SQS 폴링
# 배포: SSH 불가 → SSM Run Command로 Docker 컨테이너 배포
# ============================================================

# ──────────────── EC2 Security Group ────────────────
resource "aws_security_group" "ec2" {
  name        = "${var.project_name}-ec2-sg"
  description = "EC2 Spring Boot - Allow inbound from ALB only"
  vpc_id      = var.vpc_id

  # ALB에서 오는 트래픽만 허용 (직접 접근 차단)
  ingress {
    description     = "Spring Boot from ALB only"
    from_port       = 8080
    to_port         = 8080
    protocol        = "tcp"
    security_groups = [var.alb_security_group_id]
  }

  # 외부로 나가는 트래픽 (RDS, SQS Endpoint 통신에 필요)
  egress {
    description = "All outbound"
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }

  tags = {
    Name    = "${var.project_name}-ec2-sg"
    Project = var.project_name
  }
}

# ──────────────── IAM Role (EC2 → SQS, CloudWatch 권한) ────────────────
resource "aws_iam_role" "ec2" {
  name = "${var.project_name}-ec2-role"

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Action    = "sts:AssumeRole"
      Effect    = "Allow"
      Principal = { Service = "ec2.amazonaws.com" }
    }]
  })
}

# SQS 접근 권한 (receive, delete, get attributes)
resource "aws_iam_role_policy" "ec2_sqs" {
  name = "${var.project_name}-ec2-sqs-policy"
  role = aws_iam_role.ec2.id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect = "Allow"
      Action = [
        "sqs:ReceiveMessage",
        "sqs:DeleteMessage",
        "sqs:GetQueueAttributes",
        "sqs:GetQueueUrl"
      ]
      Resource = "*"
    }]
  })
}

# CloudWatch Logs 권한 (Spring 로그 수집)
resource "aws_iam_role_policy_attachment" "ec2_cloudwatch" {
  role       = aws_iam_role.ec2.name
  policy_arn = "arn:aws:iam::aws:policy/CloudWatchAgentServerPolicy"
}

# SSM Run Command로 배포 명령을 받기 위한 권한 (private subnet, SSH 불가)
resource "aws_iam_role_policy_attachment" "ec2_ssm" {
  role       = aws_iam_role.ec2.name
  policy_arn = "arn:aws:iam::aws:policy/AmazonSSMManagedInstanceCore"
}

# 배포 스크립트가 Parameter Store에서 DB 접속정보를 읽어올 권한
resource "aws_iam_role_policy" "ec2_ssm_params" {
  name = "${var.project_name}-ec2-ssm-params-policy"
  role = aws_iam_role.ec2.id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Effect   = "Allow"
        Action   = ["ssm:GetParameter", "ssm:GetParameters"]
        Resource = "arn:aws:ssm:*:*:parameter/${var.project_name}/*"
      },
      {
        Effect   = "Allow"
        Action   = "kms:Decrypt"
        Resource = "*"
      }
    ]
  })
}

resource "aws_iam_instance_profile" "ec2" {
  name = "${var.project_name}-ec2-profile"
  role = aws_iam_role.ec2.name
}

# ──────────────── SSM VPC Endpoint (private subnet → SSM 서비스) ────────────────
# NAT Gateway가 없으므로 SSM 에이전트가 인터넷 대신 이 Endpoint로 통신
resource "aws_security_group" "ssm_endpoint" {
  name        = "${var.project_name}-ssm-endpoint-sg"
  description = "SSM VPC Endpoints - Allow HTTPS from EC2"
  vpc_id      = var.vpc_id

  ingress {
    description     = "HTTPS from EC2"
    from_port       = 443
    to_port         = 443
    protocol        = "tcp"
    security_groups = [aws_security_group.ec2.id]
  }

  tags = {
    Name    = "${var.project_name}-ssm-endpoint-sg"
    Project = var.project_name
  }
}

resource "aws_vpc_endpoint" "ssm" {
  vpc_id              = var.vpc_id
  service_name        = "com.amazonaws.${var.aws_region}.ssm"
  vpc_endpoint_type   = "Interface"
  subnet_ids          = var.private_subnet_ids
  security_group_ids  = [aws_security_group.ssm_endpoint.id]
  private_dns_enabled = true

  tags = {
    Name    = "${var.project_name}-ssm-endpoint"
    Project = var.project_name
  }
}

resource "aws_vpc_endpoint" "ssmmessages" {
  vpc_id              = var.vpc_id
  service_name        = "com.amazonaws.${var.aws_region}.ssmmessages"
  vpc_endpoint_type   = "Interface"
  subnet_ids          = var.private_subnet_ids
  security_group_ids  = [aws_security_group.ssm_endpoint.id]
  private_dns_enabled = true

  tags = {
    Name    = "${var.project_name}-ssmmessages-endpoint"
    Project = var.project_name
  }
}

resource "aws_vpc_endpoint" "ec2messages" {
  vpc_id              = var.vpc_id
  service_name        = "com.amazonaws.${var.aws_region}.ec2messages"
  vpc_endpoint_type   = "Interface"
  subnet_ids          = var.private_subnet_ids
  security_group_ids  = [aws_security_group.ssm_endpoint.id]
  private_dns_enabled = true

  tags = {
    Name    = "${var.project_name}-ec2messages-endpoint"
    Project = var.project_name
  }
}

# ──────────────── 최신 Amazon Linux 2023 AMI 자동 조회 ────────────────
data "aws_ami" "amazon_linux" {
  most_recent = true
  owners      = ["amazon"]

  filter {
    name   = "name"
    values = ["al2023-ami-*-x86_64"]
  }

  filter {
    name   = "state"
    values = ["available"]
  }
}

# ──────────────── EC2 인스턴스 ────────────────
resource "aws_instance" "spring" {
  ami                    = data.aws_ami.amazon_linux.id
  instance_type          = var.instance_type
  subnet_id              = var.private_subnet_ids[0]  # private subnet
  vpc_security_group_ids = [aws_security_group.ec2.id]
  iam_instance_profile   = aws_iam_instance_profile.ec2.name
  key_name               = var.key_name

  # user_data는 실행 중인 인스턴스에 반영 불가(cloud-init은 최초 부팅에만 실행)
  # → 변경 시 반드시 재생성되도록 강제
  user_data_replace_on_change = true

  # Docker 설치 (컨테이너 배포는 CI/CD에서 SSM Run Command로 수행)
  user_data = base64encode(<<-EOF
    #!/bin/bash
    dnf install -y docker
    systemctl enable docker
    systemctl start docker
    usermod -aG docker ec2-user
  EOF
  )

  tags = {
    Name    = "${var.project_name}-spring-ec2"
    Project = var.project_name
  }
}

# ──────────────── ALB Target Group에 EC2 등록 ────────────────
resource "aws_lb_target_group_attachment" "spring" {
  target_group_arn = var.target_group_arn
  target_id        = aws_instance.spring.id
  port             = 8080
}
