# modules/rds/variables.tf
variable "project_name"          { type = string }
variable "vpc_id"                { type = string }
variable "private_subnet_ids"    { type = list(string) }
variable "ec2_security_group_id" { type = string }
variable "db_name"               { type = string }
variable "db_username"           { type = string }
variable "db_password" {
  type      = string
  sensitive = true
}
variable "db_instance_class"     { type = string }
