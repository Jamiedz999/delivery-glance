# Setting the app's notification env on the box.
#
# The box's /home/ubuntu/app/.env is hand-managed and read by compose.prod.yaml. Rather than replace
# the instance (which would take its Postgres volume and certificate down — core/ ignores AMI and
# user_data changes for exactly this reason), this upserts the three delivery-glance.notification.*
# vars the application reads — NOTIFY_QUEUE_URL, NOTIFY_REGION, NOTIFY_CALLBACK_TOKEN — over the same
# SSM Run Command path the deploy workflow already uses, then recreates the app container.
#
# NOTIFY_EMAIL_SOURCE is deliberately not here: the application never sends mail, so it is set on the
# Lambda (lambda.tf), not the box. The queue's SQS credentials are not here either — in production the
# app uses the instance role (app_send in iam.tf), and the real AWS SQS endpoint, so the demo-only
# NOTIFY_ENDPOINT_OVERRIDE / NOTIFY_ACCESS_KEY_ID / NOTIFY_SECRET_ACCESS_KEY stay blank.
#
# It re-runs whenever the queue URL, region or token changes — never when enable_notify is false. The
# token itself is not passed in the command; the on-box script reads it from SSM (iam.tf grants the
# box that one parameter), so the secret stays out of the command's logged parameters.
resource "terraform_data" "box_env" {
  count = local.enabled

  triggers_replace = [
    aws_sqs_queue.work[0].url,
    var.region,
    aws_ssm_parameter.callback_token[0].name,
    random_password.callback_token[0].result,
  ]

  provisioner "local-exec" {
    command = "${path.module}/scripts/apply-box-env.sh"
    environment = {
      INSTANCE_ID      = var.instance_id
      AWS_REGION       = var.region
      NOTIFY_QUEUE_URL = aws_sqs_queue.work[0].url
      NOTIFY_REGION    = var.region
      TOKEN_PARAM_NAME = aws_ssm_parameter.callback_token[0].name
    }
  }

  # The box needs its read-token policy before the on-box script fetches the parameter, and the queue
  # must exist before the app is told to publish to it.
  depends_on = [
    aws_iam_role_policy.app_token_read,
    aws_iam_role_policy.app_send,
    aws_ssm_parameter.callback_token,
    aws_sqs_queue.work,
  ]
}
