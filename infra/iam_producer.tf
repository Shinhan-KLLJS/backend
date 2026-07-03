# ============================================================
# iam_producer.tf
# 로컬 Vision(AI 담당자)이 웹캠 처리 결과를 SQS에 발행하고,
# 원본 이미지/파일을 S3에 업로드하기 위한 최소권한 IAM 사용자
# (SendMessage + PutObject만 가능, 큐/버킷 조회·삭제 등은 전부 불가)
# ============================================================

resource "aws_iam_user" "ai_producer" {
  name = "${var.project_name}-ai-producer"
}

resource "aws_iam_user_policy" "ai_producer" {
  name = "${var.project_name}-ai-producer-policy"
  user = aws_iam_user.ai_producer.name

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Sid      = "SendVisionResultsOnly"
        Effect   = "Allow"
        Action   = ["sqs:SendMessage", "sqs:GetQueueUrl"]
        Resource = module.sqs.queue_arn
      },
      {
        Sid      = "UploadImagesOnly"
        Effect   = "Allow"
        Action   = "s3:PutObject"
        Resource = "${module.s3.bucket_arn}/*"
      }
    ]
  })
}

resource "aws_iam_access_key" "ai_producer" {
  user = aws_iam_user.ai_producer.name
}
