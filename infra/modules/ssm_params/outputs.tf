# modules/ssm_params/outputs.tf
output "db_url_name"      { value = aws_ssm_parameter.db_url.name }
output "db_username_name" { value = aws_ssm_parameter.db_username.name }
output "db_password_name" { value = aws_ssm_parameter.db_password.name }
output "kakao_rest_api_key_name"  { value = aws_ssm_parameter.kakao_rest_api_key.name }
output "kakao_client_secret_name" { value = aws_ssm_parameter.kakao_client_secret.name }
output "kakao_redirect_uri_name"  { value = aws_ssm_parameter.kakao_redirect_uri.name }
output "app_frontend_url_name"    { value = aws_ssm_parameter.app_frontend_url.name }
output "jwt_secret_name"          { value = aws_ssm_parameter.jwt_secret.name }
output "sqs_queue_url_name"       { value = aws_ssm_parameter.sqs_queue_url.name }
