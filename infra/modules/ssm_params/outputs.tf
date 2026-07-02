# modules/ssm_params/outputs.tf
output "db_url_name"      { value = aws_ssm_parameter.db_url.name }
output "db_username_name" { value = aws_ssm_parameter.db_username.name }
output "db_password_name" { value = aws_ssm_parameter.db_password.name }
