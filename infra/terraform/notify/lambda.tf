# The notification-sender Lambda, packaged as a plain zip.
#
# Unlike the proof-processor (a container image, because Pillow needs native wheels), this function
# imports only boto3 and the standard library, and boto3 is provided by the python3.12 runtime. So it
# is a plain zip of handler.py and nothing else — the same assumption 20-notify.sh makes when it
# packages the function against LocalStack. requirements.txt is intentionally empty; if a runtime
# dependency is ever added, this zip must grow a pip-install step the way the init script's does.
locals {
  lambda_source_dir = "${path.module}/../../../lambda/notification-sender"
}

data "archive_file" "notification_sender" {
  count       = local.enabled
  type        = "zip"
  source_file = "${local.lambda_source_dir}/handler.py"
  output_path = "${path.module}/.build/notification-sender.zip"
}

resource "aws_lambda_function" "notification_sender" {
  count = local.enabled

  function_name    = "notification-sender"
  role             = aws_iam_role.lambda_exec[0].arn
  runtime          = "python3.12"
  handler          = "handler.lambda_handler"
  filename         = data.archive_file.notification_sender[0].output_path
  source_code_hash = data.archive_file.notification_sender[0].output_base64sha256
  timeout          = 30
  memory_size      = 256

  environment {
    variables = {
      # The consumer's own env: where to call back, the shared bearer token Terraform owns (iam.tf),
      # and the verified SES sender. NOTIFY_EMAIL_SOURCE lives here, not on the box — the application
      # never sends mail, only the Lambda does (lambda/notification-sender/handler.py).
      APP_CALLBACK_URL    = var.app_base_url
      APP_CALLBACK_TOKEN  = random_password.callback_token[0].result
      NOTIFY_EMAIL_SOURCE = var.notify_email_source
    }
  }

  # A real send needs a verifiable sender; enabling the feature without one would deploy a Lambda that
  # raises on its first email. Caught here at plan time rather than at the first notification. The
  # precondition is evaluated only when the function is in the plan (enabled), so a disabled plan
  # never requires the address.
  lifecycle {
    precondition {
      condition     = var.notify_email_source != ""
      error_message = "notify_email_source must be set to a verifiable SES sender address when enable_notify is true."
    }
  }

  depends_on = [
    aws_iam_role_policy.lambda_queue,
    aws_iam_role_policy.lambda_send,
    aws_iam_role_policy_attachment.lambda_logs,
  ]
}

# The SQS -> Lambda trigger, batch size 1, exactly as 20-notify.sh wires it. The execution role's
# queue policy (iam.tf) must exist first or the mapping is created disabled.
resource "aws_lambda_event_source_mapping" "notify" {
  count            = local.enabled
  event_source_arn = aws_sqs_queue.work[0].arn
  function_name    = aws_lambda_function.notification_sender[0].arn
  batch_size       = 1

  depends_on = [aws_iam_role_policy.lambda_queue]
}

# The verified SES sender identity. Terraform creates the identity; SES then emails a confirmation
# link that a human must click before the address can send — Terraform cannot complete that step, and
# the README says so. SES also starts every account in sandbox (only verified recipients receive
# mail); moving to production access is a manual, account-level AWS request, also documented there.
resource "aws_ses_email_identity" "sender" {
  count = local.enabled
  email = var.notify_email_source
}
