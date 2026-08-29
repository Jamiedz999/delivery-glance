# All null when enable_notify is false, so a disabled apply exposes nothing.

output "queue_url" {
  description = "The work queue the relay publishes transition ids to and the Lambda consumes."
  value       = var.enable_notify ? aws_sqs_queue.work[0].url : null
}

output "dlq_arn" {
  description = "The dead-letter queue a message lands in after 3 failed receives."
  value       = var.enable_notify ? aws_sqs_queue.dlq[0].arn : null
}

output "lambda_function_arn" {
  description = "The notification-sender function the work queue triggers."
  value       = var.enable_notify ? aws_lambda_function.notification_sender[0].arn : null
}

output "callback_token_parameter" {
  description = "SSM parameter name holding the callback token (the value is a SecureString, not output)."
  value       = var.enable_notify ? aws_ssm_parameter.callback_token[0].name : null
}
