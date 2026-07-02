# modules/alb/variables.tf
variable "project_name"      { type = string }
variable "vpc_id"            { type = string }
variable "public_subnet_ids" { type = list(string) }
variable "acm_certificate_arn" {
  description = "ALB HTTPS 리스너에 붙일 ACM 인증서 ARN (ALB와 같은 리전에서 발급된 것이어야 함)"
  type        = string
}
