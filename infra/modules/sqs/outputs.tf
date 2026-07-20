# modules/sqs/outputs.tf
output "queue_url" { value = aws_sqs_queue.main.url }
output "queue_arn" { value = aws_sqs_queue.main.arn }
output "queue_name" { value = aws_sqs_queue.main.name }
output "dlq_queue_url" { value = aws_sqs_queue.dlq.url }
