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

# ──────────── CD 배포용 IAM 자격증명 (GitHub Secrets에 등록) ────────────
output "cd_deploy_access_key_id" {
  description = "GitHub Secrets의 AWS_ACCESS_KEY_ID에 등록"
  value       = aws_iam_access_key.cd_deploy.id
}

output "cd_deploy_secret_access_key" {
  description = "GitHub Secrets의 AWS_SECRET_ACCESS_KEY에 등록 (terraform output -raw cd_deploy_secret_access_key로 확인)"
  value       = aws_iam_access_key.cd_deploy.secret
  sensitive   = true
}

# ──────────── AI 담당자(로컬 Vision) SQS 발행용 IAM 자격증명 ────────────
output "ai_producer_access_key_id" {
  description = "AI 담당자 aws configure 시 Access Key ID"
  value       = aws_iam_access_key.ai_producer.id
}

output "ai_producer_secret_access_key" {
  description = "AI 담당자 aws configure 시 Secret Access Key (terraform output -raw ai_producer_secret_access_key로 확인)"
  value       = aws_iam_access_key.ai_producer.secret
  sensitive   = true
}

output "sqs_queue_url_for_ai" {
  description = "AI 담당자 코드에 넣을 SQS_QUEUE_URL 값"
  value       = module.sqs.queue_url
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
