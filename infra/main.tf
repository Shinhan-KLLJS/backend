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
# Public subnet (ALB), Private subnet (EC2, RDS, VPC Endpoint)
# ------------------------------------------------------------
module "vpc" {
  source = "./modules/vpc"

  project_name       = var.project_name
  vpc_cidr           = var.vpc_cidr
  availability_zones = var.availability_zones
}

# ------------------------------------------------------------
# 모듈 2: SQS + VPC Endpoint
# 로컬 Vision → SQS (인터넷), EC2 → SQS (VPC Endpoint 경유)
# ------------------------------------------------------------
module "sqs" {
  source = "./modules/sqs"

  project_name       = var.project_name
  vpc_id             = module.vpc.vpc_id
  private_subnet_ids = module.vpc.private_subnet_ids
  ec2_security_group_id = module.ec2.security_group_id
}

# ------------------------------------------------------------
# 모듈 3: ALB (Application Load Balancer)
# Public subnet에 위치, HTTPS 수신 후 EC2로 HTTP 전달
# ------------------------------------------------------------
module "alb" {
  source = "./modules/alb"

  project_name      = var.project_name
  vpc_id            = module.vpc.vpc_id
  public_subnet_ids = module.vpc.public_subnet_ids
}

# ------------------------------------------------------------
# 모듈 4: EC2 (Spring Boot)
# Private subnet에 위치, ALB Target Group에 등록
# ------------------------------------------------------------
module "ec2" {
  source = "./modules/ec2"

  project_name       = var.project_name
  vpc_id             = module.vpc.vpc_id
  private_subnet_ids = module.vpc.private_subnet_ids
  alb_security_group_id = module.alb.security_group_id
  target_group_arn   = module.alb.target_group_arn
  instance_type      = var.ec2_instance_type
  key_name           = var.key_name
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
