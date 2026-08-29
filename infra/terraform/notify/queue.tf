# The work queue and its dead-letter queue, built exactly as 20-notify.sh builds them against
# LocalStack: a work queue delivery-glance-notify with a redrive policy to delivery-glance-notify-dlq
# after 3 receives. The message body is a Status Change (transition) id and nothing more — no Courier
# coordinate ever enters the queue (ADR 13), and this module does not change that.
#
# The DLQ is declared first so its ARN is available for the work queue's redrive policy.
resource "aws_sqs_queue" "dlq" {
  count = local.enabled
  name  = local.dlq_name
}

resource "aws_sqs_queue" "work" {
  count = local.enabled
  name  = local.queue_name

  # A message that fails past maxReceiveCount goes to the DLQ. Because a failed send raises, SQS
  # retries; after 3 receives the message parks in the DLQ, never touching the delivery command that
  # produced it.
  redrive_policy = jsonencode({
    deadLetterTargetArn = aws_sqs_queue.dlq[0].arn
    maxReceiveCount     = 3
  })
}
