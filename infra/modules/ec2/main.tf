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

# ──────────────── CloudWatch Log Group (앱 컨테이너 로그) ────────────────
# 배포 스크립트(cd.yml)가 docker run에 --log-driver=awslogs로 이 그룹을 지정한다.
# CD가 컨테이너를 stop/rm하고 새로 띄우는 구조라, 로그를 컨테이너 자체에만 두면 다음 배포 때
# 이전 컨테이너의 로그(장애 당시 로그 포함)가 통째로 사라진다 - 실제로 이 문제로 배포 직후
# 발생한 5xx의 원인을 확인하지 못한 사고가 있었다.
resource "aws_cloudwatch_log_group" "app" {
  name              = "/${var.project_name}/app"
  retention_in_days = 14

  tags = {
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

# EC2가 쓰는 S3 권한 - prefix별 Statement로 분리해 최소화한다
#
# 이 정책이 EC2 역할의 유일한 S3 권한이다. 버킷은 여러 기능(캠페인 소재, 사업자등록증,
# Vision 이미지)이 prefix로 나눠 쓰므로, 버킷 전체가 아니라 기능별 prefix 단위로 허용한다 -
# 버킷 전체를 열어두면 애플리케이션에 키 조작 취약점이 하나만 생겨도 남의 객체까지 덮어쓸 수 있다.
#
# [사업자등록증 prefix] PutObject 하나만 준다 (team-creation-api-spec.md 8절 "보안").
# GetObject/HeadObject를 주지 않는 이유: 팀 생성 시 S3를 다시 조회하지 않는다. 업로드 때 발급한
# 서명 토큰(HMAC)만 검증하면 되므로 서버가 파일을 읽을 일이 아예 없다. 읽기 권한이 있으면
# EC2 자격증명이 새는 순간 버킷에 쌓인 사업자등록증 원본(대표자명·주소·사업자번호가 찍힌
# 민감 문서)이 통째로 열린다. 안 쓰는 권한은 주지 않는다.
# (DeleteObject/ListBucket을 주지 않는 이유: orphan 정리 배치가 MVP에서 제외되어 지울 일도,
#  목록을 읽을 일도 없다. 배치가 다시 생기면 그때 해당 권한을 함께 되살린다.)
#
# [캠페인 소재 prefix] presigned PUT URL 발급용 PutObject가 필요하다.
# presigned URL은 서명한 역할의 권한으로 실행되므로, 이 Statement가 없으면 이미 운영 중인
# 캠페인 소재 업로드가 통째로 403이 된다. 조회는 버킷 정책(campaign-creatives/* 익명 GetObject)이
# 담당하므로 역할에는 읽기 권한이 필요 없다.
resource "aws_iam_role_policy" "ec2_s3" {
  name = "${var.project_name}-ec2-s3-policy"
  role = aws_iam_role.ec2.id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Sid      = "BusinessRegistrationUpload"
        Effect   = "Allow"
        Action   = "s3:PutObject"
        Resource = "${var.s3_bucket_arn}/${var.business_registration_key_prefix}*"
      },
      {
        Sid      = "CampaignCreativePresignedUpload"
        Effect   = "Allow"
        Action   = "s3:PutObject"
        Resource = "${var.s3_bucket_arn}/${var.campaign_creative_key_prefix}*"
      }
    ]
  })
}

# OCR Lambda 호출 권한 (DV-112)
#
# API Gateway를 거치지 않고 SDK로 직접 invoke한다 - API GW는 동기 호출에 29초 제한이 있고,
# 인증(JWT authorizer)과 CORS를 따로 관리해야 하며, 인증 없이 열려 있으면 OCR 비용이 남용된다.
# 백엔드가 이미 검증한 JWT 세션 안에서 부르면 이 문제가 전부 없어진다.
#
# 리소스를 그 함수 하나로 못박는다. 와일드카드를 쓰면 EC2가 계정의 모든 Lambda를 부를 수 있다.
resource "aws_iam_role_policy" "ec2_lambda_invoke" {
  count = var.business_registration_ocr_function_arn == "" ? 0 : 1

  name = "${var.project_name}-ec2-lambda-invoke-policy"
  role = aws_iam_role.ec2.id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Effect   = "Allow"
        Action   = "lambda:InvokeFunction"
        Resource = var.business_registration_ocr_function_arn
      }
    ]
  })
}

resource "aws_iam_instance_profile" "ec2" {
  name = "${var.project_name}-ec2-profile"
  role = aws_iam_role.ec2.name
}

# ──────────────── EC2 인스턴스 ────────────────
# AMI는 var.ami_id로 고정한다 (data.aws_ami + most_recent=true였을 때,
# apply할 때마다 최신 AMI를 새로 집어와 ami 변경 -> 인스턴스 강제 재생성 -> 무중단 배포 중이던
# 컨테이너가 통째로 사라지는 사고가 실제로 발생했다. 의도적으로 업그레이드할 때만
# terraform.tfvars의 ec2_ami_id 값을 바꾼다).
resource "aws_instance" "spring" {
  ami                    = var.ami_id
  instance_type          = var.instance_type
  subnet_id              = var.public_subnet_ids[0]  # public subnet (map_public_ip_on_launch로 퍼블릭 IP 자동 할당)
  vpc_security_group_ids = [aws_security_group.ec2.id]
  iam_instance_profile   = aws_iam_instance_profile.ec2.name
  key_name               = var.key_name

  # user_data는 실행 중인 인스턴스에 반영 불가(cloud-init은 최초 부팅에만 실행)
  # → 변경 시 반드시 재생성되도록 강제
  user_data_replace_on_change = true

  # Docker 설치 (컨테이너 배포는 CI/CD에서 SSM Run Command로 수행)
  # t3.micro(1GB RAM)라 스왑 없이는 배포 중 OOM 위험이 있어 1GB swapfile을 부팅 시 만들어둔다 -
  # vm.swappiness는 낮게 둬서 평소엔 RAM을 우선 쓰고 진짜 메모리 부족할 때만 스왑을 쓰게 한다.
  user_data = base64encode(<<-EOF
    #!/bin/bash
    dnf install -y docker
    systemctl enable docker
    systemctl start docker
    usermod -aG docker ec2-user

    if ! swapon --show | grep -q '/swapfile'; then
      fallocate -l 1G /swapfile || dd if=/dev/zero of=/swapfile bs=1M count=1024
      chmod 600 /swapfile
      mkswap /swapfile
      swapon /swapfile
      grep -q '^/swapfile ' /etc/fstab || echo '/swapfile swap swap defaults 0 0' >> /etc/fstab
    fi
    if grep -q '^vm.swappiness' /etc/sysctl.conf 2>/dev/null; then
      sed -i 's/^vm.swappiness.*/vm.swappiness=10/' /etc/sysctl.conf
    else
      echo 'vm.swappiness=10' >> /etc/sysctl.conf
    fi
    sysctl -w vm.swappiness=10
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
