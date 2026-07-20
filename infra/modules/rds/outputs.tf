# modules/rds/outputs.tf
output "endpoint" { value = aws_db_instance.main.endpoint }
output "db_name" { value = aws_db_instance.main.db_name }
output "identifier" { value = aws_db_instance.main.identifier }
