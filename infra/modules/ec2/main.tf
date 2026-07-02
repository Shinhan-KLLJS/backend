# ============================================================
# modules/ec2/main.tf
# EC2: public subnet에 위치하지만, 보안그룹으로 ALB 외 인바운드 전면 차단
# (인스턴스 1대뿐이라 NAT/VPC Endpoint 대신 IGW를 직접 쓰고 비용 절감)
# 배포: SSH 인바운드 없음 → SSM Run Command로 Docker 컨테이너 배포
# ============================================================

# ──────────────── EC2 Security Group ────────────────
resource "aws_security_group" "ec2" {
  name        = "${var.project_name}-ec2-sg"
  description = "EC2 Spring Boot - Allow inbound from ALB only"
  vpc_id      = var.vpc_id

  # ALB에서 오는 트래픽만 허용 (SSH 포함 그 외 인바운드 전부 차단, public subnet이어도 직접 접근 불가)
  ingress {
    description     = "Spring Boot from ALB only"
    from_port       = 8080
    to_port         = 8080
    protocol        = "tcp"
    security_groups = [var.alb_security_group_id]
  }

  # 외부로 나가는 트래픽 (RDS, SQS, Docker Hub, SSM 통신에 필요)
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
  subnet_id              = var.public_subnet_ids[0]  # public subnet (map_public_ip_on_launch로 퍼블릭 IP 자동 할당)
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
