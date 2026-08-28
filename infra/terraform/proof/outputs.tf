# All null when enable_proof is false, so a disabled apply exposes nothing.

output "bucket_name" {
  description = "The private proof bucket the app presigns against and the Lambda reads and writes."
  value       = var.enable_proof ? aws_s3_bucket.proof[0].id : null
}

output "lambda_function_arn" {
  description = "The proof-processor function the raw/ trigger invokes."
  value       = var.enable_proof ? aws_lambda_function.proof_processor[0].arn : null
}

output "callback_token_parameter" {
  description = "SSM parameter name holding the callback token (the value is a SecureString, not output)."
  value       = var.enable_proof ? aws_ssm_parameter.callback_token[0].name : null
}
