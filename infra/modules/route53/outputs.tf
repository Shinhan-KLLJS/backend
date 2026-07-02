# modules/route53/outputs.tf
output "fqdn" { value = aws_route53_record.alb_alias.fqdn }
