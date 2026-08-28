# Concrete, non-secret facts about the one live deployment — the same kind core/terraform.tfvars
# records. enable_proof stays false here: turning proof on is a deliberate act, done with
# `terraform apply -var enable_proof=true` (or by flipping it here), not a checked-in default.

region     = "us-east-1"
account_id = "814654818352"

# The Core box (core output instance_id) and its role (core/iam_ec2.tf), attached to by name.
instance_id        = "i-0746a20b7f36b195c"
instance_role_name = "delivery-glance-ec2"

# The public demo host: the CORS origin and the Lambda's callback base.
app_base_url = "https://delivery-glance.duckdns.org"
