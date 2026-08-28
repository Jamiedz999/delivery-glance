output "state_bucket" {
  description = "Name of the S3 bucket holding Terraform state. Wire this into core/terraform.tf backend."
  value       = aws_s3_bucket.state.id
}

output "lock_table" {
  description = "Name of the DynamoDB lock table."
  value       = aws_dynamodb_table.lock.name
}
