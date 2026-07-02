# modules/ec2/outputs.tf
output "instance_id"       { value = aws_instance.spring.id }
output "private_ip"        { value = aws_instance.spring.private_ip }
output "security_group_id" { value = aws_security_group.ec2.id }
