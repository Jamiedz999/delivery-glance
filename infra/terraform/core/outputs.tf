output "gha_ecr_role_arn" {
  description = "ARN GitHub Actions assumes to push and deploy. Set as the AWS_GHA_ROLE_ARN repo secret."
  value       = aws_iam_role.gha_ecr.arn
}

output "ecr_repository_url" {
  description = "Registry URL images are pushed to and pulled from."
  value       = aws_ecr_repository.delivery_glance.repository_url
}

output "instance_id" {
  description = "The one Core instance."
  value       = aws_instance.core.id
}

output "public_ip" {
  description = "The Elastic IP the DuckDNS hostname points at."
  value       = aws_eip.core.public_ip
}
