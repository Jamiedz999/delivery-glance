# The callback secret, and every IAM boundary the feature draws.
#
# Terraform owns the callback token: it generates one, hands it to the Lambda as APP_CALLBACK_TOKEN,
# and stores it as an SSM SecureString the box reads at env-set time (env.tf). So both sides — the
# Lambda that presents it and the application's delivery-glance.notification.callback-token that
# checks it — carry the same value with no secret ever typed in or committed. Alphanumeric only, so
# it is safe in a Bearer header and in the on-box sed that writes it.

resource "random_password" "callback_token" {
  count   = local.enabled
  length  = 48
  special = false
}

resource "aws_ssm_parameter" "callback_token" {
  count       = local.enabled
  name        = "/delivery-glance/notify/callback-token"
  description = "Shared bearer token: the notification Lambda presents it, the app's delivery-glance.notification.callback-token checks it."
  type        = "SecureString"
  value       = random_password.callback_token[0].result
}

# --- The Lambda execution role: the least privilege the README names ---

resource "aws_iam_role" "lambda_exec" {
  count = local.enabled
  name  = "delivery-glance-notification-sender"

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect    = "Allow"
      Principal = { Service = "lambda.amazonaws.com" }
      Action    = "sts:AssumeRole"
    }]
  })
}

# CloudWatch Logs only — the managed basic-execution policy, so a failed send is diagnosable.
resource "aws_iam_role_policy_attachment" "lambda_logs" {
  count      = local.enabled
  role       = aws_iam_role.lambda_exec[0].name
  policy_arn = "arn:aws:iam::aws:policy/service-role/AWSLambdaBasicExecutionRole"
}

# What an SQS event source needs the function role to hold on the work queue: receive, delete and
# read-attributes. Scoped to the one queue, so the function can touch no other queue in the account.
resource "aws_iam_role_policy" "lambda_queue" {
  count = local.enabled
  name  = "notify-queue-consume"
  role  = aws_iam_role.lambda_exec[0].id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Sid    = "ConsumeWorkQueue"
      Effect = "Allow"
      Action = [
        "sqs:ReceiveMessage",
        "sqs:DeleteMessage",
        "sqs:GetQueueAttributes",
      ]
      Resource = aws_sqs_queue.work[0].arn
    }]
  })
}

# The send itself: SES email and SNS SMS. SES SendEmail and SNS Publish (to a bare phone number, with
# no topic) are account-level actions with no queue- or bucket-like ARN to scope against, so the
# resource is "*"; the sender is still pinned by NOTIFY_EMAIL_SOURCE and SES's own verified-identity
# check. Callback egress to reach the application needs no policy: the function runs outside any VPC
# and so has the default internet route.
resource "aws_iam_role_policy" "lambda_send" {
  count = local.enabled
  name  = "notify-send"
  role  = aws_iam_role.lambda_exec[0].id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Sid      = "SendEmailAndSms"
      Effect   = "Allow"
      Action   = ["ses:SendEmail", "sns:Publish"]
      Resource = "*"
    }]
  })
}

# --- The application's IAM, on the Core instance role (attached by name, not managed) ---

# The relay publishes the bare transition id to the work queue. It needs exactly sqs:SendMessage on
# that one queue, and nothing when enable_notify is false.
resource "aws_iam_role_policy" "app_send" {
  count = local.enabled
  name  = "notify-relay-send"
  role  = var.instance_role_name

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Sid      = "PublishToWorkQueue"
      Effect   = "Allow"
      Action   = "sqs:SendMessage"
      Resource = aws_sqs_queue.work[0].arn
    }]
  })
}

# So the box can read the callback token at env-set time without the secret ever passing through an
# SSM Run Command's logged parameters. Scoped to the one parameter, plus decrypt of the SSM-managed
# key it is sealed with (constrained to SSM's use of it).
resource "aws_iam_role_policy" "app_token_read" {
  count = local.enabled
  name  = "notify-callback-token-read"
  role  = var.instance_role_name

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Sid      = "ReadCallbackToken"
        Effect   = "Allow"
        Action   = "ssm:GetParameter"
        Resource = aws_ssm_parameter.callback_token[0].arn
      },
      {
        Sid      = "DecryptViaSsm"
        Effect   = "Allow"
        Action   = "kms:Decrypt"
        Resource = "*"
        Condition = {
          StringEquals = { "kms:ViaService" = "ssm.${var.region}.amazonaws.com" }
        }
      },
    ]
  })
}
