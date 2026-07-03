# ============================================================
# modules/sqs/main.tf
# SQS 큐
# - 로컬 Vision, EC2 모두 인터넷을 통해 직접 접근 (EC2가 public subnet에 있음)
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
# 발행(로컬 Vision AI 담당자)과 수신(EC2)을 각각 특정 IAM 주체로 제한
resource "aws_sqs_queue_policy" "main" {
  queue_url = aws_sqs_queue.main.id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        # 로컬 Vision(AI 담당자) 머신에서 메시지 전송 허용
        Sid       = "AllowLocalVisionPublish"
        Effect    = "Allow"
        Principal = { AWS = var.producer_principal_arn }
        Action    = "sqs:SendMessage"
        Resource  = aws_sqs_queue.main.arn
      },
      {
        # EC2에서 메시지 수신/삭제 허용
        Sid       = "AllowEC2Consume"
        Effect    = "Allow"
        Principal = { AWS = var.consumer_principal_arn }
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
