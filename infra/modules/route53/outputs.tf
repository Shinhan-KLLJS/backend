# modules/route53/outputs.tf
output "fqdn" { value = aws_route53_record.alb_alias.fqdn }
output "zone_id" { value = data.aws_route53_zone.main.zone_id }
