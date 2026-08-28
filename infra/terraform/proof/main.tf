# Shared locals. `enabled` is the 0/1 count every resource here multiplies by, so the whole stack
# collapses to nothing when enable_proof is false. `bucket_name` derives a globally-unique default.
locals {
  enabled = var.enable_proof ? 1 : 0

  # The globally-unique S3 name for the one deployment: the LocalStack demo's delivery-glance-proof
  # plus the account id, so a real bucket does not collide.
  bucket_name = "delivery-glance-proof-${var.account_id}"

  # Prefixes are a security boundary, not a convenience — the app and the Lambda agree on them in
  # server .../proof/ProofObjectKeys.java and lambda/proof-processor/handler.py. Named here once so
  # every policy statement that scopes to a prefix reads against the same list.
  raw_prefix        = "raw/"
  clean_prefix      = "clean/"
  thumbnail_prefix  = "thumb/"
  quarantine_prefix = "quarantine/"
}
