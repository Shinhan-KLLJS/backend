# modules/ssm_params/variables.tf
variable "project_name" { type = string }
variable "db_url" { type = string }
variable "db_username" { type = string }
variable "db_password" {
  type      = string
  sensitive = true
}

# ──────────────── 카카오 로그인 / 서비스 JWT ────────────────
variable "kakao_rest_api_key" { type = string } # client_id 역할, 인가 URL에 노출되므로 비밀은 아니지만 SSM으로 일원화
variable "kakao_client_secret" {
  type      = string
  sensitive = true
}
variable "kakao_redirect_uri" { type = string }
variable "app_frontend_url" { type = string }
variable "jwt_secret" {
  type      = string
  sensitive = true
}

# ──────────────── Vision Summary SQS ────────────────
variable "sqs_queue_url" { type = string } # 큐 URL 자체는 비밀이 아니라 접근은 IAM으로 통제됨

# ──────────────── 캠페인 소재 업로드 ────────────────
variable "campaign_creative_bucket" { type = string }          # 기존 업로드 버킷 재사용, 새 버킷 아님
variable "campaign_creative_public_base_url" { type = string } # campaign-creatives/* 공개 조회용 버킷 origin
variable "campaign_creative_upload_token_secret" {
  type      = string
  sensitive = true
}
