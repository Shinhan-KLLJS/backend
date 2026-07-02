# ============================================================
# modules/rds/main.tf
# RDS MySQL: private subnet, EC2에서만 접근
# ============================================================

# ──────────────── RDS Security Group ────────────────
resource "aws_security_group" "rds" {
  name        = "${var.project_name}-rds-sg"
  description = "RDS MySQL - Allow inbound from EC2 only"
  vpc_id      = var.vpc_id

  # EC2 Security Group에서 오는 MySQL 트래픽만 허용
  ingress {
    description     = "MySQL from EC2 only"
    from_port       = 3306
    to_port         = 3306
    protocol        = "tcp"
    security_groups = [var.ec2_security_group_id]
  }

  tags = {
    Name    = "${var.project_name}-rds-sg"
    Project = var.project_name
  }
}

# ──────────────── RDS 서브넷 그룹 ────────────────
# RDS는 Multi-AZ를 위해 최소 2개 서브넷 필요
resource "aws_db_subnet_group" "main" {
  name       = "${var.project_name}-rds-subnet-group"
  subnet_ids = var.private_subnet_ids

  tags = {
    Name    = "${var.project_name}-rds-subnet-group"
    Project = var.project_name
  }
}

# ──────────────── RDS 파라미터 그룹 ────────────────
resource "aws_db_parameter_group" "mysql" {
  name   = "${var.project_name}-mysql-params"
  family = "mysql8.0"

  # 한국어 인코딩 설정
  parameter {
    name  = "character_set_server"
    value = "utf8mb4"
  }

  parameter {
    name  = "collation_server"
    value = "utf8mb4_unicode_ci"
  }

  tags = {
    Name    = "${var.project_name}-mysql-params"
    Project = var.project_name
  }
}

# ──────────────── RDS 인스턴스 ────────────────
resource "aws_db_instance" "main" {
  identifier = "${var.project_name}-mysql"

  # 엔진
  engine         = "mysql"
  engine_version = "8.0.46" # ap-northeast-2에서 사용 가능한 버전 (aws rds describe-db-engine-versions로 확인)
  instance_class = var.db_instance_class

  # 스토리지
  allocated_storage     = 20      # GB
  max_allocated_storage = 100     # 오토스케일링 최대치
  storage_type          = "gp3"
  storage_encrypted     = true    # 암호화 필수

  # 데이터베이스
  db_name  = var.db_name
  username = var.db_username
  password = var.db_password

  # 네트워크
  db_subnet_group_name   = aws_db_subnet_group.main.name
  vpc_security_group_ids = [aws_security_group.rds.id]
  publicly_accessible    = false  # 외부 접근 완전 차단

  # 파라미터 그룹
  parameter_group_name = aws_db_parameter_group.mysql.name

  # 백업
  backup_retention_period = 1     # 프리티어 계정은 7일 불가 (FreeTierRestrictionError) → 1일로 제한
  backup_window           = "03:00-04:00"   # 새벽 3시 백업 (KST 12:00)
  maintenance_window      = "Mon:04:00-Mon:05:00"

  # 삭제 보호 (실수로 terraform destroy 해도 RDS는 보호)
  deletion_protection = false   # 프로덕션에서는 true로 변경!
  skip_final_snapshot = true    # 학습용. 프로덕션에서는 false

  tags = {
    Name    = "${var.project_name}-mysql"
    Project = var.project_name
  }
}
