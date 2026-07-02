# ============================================================
# iam_cd.tf
# GitHub Actions(cd.yml)가 SSM Run Command로 배포하기 위한 최소권한 IAM 사용자
# ============================================================

resource "aws_iam_user" "cd_deploy" {
  name = "${var.project_name}-cd-deploy"
}

resource "aws_iam_user_policy" "cd_deploy" {
  name = "${var.project_name}-cd-deploy-policy"
  user = aws_iam_user.cd_deploy.name

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Sid      = "SendCommandToDeployTarget"
        Effect   = "Allow"
        Action   = "ssm:SendCommand"
        Resource = [
          "arn:aws:ssm:*::document/AWS-RunShellScript",
          module.ec2.instance_arn
        ]
      },
      {
        Sid      = "ReadCommandResult"
        Effect   = "Allow"
        Action   = ["ssm:GetCommandInvocation", "ssm:ListCommandInvocations"]
        Resource = "*"
      },
      {
        Sid      = "FindInstance"
        Effect   = "Allow"
        Action   = "ec2:DescribeInstances"
        Resource = "*"
      }
    ]
  })
}

resource "aws_iam_access_key" "cd_deploy" {
  user = aws_iam_user.cd_deploy.name
}
