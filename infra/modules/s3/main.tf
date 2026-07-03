# ============================================================
# modules/s3/main.tf
# 이미지/파일 업로드용 S3 버킷
# - 퍼블릭 접근 완전 차단, 접근은 IAM 정책(ai_producer, EC2 role)으로만 제어
# ============================================================

data "aws_caller_identity" "current" {}

resource "aws_s3_bucket" "uploads" {
  bucket        = "${var.project_name}-uploads-${data.aws_caller_identity.current.account_id}"
  force_destroy = true # 학습용. 프로덕션에서는 false 권장

  tags = {
    Name    = "${var.project_name}-uploads"
    Project = var.project_name
  }
}

resource "aws_s3_bucket_public_access_block" "uploads" {
  bucket = aws_s3_bucket.uploads.id

  block_public_acls       = true
  block_public_policy     = true
  ignore_public_acls      = true
  restrict_public_buckets = true
}

resource "aws_s3_bucket_server_side_encryption_configuration" "uploads" {
  bucket = aws_s3_bucket.uploads.id

  rule {
    apply_server_side_encryption_by_default {
      sse_algorithm = "AES256"
    }
  }
}
