# ============================================================
# outputs.tf  —  terraform apply 후 출력되는 값
# terraform output 명령으로 언제든 다시 확인 가능
# ============================================================

output "alb_dns_name" {
  description = "ALB DNS 주소 (Spring Boot API 접근 주소)"
  value       = module.alb.dns_name
}

output "rds_endpoint" {
  description = "RDS 엔드포인트 (Spring application.yml에 입력)"
  value       = module.rds.endpoint
}

output "sqs_queue_url" {
  description = "SQS 큐 URL (로컬 Vision Python 코드에 입력)"
  value       = module.sqs.queue_url
}

output "ec2_private_ip" {
  description = "EC2 프라이빗 IP"
  value       = module.ec2.private_ip
}

output "vpc_id" {
  description = "VPC ID"
  value       = module.vpc.vpc_id
}

output "api_domain" {
  description = "API 접근용 커스텀 도메인"
  value       = module.route53.fqdn
}

# ──────────── 적용 후 해야 할 일 안내 ────────────
output "next_steps" {
  description = "terraform apply 완료 후 설정 가이드"
  value = join("\n", [
    "✅ 인프라 생성 완료! 다음을 설정하세요:",
    "",
    "1. Spring Boot application.yml:",
    "   spring.datasource.url=jdbc:mysql://${module.rds.endpoint}/${var.db_name}",
    "   cloud.aws.sqs.queue-url=${module.sqs.queue_url}",
    "",
    "2. 로컬 Vision Python (.env):",
    "   SQS_QUEUE_URL=${module.sqs.queue_url}",
    "   AWS_DEFAULT_REGION=ap-northeast-2",
    "",
    "3. API 접근 주소:",
    "   https://${module.route53.fqdn}/api/...",
    "",
    "4. Vercel 환경변수:",
    "   NEXT_PUBLIC_API_URL=https://${module.route53.fqdn}",
  ])
}
