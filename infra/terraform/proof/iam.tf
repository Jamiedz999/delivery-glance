# The callback secret, and every IAM boundary the feature draws.
#
# Terraform owns the callback token: it generates one, hands it to the Lambda as APP_CALLBACK_TOKEN,
# and stores it as an SSM SecureString the box reads at env-set time (env.tf). So both sides — the
# Lambda that presents it and the application's delivery-glance.proof.callback-token that checks it —
# carry the same value with no secret ever typed in or committed. Alphanumeric only, so it is safe
# in a Bearer header and in the on-box sed that writes it.

resource "random_password" "callback_token" {
  count   = local.enabled
  length  = 48
  special = false
}

resource "aws_ssm_parameter" "callback_token" {
  count       = local.enabled
  name        = "/delivery-glance/proof/callback-token"
  description = "Shared bearer token: the proof Lambda presents it, the app's delivery-glance.proof.callback-token checks it."
  type        = "SecureString"
  value       = random_password.callback_token[0].result
}

# --- The Lambda execution role: the least privilege the README names ---

resource "aws_iam_role" "lambda_exec" {
  count = local.enabled
  name  = "delivery-glance-proof-processor"

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect    = "Allow"
      Principal = { Service = "lambda.amazonaws.com" }
      Action    = "sts:AssumeRole"
    }]
  })
}

# CloudWatch Logs only — the managed basic-execution policy. Not an S3 or network grant; it just
# lets the function write its own log group so a failed scrub is diagnosable.
resource "aws_iam_role_policy_attachment" "lambda_logs" {
  count      = local.enabled
  role       = aws_iam_role.lambda_exec[0].name
  policy_arn = "arn:aws:iam::aws:policy/service-role/AWSLambdaBasicExecutionRole"
}

# The S3 access the handler actually makes: read the raw upload, write the scrubbed clean/ and
# thumb/ copies, and on rejection copy to quarantine/ then delete the raw object. Scoped per prefix,
# so the function can never read a clean/ object it wrote or touch anything outside these four
# prefixes. Egress to reach the callback needs no policy: the function runs outside any VPC and so
# has the default internet route.
resource "aws_iam_role_policy" "lambda_s3" {
  count = local.enabled
  name  = "proof-object-access"
  role  = aws_iam_role.lambda_exec[0].id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Sid      = "ReadRaw"
        Effect   = "Allow"
        Action   = "s3:GetObject"
        Resource = "${aws_s3_bucket.proof[0].arn}/${local.raw_prefix}*"
      },
      {
        Sid    = "WriteCleanAndThumb"
        Effect = "Allow"
        Action = "s3:PutObject"
        Resource = [
          "${aws_s3_bucket.proof[0].arn}/${local.clean_prefix}*",
          "${aws_s3_bucket.proof[0].arn}/${local.thumbnail_prefix}*",
        ]
      },
      {
        Sid    = "QuarantineAndCleanupRaw"
        Effect = "Allow"
        Action = ["s3:PutObject", "s3:DeleteObject"]
        Resource = [
          "${aws_s3_bucket.proof[0].arn}/${local.quarantine_prefix}*",
          "${aws_s3_bucket.proof[0].arn}/${local.raw_prefix}*",
        ]
      },
    ]
  })
}

# --- The application's presign IAM, on the Core instance role (attached by name, not managed) ---

# The app never streams a proof byte; it only signs URLs. A presigned request is valid only if the
# signing principal — this instance role — itself holds the permission, so the box needs exactly:
# PutObject on raw/* (the Courier's upload) and GetObject on clean/* and thumb/* (the scrubbed views
# a Dispatcher and Recipient load). Nothing else, and nothing when enable_proof is false.
resource "aws_iam_role_policy" "app_presign" {
  count = local.enabled
  name  = "proof-presign"
  role  = var.instance_role_name

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Sid      = "PresignRawUpload"
        Effect   = "Allow"
        Action   = "s3:PutObject"
        Resource = "${aws_s3_bucket.proof[0].arn}/${local.raw_prefix}*"
      },
      {
        Sid    = "PresignCleanAndThumbView"
        Effect = "Allow"
        Action = "s3:GetObject"
        Resource = [
          "${aws_s3_bucket.proof[0].arn}/${local.clean_prefix}*",
          "${aws_s3_bucket.proof[0].arn}/${local.thumbnail_prefix}*",
        ]
      },
    ]
  })
}

# So the box can read the callback token at env-set time without the secret ever passing through an
# SSM Run Command's logged parameters. Scoped to the one parameter, plus decrypt of the SSM-managed
# key it is sealed with (constrained to SSM's use of it).
resource "aws_iam_role_policy" "app_token_read" {
  count = local.enabled
  name  = "proof-callback-token-read"
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
