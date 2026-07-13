# modules/ssm_params/variables.tf
variable "project_name" { type = string }
variable "db_url"       { type = string }
variable "db_username"  { type = string }
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
variable "kakao_redirect_uri"  { type = string }
variable "app_frontend_url"    { type = string }
variable "jwt_secret" {
  type      = string
  sensitive = true
}

# ──────────────── Vision Summary SQS ────────────────
variable "sqs_queue_url" { type = string } # 큐 URL 자체는 비밀이 아니라 접근은 IAM으로 통제됨

# ──────────────── 사업자등록증 검증 (DV-112) ────────────────
# 국세청 사업자등록정보 진위확인 API (공공데이터포털 odcloud) 서비스 키.
# 없으면 사업자등록증 검증이 500(BUSINESS_500_001)으로 막힌다.
variable "nts_service_key" {
  type      = string
  sensitive = true
}

# 업로드 토큰 HMAC 서명 키. JWT 서명 키와 반드시 분리한다 - 용도가 다른 키를 공유하면
# 한쪽이 유출됐을 때 다른 쪽까지 무너진다. 최소 256비트 무작위 값을 넣는다.
#
# 교체하면 최대 1시간(token-ttl-seconds) 동안 기존 업로드 토큰이 무효가 되어, 그 사이 팀 생성을
# 진행 중이던 사용자는 파일을 다시 업로드해야 한다 - 사용량이 적은 시간대에 교체한다.
variable "business_registration_upload_token_secret" {
  type      = string
  sensitive = true
}

# OCR Lambda 함수 이름. 백엔드가 SDK로 직접 invoke한다 (API Gateway를 거치지 않는다).
variable "business_registration_ocr_function" { type = string }

# 사업자등록증 원본을 두는 S3 버킷 이름. 버킷 이름 자체는 비밀이 아니다 - 버킷은 public access를
# 전부 차단해 두었고, 접근은 IAM으로만 통제된다.
variable "business_registration_bucket" { type = string }
