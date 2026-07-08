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
