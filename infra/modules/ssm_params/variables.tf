# modules/ssm_params/variables.tf
variable "project_name" { type = string }
variable "db_url"       { type = string }
variable "db_username"  { type = string }
variable "db_password" {
  type      = string
  sensitive = true
}
