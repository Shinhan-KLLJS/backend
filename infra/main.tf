# ============================================================
# main.tf  —  Vision 프로젝트 AWS 인프라 루트
# ============================================================
# 실행 순서:
#   1. terraform init
#   2. terraform plan
#   3. terraform apply
# ============================================================

terraform {
  required_version = ">= 1.6.0"

  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.0"
    }
  }
}

provider "aws" {
  region = var.aws_region
}

# ------------------------------------------------------------
# 모듈 1: VPC (네트워크 기반)
# Public subnet (ALB, EC2), Private subnet (RDS 전용)
# ------------------------------------------------------------
module "vpc" {
  source = "./modules/vpc"

  project_name       = var.project_name
  vpc_cidr           = var.vpc_cidr
  availability_zones = var.availability_zones
}

# ------------------------------------------------------------
# 모듈 1-1: S3 (이미지/파일 업로드)
# 퍼블릭 접근 차단, IAM 정책으로만 접근 제어
# ------------------------------------------------------------
module "s3" {
  source = "./modules/s3"

  project_name = var.project_name
}

# ------------------------------------------------------------
# 모듈 2: SQS
# 로컬 Vision, EC2 모두 인터넷을 통해 직접 접근
# ------------------------------------------------------------
module "sqs" {
  source = "./modules/sqs"

  project_name           = var.project_name
  producer_principal_arn = aws_iam_user.ai_producer.arn
  consumer_principal_arn = module.ec2.role_arn
}

# ------------------------------------------------------------
# 모듈 3: ALB (Application Load Balancer)
# Public subnet에 위치, HTTPS 수신 후 EC2로 HTTP 전달
# ------------------------------------------------------------
module "alb" {
  source = "./modules/alb"

  project_name        = var.project_name
  vpc_id              = module.vpc.vpc_id
  public_subnet_ids   = module.vpc.public_subnet_ids
  acm_certificate_arn = var.acm_certificate_arn
}

# ------------------------------------------------------------
# 모듈 3-1: Route53 (도메인 → ALB 연결)
# 가비아에서 구매한 도메인의 네임서버가 이 Route53 호스팅 영역으로 위임된 상태
# ------------------------------------------------------------
module "route53" {
  source = "./modules/route53"

  root_domain  = var.root_domain
  record_name  = var.domain_name
  alb_dns_name = module.alb.dns_name
  alb_zone_id  = module.alb.zone_id
}

# ------------------------------------------------------------
# 모듈 3-2: Route53 (루트/www 도메인 → Vercel 프론트엔드 연결)
# api.loovi.my와 같은 호스팅 영역이라 위 module.route53과 zone_id를 공유한다.
# loovi.my(루트)는 Vercel 쪽에서 www.loovi.my로 308 리다이렉트되도록 이미 설정됨 —
# 실제 프로덕션 프론트 origin은 www.loovi.my (var.app_frontend_url과 일치해야 함).
# ------------------------------------------------------------
resource "aws_route53_record" "frontend_apex" {
  zone_id = module.route53.zone_id
  name    = var.root_domain
  type    = "A"
  ttl     = 300
  records = [var.vercel_apex_ip]
}

resource "aws_route53_record" "frontend_www" {
  zone_id = module.route53.zone_id
  name    = "www.${var.root_domain}"
  type    = "CNAME"
  ttl     = 300
  records = [var.vercel_www_cname_target]
}

# ------------------------------------------------------------
# 모듈 4: EC2 (Spring Boot)
# Public subnet에 위치(보안그룹으로 ALB 외 인바운드 차단), ALB Target Group에 등록
# ------------------------------------------------------------
module "ec2" {
  source = "./modules/ec2"

  project_name      = var.project_name
  vpc_id            = module.vpc.vpc_id
  public_subnet_ids = module.vpc.public_subnet_ids
  alb_security_group_id = module.alb.security_group_id
  target_group_arn   = module.alb.target_group_arn
  instance_type      = var.ec2_instance_type
  ami_id             = var.ec2_ami_id
  key_name           = var.key_name
  s3_bucket_arn      = module.s3.bucket_arn

  business_registration_ocr_function_arn = var.business_registration_ocr_function_arn
}

# ------------------------------------------------------------
# 모듈 5: RDS (MySQL)
# Private subnet에 위치, EC2 Security Group만 접근 허용
# ------------------------------------------------------------
module "rds" {
  source = "./modules/rds"

  project_name          = var.project_name
  vpc_id                = module.vpc.vpc_id
  private_subnet_ids    = module.vpc.private_subnet_ids
  ec2_security_group_id = module.ec2.security_group_id
  db_name               = var.db_name
  db_username           = var.db_username
  db_password           = var.db_password
  db_instance_class     = var.db_instance_class
}

# ------------------------------------------------------------
# 모듈 6: SSM Parameter Store
# EC2가 배포 시 SSM Run Command로 조회할 DB 접속정보/카카오·JWT 설정 저장
# ------------------------------------------------------------
module "ssm_params" {
  source = "./modules/ssm_params"

  project_name        = var.project_name
  db_url              = "jdbc:mysql://${module.rds.endpoint}/${var.db_name}"
  db_username         = var.db_username
  db_password         = var.db_password
  kakao_rest_api_key  = var.kakao_rest_api_key
  kakao_client_secret = var.kakao_client_secret
  kakao_redirect_uri  = var.kakao_redirect_uri
  app_frontend_url    = var.app_frontend_url
  jwt_secret          = var.jwt_secret
  sqs_queue_url       = module.sqs.queue_url

  nts_service_key                           = var.nts_service_key
  business_registration_upload_token_secret = var.business_registration_upload_token_secret
  business_registration_ocr_function        = var.business_registration_ocr_function
  # 버킷 이름은 tfvars로 받지 않고 s3 모듈이 실제로 만든 버킷에서 그대로 가져온다 -
  # 사람이 옮겨 적으면서 어긋날 여지를 없앤다.
  business_registration_bucket = module.s3.bucket_name
}
