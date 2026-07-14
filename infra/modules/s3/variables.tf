# modules/s3/variables.tf
variable "project_name" { type = string }

variable "campaign_creative_cors_origins" {
  description = "campaign-creatives/* presigned PUT 업로드를 브라우저에서 호출할 origin 목록 (프론트, Swagger)"
  type        = list(string)
}
