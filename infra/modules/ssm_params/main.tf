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

resource "aws_ssm_parameter" "additional_allowed_origins" {
  name = "/${var.project_name}/prod/additional-allowed-origins"
  type = "String" # 콤마로 구분한 origin 목록일 뿐 비밀은 아니다
  # SSM Parameter Store는 빈 문자열 값을 거부한다 - 부가 origin이 없으면 공백 하나로 대신한다.
  # 앱 쪽(AllowedOriginsProperties)은 isBlank()로 걸러서 공백을 "없음"과 동일하게 취급한다.
  value = var.additional_allowed_origins != "" ? var.additional_allowed_origins : " "

  tags = {
    Project = var.project_name
  }
}

resource "aws_ssm_parameter" "refresh_cookie_same_site" {
  name  = "/${var.project_name}/prod/refresh-cookie-same-site"
  type  = "String" # Lax/Strict/None 중 하나일 뿐 비밀은 아니다
  value = var.refresh_cookie_same_site

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

# ──────────────── 사업자등록증 검증 (DV-112) ────────────────
# application-prod.yml이 이 값들을 기본값 없이 요구한다 - 없으면 애플리케이션 기동 자체가 실패한다.
# 조용히 잘못된 값으로 뜨는 것보다 배포가 실패하는 편이 낫다.

resource "aws_ssm_parameter" "business_registration_upload_token_secret" {
  name  = "/${var.project_name}/prod/business-registration-upload-token-secret"
  type  = "SecureString"
  value = var.business_registration_upload_token_secret

  tags = {
    Project = var.project_name
  }
}

resource "aws_ssm_parameter" "business_registration_ocr_function" {
  name  = "/${var.project_name}/prod/business-registration-ocr-function"
  type  = "String" # 함수 이름 자체는 비밀이 아니다. 호출 권한은 IAM으로 통제된다
  value = var.business_registration_ocr_function

  tags = {
    Project = var.project_name
  }
}

resource "aws_ssm_parameter" "business_registration_bucket" {
  name  = "/${var.project_name}/prod/business-registration-bucket"
  type  = "String" # 버킷 이름 자체는 비밀이 아니다. 버킷은 public access 차단 + IAM으로만 접근한다
  value = var.business_registration_bucket

  tags = {
    Project = var.project_name
  }
}
