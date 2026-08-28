# The private proof bucket, configured exactly as 10-proof.sh builds it against LocalStack: all
# public access blocked, browser-origin CORS for direct PUT/GET/HEAD, and a 30-day expiry so no
# proof artifact outlives its purpose. Every image byte moves browser <-> bucket <-> Lambda; none
# passes through the application.

resource "aws_s3_bucket" "proof" {
  count  = local.enabled
  bucket = local.bucket_name
}

resource "aws_s3_bucket_public_access_block" "proof" {
  count                   = local.enabled
  bucket                  = aws_s3_bucket.proof[0].id
  block_public_acls       = true
  ignore_public_acls      = true
  block_public_policy     = true
  restrict_public_buckets = true
}

resource "aws_s3_bucket_cors_configuration" "proof" {
  count  = local.enabled
  bucket = aws_s3_bucket.proof[0].id

  cors_rule {
    allowed_methods = ["PUT", "GET", "HEAD"]
    allowed_origins = [var.app_base_url]
    allowed_headers = ["*"]
    expose_headers  = ["ETag"]
    max_age_seconds = 3000
  }
}

resource "aws_s3_bucket_lifecycle_configuration" "proof" {
  count  = local.enabled
  bucket = aws_s3_bucket.proof[0].id

  rule {
    id     = "expire-proof"
    status = "Enabled"

    # The whole bucket: every prefix (raw/, clean/, thumb/, quarantine/) is transient proof.
    filter {}

    expiration {
      days = 30
    }
  }
}

# The raw/-filtered trigger. It fires only on the prefix a Courier uploads to, so the Lambda never
# re-triggers on the clean/ and thumb/ objects it writes. The invoke permission must exist first.
resource "aws_s3_bucket_notification" "proof" {
  count  = local.enabled
  bucket = aws_s3_bucket.proof[0].id

  lambda_function {
    lambda_function_arn = aws_lambda_function.proof_processor[0].arn
    events              = ["s3:ObjectCreated:*"]
    filter_prefix       = local.raw_prefix
  }

  depends_on = [aws_lambda_permission.allow_s3]
}
