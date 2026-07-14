# modules/ec2/variables.tf
variable "project_name"          { type = string }
variable "vpc_id"                { type = string }
variable "public_subnet_ids"     { type = list(string) }
variable "alb_security_group_id" { type = string }
variable "target_group_arn"      { type = string }
variable "instance_type"         { type = string }
variable "key_name"              { type = string }
variable "s3_bucket_arn"         { type = string }
variable "ami_id"                { type = string }

# OCR Lambda의 ARN (DV-112). 백엔드가 SDK로 직접 invoke한다.
# Lambda는 Terraform으로 관리하지 않고(별도 레포에서 배포) ARN만 받아 호출 권한을 붙인다.
# 빈 문자열이면 정책을 만들지 않는다 - Lambda 배포 전에도 apply가 되도록 하기 위해서다.
variable "business_registration_ocr_function_arn" {
  type    = string
  default = ""
}

# 사업자등록증이 저장되는 S3 키 prefix (DV-112).
# S3 권한을 이 prefix 안으로만 제한하는 데 쓴다 - 버킷을 Vision 이미지와 공유하기 때문이다.
# 애플리케이션의 app.upload.business-registration.key-prefix와 반드시 같은 값이어야 한다.
# 값이 어긋나면 업로드가 AccessDenied로 실패한다.
variable "business_registration_key_prefix" {
  type    = string
  default = "team-registrations/"
}

# 캠페인 소재가 저장되는 S3 키 prefix.
# 이미 운영 중인 presigned PUT 업로드(CampaignCreativeUploadService의 KEY_PREFIX)와 같은 값이어야
# 한다 - presigned URL은 서명한 EC2 역할의 권한으로 실행되므로, 여기서 어긋나면 업로드가 403이 된다.
variable "campaign_creative_key_prefix" {
  type    = string
  default = "campaign-creatives/"
}
