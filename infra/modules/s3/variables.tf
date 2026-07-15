# modules/s3/variables.tf
variable "project_name" { type = string }

variable "campaign_creative_cors_origins" {
  description = "campaign-creatives/* presigned PUT 업로드를 브라우저에서 호출할 origin 목록 (프론트, Swagger)"
  type        = list(string)
}

variable "business_registration_ocr_lambda_role_arn" {
  description = "OCR Lambda 실행 역할 ARN. team-registrations/* 읽기를 이 역할 하나로 한정해 허용한다."
  type        = string
}
