# ============================================================
# modules/s3/main.tf
# 이미지/파일 업로드용 S3 버킷
# - 기본은 퍼블릭 접근 완전 차단, 접근은 IAM 정책(ai_producer, EC2 role)으로만 제어
# - campaign-creatives/* 프리픽스만 예외적으로 공개 읽기 허용 (캠페인 소재는 공개 URL로 노출되어야 함)
# ============================================================

data "aws_caller_identity" "current" {}

resource "aws_s3_bucket" "uploads" {
  bucket        = "${var.project_name}-uploads-${data.aws_caller_identity.current.account_id}"
  force_destroy = false # 운영 버킷 - 실수로 destroy해도 비어있지 않으면 거부되도록 방지막을 켜둔다

  tags = {
    Name    = "${var.project_name}-uploads"
    Project = var.project_name
  }
}

resource "aws_s3_bucket_public_access_block" "uploads" {
  bucket = aws_s3_bucket.uploads.id

  # ACL 기반 공개는 여전히 전면 차단한다 (ACL을 쓰지 않으므로 유지).
  block_public_acls  = true
  ignore_public_acls = true

  # campaign-creatives/* 프리픽스만 허용하는 버킷 정책(아래)을 적용하려면
  # 정책 기반 공개 접근 차단은 꺼야 한다. 이 두 값만 false로 낮추고 ACL 차단은 유지한다.
  block_public_policy     = false
  restrict_public_buckets = false
}

resource "aws_s3_bucket_server_side_encryption_configuration" "uploads" {
  bucket = aws_s3_bucket.uploads.id

  rule {
    apply_server_side_encryption_by_default {
      sse_algorithm = "AES256"
    }
  }
}

# campaign-creatives/* 오브젝트만 익명 GetObject를 허용한다.
# 다른 프리픽스(예: 팀 사업자 등록 서류)는 이 정책에 포함되지 않으므로 계속 비공개로 남는다.
resource "aws_s3_bucket_policy" "campaign_creatives_public_read" {
  bucket = aws_s3_bucket.uploads.id

  # public access block 완화가 먼저 적용되어야 정책이 거부되지 않는다.
  depends_on = [aws_s3_bucket_public_access_block.uploads]

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Sid       = "PublicReadCampaignCreatives"
        Effect    = "Allow"
        Principal = "*"
        Action    = "s3:GetObject"
        Resource  = "${aws_s3_bucket.uploads.arn}/campaign-creatives/*"
      }
    ]
  })
}

# 브라우저가 presigned PUT URL로 캠페인 소재를 직접 업로드할 수 있도록 CORS를 허용한다.
resource "aws_s3_bucket_cors_configuration" "campaign_creatives" {
  bucket = aws_s3_bucket.uploads.id

  cors_rule {
    allowed_methods = ["PUT"]
    allowed_origins = var.campaign_creative_cors_origins
    allowed_headers = ["Content-Type"]
    max_age_seconds = 3000
  }
}
