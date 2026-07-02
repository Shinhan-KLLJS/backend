# ============================================================
# modules/sqs/main.tf
# SQS 큐 + VPC Interface Endpoint
# - 로컬 Vision → SQS: 인터넷 직접 접근 (boto3 + IAM 키)
# - EC2 → SQS: VPC Endpoint 경유 (내부 AWS 백본망)
# ============================================================

# ──────────────── Dead Letter Queue (실패 메시지 보관) ────────────────
resource "aws_sqs_queue" "dlq" {
  name                      = "${var.project_name}-vision-dlq"
  message_retention_seconds = 1209600  # 14일 보관

  tags = {
    Name    = "${var.project_name}-vision-dlq"
    Project = var.project_name
  }
}

# ──────────────── 메인 SQS 큐 ────────────────
resource "aws_sqs_queue" "main" {
  name                       = "${var.project_name}-vision-queue"
  visibility_timeout_seconds = 60     # Spring이 처리하는 동안 다른 Consumer에 안 보임
  message_retention_seconds  = 345600 # 4일 보관
  receive_wait_time_seconds  = 20     # Long polling (빈 응답 줄여 비용 절감)

  # DLQ 연결 (3번 실패하면 DLQ로 이동)
  redrive_policy = jsonencode({
    deadLetterTargetArn = aws_sqs_queue.dlq.arn
    maxReceiveCount     = 3
  })

  tags = {
    Name    = "${var.project_name}-vision-queue"
    Project = var.project_name
  }
}

# ──────────────── SQS 접근 정책 ────────────────
# 로컬 머신(boto3)과 EC2(VPC Endpoint) 모두 허용
resource "aws_sqs_queue_policy" "main" {
  queue_url = aws_sqs_queue.main.id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        # 로컬 Vision 머신에서 메시지 전송 허용
        Sid    = "AllowLocalVisionPublish"
        Effect = "Allow"
        Principal = { AWS = "*" }
        Action    = "sqs:SendMessage"
        Resource  = aws_sqs_queue.main.arn
        Condition = {
          # 실제 배포 시 로컬 IAM User ARN으로 제한 권장
          StringEquals = {
            "aws:RequestedRegion" = "ap-northeast-2"
          }
        }
      },
      {
        # EC2에서 메시지 수신/삭제 허용
        Sid    = "AllowEC2Consume"
        Effect = "Allow"
        Principal = { AWS = "*" }
        Action = [
          "sqs:ReceiveMessage",
          "sqs:DeleteMessage",
          "sqs:GetQueueAttributes"
        ]
        Resource = aws_sqs_queue.main.arn
      }
    ]
  })
}

# ──────────────── VPC Endpoint Security Group ────────────────
# EC2에서 Endpoint로 HTTPS(443) 접근 허용
resource "aws_security_group" "vpc_endpoint" {
  name        = "${var.project_name}-vpc-endpoint-sg"
  description = "VPC Endpoint - Allow HTTPS from EC2"
  vpc_id      = var.vpc_id

  ingress {
    description     = "HTTPS from EC2"
    from_port       = 443
    to_port         = 443
    protocol        = "tcp"
    security_groups = [var.ec2_security_group_id]
  }

  tags = {
    Name    = "${var.project_name}-vpc-endpoint-sg"
    Project = var.project_name
  }
}

# ──────────────── SQS VPC Interface Endpoint ────────────────
# EC2(private subnet) → SQS를 인터넷 없이 AWS 내부망으로 연결
resource "aws_vpc_endpoint" "sqs" {
  vpc_id              = var.vpc_id
  service_name        = "com.amazonaws.ap-northeast-2.sqs"
  vpc_endpoint_type   = "Interface"

  # EC2가 있는 private subnet에 ENI 생성
  subnet_ids          = var.private_subnet_ids
  security_group_ids  = [aws_security_group.vpc_endpoint.id]

  # 이 설정이 핵심:
  # EC2에서 sqs.ap-northeast-2.amazonaws.com 호출 시
  # 자동으로 Endpoint로 라우팅됨 (코드 변경 불필요)
  private_dns_enabled = true

  tags = {
    Name    = "${var.project_name}-sqs-endpoint"
    Project = var.project_name
  }
}
