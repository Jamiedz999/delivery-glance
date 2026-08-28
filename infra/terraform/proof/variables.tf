# Proof of delivery (Issue 50) provisioned on real AWS, gated behind one switch. This is the
# real-AWS twin of infra/localstack/init/10-proof.sh, which is the reproducible reference for what a
# deployment provisions by hand or here. Concrete, non-secret facts about the one live deployment
# sit in terraform.tfvars, exactly as core/ records the account id, region and instance — none of
# them is a credential. The callback token is not among them: Terraform generates it (see iam.tf).

variable "enable_proof" {
  description = <<-EOT
    The one gate. false (the default) means this root creates nothing and the Core box's env is
    untouched — `terraform plan` shows no resources and the deployment stays byte-for-byte what
    deployment.md §9 describes. true provisions the bucket, Lambda, trigger and IAM, sets the app's
    delivery-glance.proof.* env on the box, and a re-apply turns the feature on.
  EOT
  type        = bool
  default     = false
}

variable "region" {
  description = "AWS region the Core box and this stack run in."
  type        = string
  default     = "us-east-1"
}

variable "account_id" {
  description = "AWS account the deployment lives in. Used to build ARNs and scope the S3 invoke."
  type        = string
}

variable "instance_id" {
  description = "The Core instance whose on-box .env this stack updates when enabled."
  type        = string
}

variable "instance_role_name" {
  description = <<-EOT
    Name of the Core instance role (delivery-glance-ec2, from core/iam_ec2.tf). This stack attaches
    the application's presign policy and the callback-token read to it by name, without managing the
    role itself — so core/'s state and its no-op plan are untouched.
  EOT
  type        = string
}

variable "app_base_url" {
  description = <<-EOT
    The application's public base URL. It is the CORS origin browser uploads are allowed from and
    the base the Lambda calls back to at {url}/api/internal/proof-processed. Not secret — it is the
    public demo hostname.
  EOT
  type        = string
}
