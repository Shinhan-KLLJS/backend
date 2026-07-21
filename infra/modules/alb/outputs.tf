# modules/alb/outputs.tf
output "dns_name" { value = aws_lb.main.dns_name }
output "zone_id" { value = aws_lb.main.zone_id }
output "target_group_arn" { value = aws_lb_target_group.ec2.arn }
output "security_group_id" { value = aws_security_group.alb.id }

# CloudWatch 메트릭 dimension용 - 전체 ARN이 아니라 이 suffix 형식(app/이름/id,
# targetgroup/이름/id)을 써야 ALB/TargetGroup 메트릭을 조회할 수 있다.
output "arn_suffix" { value = aws_lb.main.arn_suffix }
output "target_group_arn_suffix" { value = aws_lb_target_group.ec2.arn_suffix }
