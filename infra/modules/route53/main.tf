# ============================================================
# modules/route53/main.tf
# 기존 호스팅 영역(root_domain)에 ALB Alias 레코드 추가
# 가비아 등 외부 등록기관에서 Route53 네임서버로 위임된 상태를 전제로 함
# ============================================================

data "aws_route53_zone" "main" {
  name         = var.root_domain
  private_zone = false
}

resource "aws_route53_record" "alb_alias" {
  zone_id = data.aws_route53_zone.main.zone_id
  name    = var.record_name
  type    = "A"

  alias {
    name                   = var.alb_dns_name
    zone_id                = var.alb_zone_id
    evaluate_target_health = true
  }
}
