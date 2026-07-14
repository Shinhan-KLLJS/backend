# ============================================================
# modules/ssm_params/main.tf
# EC2가 SSM Run Command로 배포될 때 읽어가는 DB 접속정보
# GitHub Actions는 이 값을 모르고, EC2가 직접 Parameter Store에서 조회함
# ============================================================

resource "aws_ssm_parameter" "db_url" {
  name  = "/${var.project_name}/prod/db-url"
  type  = "String"
  value = var.db_url

  tags = {
    Project = var.project_name
  }
}

resource "aws_ssm_parameter" "db_username" {
  name  = "/${var.project_name}/prod/db-username"
  type  = "String"
  value = var.db_username

  tags = {
    Project = var.project_name
  }
}

resource "aws_ssm_parameter" "db_password" {
  name  = "/${var.project_name}/prod/db-password"
  type  = "SecureString"
  value = var.db_password

  tags = {
    Project = var.project_name
  }
}

resource "aws_ssm_parameter" "kakao_rest_api_key" {
  name  = "/${var.project_name}/prod/kakao-rest-api-key"
  type  = "String"
  value = var.kakao_rest_api_key

  tags = {
    Project = var.project_name
  }
}

resource "aws_ssm_parameter" "kakao_client_secret" {
  name  = "/${var.project_name}/prod/kakao-client-secret"
  type  = "SecureString"
  value = var.kakao_client_secret

  tags = {
    Project = var.project_name
  }
}

resource "aws_ssm_parameter" "kakao_redirect_uri" {
  name  = "/${var.project_name}/prod/kakao-redirect-uri"
  type  = "String"
  value = var.kakao_redirect_uri

  tags = {
    Project = var.project_name
  }
}

resource "aws_ssm_parameter" "app_frontend_url" {
  name  = "/${var.project_name}/prod/app-frontend-url"
  type  = "String"
  value = var.app_frontend_url

  tags = {
    Project = var.project_name
  }
}

resource "aws_ssm_parameter" "jwt_secret" {
  name  = "/${var.project_name}/prod/jwt-secret"
  type  = "SecureString"
  value = var.jwt_secret

  tags = {
    Project = var.project_name
  }
}

resource "aws_ssm_parameter" "sqs_queue_url" {
  name  = "/${var.project_name}/prod/sqs-queue-url"
  type  = "String" # 큐 URL 자체는 비밀이 아니라 접근은 IAM으로 통제됨 (변수 선언 주석 참고)
  value = var.sqs_queue_url

  tags = {
    Project = var.project_name
  }
}

resource "aws_ssm_parameter" "campaign_creative_bucket" {
  name  = "/${var.project_name}/prod/campaign-creative-bucket"
  type  = "String"
  value = var.campaign_creative_bucket

  tags = {
    Project = var.project_name
  }
}

resource "aws_ssm_parameter" "campaign_creative_public_base_url" {
  name  = "/${var.project_name}/prod/campaign-creative-public-base-url"
  type  = "String"
  value = var.campaign_creative_public_base_url

  tags = {
    Project = var.project_name
  }
}

resource "aws_ssm_parameter" "campaign_creative_upload_token_secret" {
  name  = "/${var.project_name}/prod/campaign-creative-upload-token-secret"
  type  = "SecureString"
  value = var.campaign_creative_upload_token_secret

  tags = {
    Project = var.project_name
  }
}
