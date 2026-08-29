# Concrete, non-secret facts about the one live deployment — the same kind core/terraform.tfvars
# records. enable_notify stays false here: turning notification on is a deliberate act, done with
# `terraform apply -var enable_notify=true -var notify_email_source=you@example.com` (or by flipping
# enable_notify here), not a checked-in default.

region     = "us-east-1"
account_id = "814654818352"

# The Core box (core output instance_id) and its role (core/iam_ec2.tf), attached to by name.
instance_id        = "i-0746a20b7f36b195c"
instance_role_name = "delivery-glance-ec2"

# The public demo host: the Lambda's callback base.
app_base_url = "https://delivery-glance.duckdns.org"

# notify_email_source is deliberately NOT set here. It needs a *verifiable* SES sender address, and
# no personal address belongs in the repository, so the operator supplies it at apply time:
#
#   terraform apply -var enable_notify=true -var notify_email_source=you@example.com
#
# SES then emails a confirmation link that must be clicked before the address can send, and the
# account starts in sandbox — see README.md for the manual production-access step.
