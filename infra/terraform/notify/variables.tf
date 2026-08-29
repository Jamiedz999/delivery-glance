# Off-band Recipient notification (Issue 51) provisioned on real AWS, gated behind one switch. This
# is the real-AWS twin of infra/localstack/init/20-notify.sh, which is the reproducible reference for
# what a deployment provisions by hand or here. Concrete, non-secret facts about the one live
# deployment sit in terraform.tfvars, exactly as core/ records the account id, region and instance —
# none of them is a credential. The callback token is not among them: Terraform generates it (see
# iam.tf). The SES sender address is not among them either: it is a per-operator input with no
# checked-in default, so no personal address ever enters the repository (see notify_email_source).

variable "enable_notify" {
  description = <<-EOT
    The one gate. false (the default) means this root creates nothing and the Core box's env is
    untouched — `terraform plan` shows no resources and the deployment holds no Recipient contact at
    all, exactly as deployment.md describes. true provisions the queue, DLQ, Lambda, trigger, SES
    identity and IAM, sets the app's delivery-glance.notification.* env on the box, and a re-apply
    turns the feature on.
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
  description = "AWS account the deployment lives in. Used to build ARNs and scope grants."
  type        = string
}

variable "instance_id" {
  description = "The Core instance whose on-box .env this stack updates when enabled."
  type        = string
}

variable "instance_role_name" {
  description = <<-EOT
    Name of the Core instance role (delivery-glance-ec2, from core/iam_ec2.tf). This stack attaches
    the relay's sqs:SendMessage grant and the callback-token read to it by name, without managing the
    role itself — so core/'s state and its no-op plan are untouched.
  EOT
  type        = string
}

variable "app_base_url" {
  description = <<-EOT
    The application's public base URL. It is the base the consumer Lambda calls back to at
    {url}/api/internal/notifications/{begin,sent}. Not secret — it is the public demo hostname.
  EOT
  type        = string
}

variable "notify_email_source" {
  description = <<-EOT
    The SES sender address for email notifications. No default on purpose: a real send needs a
    *verifiable* address (SES emails a confirmation link a human must click), so no address is baked
    into the repository — the operator supplies one at apply time, and the module verifies it as an
    SES identity. Left blank it is only valid while enable_notify is false; enabling with a blank
    address fails a precondition (see lambda.tf). SES also starts in sandbox — see the README for the
    manual production-access step. Not secret.
  EOT
  type        = string
  default     = ""
}
