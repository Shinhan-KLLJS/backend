# ============================================================
# modules/ec2/main.tf
# EC2: private subnet, ALB에서만 접근, SQS 폴링
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
  subnet_id              = var.private_subnet_ids[0]  # private subnet
  vpc_security_group_ids = [aws_security_group.ec2.id]
  iam_instance_profile   = aws_iam_instance_profile.ec2.name
  key_name               = var.key_name

  # Spring Boot 초기 설치 스크립트
  user_data = base64encode(<<-EOF
    #!/bin/bash
    # Java 21 설치
    dnf install -y java-21-amazon-corretto

    # 앱 디렉토리 생성
    mkdir -p /app/config

    # Spring Boot JAR 배포 위치
    # 실제 배포 시 GitHub Actions 또는 S3에서 jar를 가져옴
    echo "EC2 초기화 완료. Spring Boot JAR를 /app/app.jar 에 배치하세요." > /var/log/init.log

    # systemd 서비스 등록 (Spring Boot 자동 시작)
    cat > /etc/systemd/system/spring-boot.service << 'SERVICE'
    [Unit]
    Description=Spring Boot Vision App
    After=network.target

    [Service]
    Type=simple
    User=ec2-user
    ExecStart=/usr/bin/java -jar /app/app.jar --spring.config.location=/app/config/application.yml
    Restart=always
    RestartSec=10

    [Install]
    WantedBy=multi-user.target
    SERVICE

    systemctl daemon-reload
    systemctl enable spring-boot
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
