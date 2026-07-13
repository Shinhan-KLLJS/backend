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
