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

# ──────────────── EC2 ────────────────
variable "ec2_instance_type" {
  description = "EC2 인스턴스 타입"
  type        = string
  default     = "t3.small"
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
  sensitive   = true  # 로그/출력에서 숨김
}
