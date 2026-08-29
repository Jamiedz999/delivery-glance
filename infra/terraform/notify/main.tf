# Shared locals. `enabled` is the 0/1 count every resource here multiplies by, so the whole stack
# collapses to nothing when enable_notify is false.
locals {
  enabled = var.enable_notify ? 1 : 0

  # The work queue name is fixed — the same delivery-glance-notify the LocalStack init script and the
  # app's NOTIFY_QUEUE_URL agree on. The DLQ is that name plus -dlq, exactly as 20-notify.sh builds
  # it. Unlike the proof bucket, an SQS name is scoped to the account and region, so it needs no
  # account-id suffix to stay unique.
  queue_name = "delivery-glance-notify"
  dlq_name   = "${local.queue_name}-dlq"
}
