# ============================================================
# dashboard.tf — 운영 모니터링용 CloudWatch Dashboard
#
# 인스턴스 1대·DB 1대·오토스케일링 없는 구조라, "이 서버가 살아있는가 /
# 터지기 직전인가"가 핵심이다. CPU 메트릭은 에이전트 없이 기본 제공되는
# 것만 쓴다 - 메모리/디스크는 CloudWatch Agent가 있어야 하는데, EC2
# 재생성(다운타임)이 필요해서 이번 범위에서는 뺐다.
# ============================================================

locals {
  dashboard_body = jsonencode({
    widgets = [
      { type = "text", x = 0, y = 0, width = 24, height = 1, properties = { markdown = "## 가용성 (ALB)" } },
      {
        type = "metric", x = 0, y = 1, width = 8, height = 6,
        properties = {
          title  = "타깃 헬스"
          region = var.aws_region
          metrics = [
            ["AWS/ApplicationELB", "HealthyHostCount", "TargetGroup", module.alb.target_group_arn_suffix, "LoadBalancer", module.alb.arn_suffix],
            [".", "UnHealthyHostCount", ".", ".", ".", "."]
          ]
          period = 60
          stat   = "Average"
        }
      },
      {
        type = "metric", x = 8, y = 1, width = 8, height = 6,
        properties = {
          title  = "5xx 카운트 (ALB vs 앱)"
          region = var.aws_region
          metrics = [
            ["AWS/ApplicationELB", "HTTPCode_ELB_5XX_Count", "LoadBalancer", module.alb.arn_suffix, { stat = "Sum" }],
            [".", "HTTPCode_Target_5XX_Count", ".", ".", { stat = "Sum" }]
          ]
          period = 60
        }
      },
      {
        type = "metric", x = 16, y = 1, width = 8, height = 6,
        properties = {
          title  = "응답 지연시간"
          region = var.aws_region
          metrics = [
            ["AWS/ApplicationELB", "TargetResponseTime", "TargetGroup", module.alb.target_group_arn_suffix, "LoadBalancer", module.alb.arn_suffix]
          ]
          period = 60
          stat   = "p99"
        }
      },

      { type = "text", x = 0, y = 7, width = 24, height = 1, properties = { markdown = "## EC2" } },
      {
        type = "metric", x = 0, y = 8, width = 8, height = 6,
        properties = {
          title  = "CPU 사용률"
          region = var.aws_region
          metrics = [
            ["AWS/EC2", "CPUUtilization", "InstanceId", module.ec2.instance_id]
          ]
          period = 60
          stat   = "Average"
        }
      },
      {
        type = "metric", x = 8, y = 8, width = 8, height = 6,
        properties = {
          title  = "CPU 크레딧 잔량 (버스터블 인스턴스 스로틀링 위험)"
          region = var.aws_region
          metrics = [
            ["AWS/EC2", "CPUCreditBalance", "InstanceId", module.ec2.instance_id]
          ]
          period = 300
          stat   = "Minimum"
        }
      },
      {
        type = "metric", x = 16, y = 8, width = 8, height = 6,
        properties = {
          title  = "인스턴스/시스템 상태 체크 실패"
          region = var.aws_region
          metrics = [
            ["AWS/EC2", "StatusCheckFailed_Instance", "InstanceId", module.ec2.instance_id, { stat = "Maximum" }],
            [".", "StatusCheckFailed_System", ".", ".", { stat = "Maximum" }]
          ]
          period = 60
        }
      },

      { type = "text", x = 0, y = 14, width = 24, height = 1, properties = { markdown = "## RDS" } },
      {
        type = "metric", x = 0, y = 15, width = 8, height = 6,
        properties = {
          title  = "CPU / 커넥션 수"
          region = var.aws_region
          metrics = [
            ["AWS/RDS", "CPUUtilization", "DBInstanceIdentifier", module.rds.identifier],
            [".", "DatabaseConnections", ".", ".", { yAxis = "right" }]
          ]
          period = 60
          stat   = "Average"
        }
      },
      {
        type = "metric", x = 8, y = 15, width = 8, height = 6,
        properties = {
          title  = "여유 메모리 / 디스크"
          region = var.aws_region
          metrics = [
            ["AWS/RDS", "FreeableMemory", "DBInstanceIdentifier", module.rds.identifier],
            [".", "FreeStorageSpace", ".", ".", { yAxis = "right" }]
          ]
          period = 300
          stat   = "Minimum"
        }
      },
      {
        type = "metric", x = 16, y = 15, width = 8, height = 6,
        properties = {
          title  = "Read/Write IOPS"
          region = var.aws_region
          metrics = [
            ["AWS/RDS", "ReadIOPS", "DBInstanceIdentifier", module.rds.identifier],
            [".", "WriteIOPS", ".", "."]
          ]
          period = 60
          stat   = "Average"
        }
      },

      { type = "text", x = 0, y = 21, width = 24, height = 1, properties = { markdown = "## SQS (Vision 큐)" } },
      {
        type = "metric", x = 0, y = 22, width = 12, height = 6,
        properties = {
          title  = "미처리 메시지 수 (백로그)"
          region = var.aws_region
          metrics = [
            ["AWS/SQS", "ApproximateNumberOfMessagesVisible", "QueueName", module.sqs.queue_name]
          ]
          period = 60
          stat   = "Maximum"
        }
      },
      {
        type = "metric", x = 12, y = 22, width = 12, height = 6,
        properties = {
          title  = "가장 오래된 미처리 메시지 나이 (초) - 컨슈머 지연 지표"
          region = var.aws_region
          metrics = [
            ["AWS/SQS", "ApproximateAgeOfOldestMessage", "QueueName", module.sqs.queue_name]
          ]
          period = 60
          stat   = "Maximum"
        }
      },
    ]
  })
}

resource "aws_cloudwatch_dashboard" "main" {
  dashboard_name = "${var.project_name}-overview"
  dashboard_body = local.dashboard_body
}
