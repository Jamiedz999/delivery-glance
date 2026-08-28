# Concrete values for the one live deployment. They are not secret — the account id, the region,
# the Elastic IP and the admin's SSH address are all discoverable from the running box — so they
# sit in terraform.tfvars in the repo rather than behind a prompt. Nothing here is a credential.

variable "region" {
  description = "AWS region the Core box runs in."
  type        = string
  default     = "us-east-1"
}

variable "account_id" {
  description = "AWS account the Core deployment lives in. Used to build ARNs."
  type        = string
}

variable "github_repo" {
  description = "owner/repo whose GitHub Actions may assume the push role."
  type        = string
}

variable "github_repo_immutable" {
  description = <<-EOT
    The immutable-subject form of the repo, owner@ownerid/repo@repoid. This repo has immutable OIDC
    subjects enabled, so the Actions token 'sub' carries this, not the plain owner/repo. Both forms
    are listed in the trust policy so the push keeps working; see iam_gha.tf.
  EOT
  type        = string
}

variable "admin_ssh_cidr" {
  description = "The single /32 allowed to SSH the box on port 22 (the admin's address)."
  type        = string
}

variable "instance_ami" {
  description = "AMI the live instance was launched from (Ubuntu 24.04 arm64). Pinned so plan is a no-op."
  type        = string
}

variable "vpc_id" {
  description = "The account's default VPC, which the security group and instance live in."
  type        = string
}

variable "subnet_id" {
  description = "The subnet the instance was launched into (determines its availability zone)."
  type        = string
}
