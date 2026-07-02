# modules/route53/variables.tf
variable "root_domain" {
  description = "Route53 호스팅 영역 도메인 (예: loovi.my)"
  type        = string
}
variable "record_name" {
  description = "ALB에 연결할 전체 도메인 (예: api.loovi.my)"
  type        = string
}
variable "alb_dns_name" { type = string }
variable "alb_zone_id"  { type = string }
