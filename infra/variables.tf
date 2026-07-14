# ============================================================
# variables.tf  —  변수 선언
# 실제 값은 terraform.tfvars 에 작성하세요.
# ============================================================

variable "aws_region" {
  description = "AWS 리전 (서울)"
  type        = string
  default     = "ap-northeast-2"
}

variable "project_name" {
  description = "프로젝트 이름 (리소스 이름 prefix로 사용)"
  type        = string
  default     = "vision"
}

# ──────────────── VPC ────────────────
variable "vpc_cidr" {
  description = "VPC CIDR 블록"
  type        = string
  default     = "10.0.0.0/16"
}

variable "availability_zones" {
  description = "사용할 가용 영역 목록"
  type        = list(string)
  default     = ["ap-northeast-2a", "ap-northeast-2c"]
}

# ──────────────── 도메인 / ACM ────────────────
variable "domain_name" {
  description = "ALB에 연결할 전체 도메인 (예: api.loovi.my)"
  type        = string
  default     = "api.loovi.my"
}

variable "root_domain" {
  description = "Route53 호스팅 영역의 루트 도메인 (예: loovi.my)"
  type        = string
  default     = "loovi.my"
}

variable "acm_certificate_arn" {
  description = "ALB HTTPS 리스너용 ACM 인증서 ARN (ALB와 같은 리전 ap-northeast-2에서 발급된 것)"
  type        = string
}

# ──────────────── EC2 ────────────────
variable "ec2_instance_type" {
  description = "EC2 인스턴스 타입"
  type        = string
  default     = "t3.small"
}

variable "ec2_ami_id" {
  description = "EC2에 사용할 AMI ID. most_recent 자동조회 대신 고정값을 쓴다 - 관련없는 apply 도중 AMI가 갱신되어 인스턴스가 재생성되는 사고를 막기 위함. 의도적으로 업그레이드할 때만 이 값을 바꾼다 (aws ec2 describe-images --owners amazon --filters \"Name=name,Values=al2023-ami-*-x86_64\" --query \"reverse(sort_by(Images,&CreationDate))[0].ImageId\")."
  type        = string
}

variable "key_name" {
  description = "EC2 SSH 키페어 이름 (AWS 콘솔에서 미리 생성 필요)"
  type        = string
}

# ──────────────── RDS ────────────────
variable "db_instance_class" {
  description = "RDS 인스턴스 클래스"
  type        = string
  default     = "db.t3.micro"
}

variable "db_name" {
  description = "데이터베이스 이름"
  type        = string
  default     = "visiondb"
}

variable "db_username" {
  description = "DB 마스터 사용자 이름"
  type        = string
  default     = "visionadmin"
}

variable "db_password" {
  description = "DB 마스터 비밀번호 (terraform.tfvars에서 설정)"
  type        = string
  sensitive   = true # 로그/출력에서 숨김
}

# ──────────────── 카카오 로그인 / 서비스 JWT ────────────────
variable "kakao_rest_api_key" {
  description = "카카오 디벨로퍼스 REST API 키 (client-id)"
  type        = string
}

variable "kakao_client_secret" {
  description = "카카오 디벨로퍼스 Client Secret"
  type        = string
  sensitive   = true
}

variable "kakao_redirect_uri" {
  description = "카카오 콜백 URI (카카오 디벨로퍼스에 등록한 값과 정확히 일치해야 함)"
  type        = string
  default     = "https://api.loovi.my/login/oauth2/code/kakao"
}

variable "app_frontend_url" {
  description = "로그인 성공/실패 후 리다이렉트할 프론트엔드 주소 (실제 브라우저 접속 origin과 정확히 일치해야 함)"
  type        = string
}

variable "refresh_cookie_same_site" {
  description = <<-EOT
    refresh_token 쿠키의 SameSite 값 (Lax/Strict/None). 기본값 Lax를 유지한다 - 프론트가
    API와 같은 site일 때(운영) CSRF 방어에 가장 안전하다. 로컬 프론트(localhost)로 이
    서버를 cross-site로 테스트할 때만(issue #31) terraform.tfvars에서 None으로 임시
    오버라이드한다 - 실제 배포 전 반드시 Lax로 되돌려야 한다.
  EOT
  type        = string
  default     = "Lax"
}

variable "vercel_apex_ip" {
  description = "루트 도메인(root_domain)을 Vercel에 연결하기 위한 A 레코드 값 (Vercel Domains 화면에서 확인)"
  type        = string
}

variable "vercel_www_cname_target" {
  description = "www 서브도메인을 Vercel에 연결하기 위한 CNAME 값 (Vercel Domains 화면에서 확인, 프로젝트마다 다름)"
  type        = string
}

variable "jwt_secret" {
  description = "서비스 JWT 서명용 HMAC 비밀키 (openssl rand -base64 32 등으로 생성, 32바이트 이상)"
  type        = string
  sensitive   = true
}

# ──────────────── 캠페인 소재 업로드 ────────────────
variable "campaign_creative_upload_token_secret" {
  description = "캠페인 소재 업로드 토큰 서명용 HMAC 비밀키 (JWT 서명 키와 별도 - openssl rand -base64 32 등으로 생성)"
  type        = string
  sensitive   = true
}

# ──────────────── 사업자등록증 업로드 (DV-112) ────────────────

variable "business_registration_upload_token_secret" {
  description = <<-EOT
    사업자등록증 업로드 토큰 HMAC 서명 키 (openssl rand -base64 32, 32바이트 이상).
    JWT 서명 키(jwt_secret)와 반드시 다른 값을 쓴다 - 용도가 다른 키를 공유하면 한쪽이 유출됐을 때
    다른 쪽까지 무너진다. 이 키가 유출되면 남이 업로드한 사업자등록증 파일을 자기 것처럼 참조해
    팀을 만들 수 있다.
  EOT
  type        = string
  sensitive   = true
}

variable "business_registration_ocr_function" {
  description = "OCR Lambda 함수 이름 (백엔드가 SDK로 직접 invoke)"
  type        = string
}

variable "business_registration_ocr_function_arn" {
  description = <<-EOT
    OCR Lambda 함수 ARN. EC2에 lambda:InvokeFunction 권한을 이 함수 하나로 한정해 붙인다.
    Lambda는 Terraform으로 관리하지 않고 별도 레포(biz-eligibility)에서 배포하므로 ARN만 받는다.
    빈 문자열이면 호출 정책을 만들지 않는다 - Lambda 배포 전에도 apply가 되도록.
  EOT
  type        = string
  default     = ""
}
