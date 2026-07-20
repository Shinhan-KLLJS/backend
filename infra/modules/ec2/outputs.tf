# modules/ec2/outputs.tf
output "instance_id"       { value = aws_instance.spring.id }
output "instance_arn"      { value = aws_instance.spring.arn }
output "private_ip"        { value = aws_instance.spring.private_ip }
output "security_group_id" { value = aws_security_group.ec2.id }
output "role_arn"          { value = aws_iam_role.ec2.arn }
output "log_group_name"    { value = aws_cloudwatch_log_group.app.name }
