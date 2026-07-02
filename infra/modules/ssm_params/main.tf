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
