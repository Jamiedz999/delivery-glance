# The proof-processor Lambda, packaged as an arm64 container image.
#
# Pillow's native wheels cannot ride in a plain zip, so the function is an image: its own ECR repo,
# an image built FROM the official Lambda base (which makes pip resolve the arm64 wheels) and pushed
# here, and the function pointed at it. The image is tagged by a hash of its inputs, so any change to
# handler.py, requirements.txt or the Dockerfile produces a new tag and a new function version, and a
# re-apply that changes nothing re-pushes nothing.

resource "aws_ecr_repository" "proof_processor" {
  count                = local.enabled
  name                 = "delivery-glance-proof-processor"
  image_tag_mutability = "MUTABLE"

  image_scanning_configuration {
    scan_on_push = true
  }
}

locals {
  lambda_source_dir = "${path.module}/../../../lambda/proof-processor"

  # Content address of the deployable: rebuild and re-tag only when one of these changes.
  image_tag = substr(sha256(join("", [
    filesha256("${local.lambda_source_dir}/handler.py"),
    filesha256("${local.lambda_source_dir}/requirements.txt"),
    filesha256("${local.lambda_source_dir}/Dockerfile"),
  ])), 0, 16)

  image_uri = var.enable_proof ? "${aws_ecr_repository.proof_processor[0].repository_url}:${local.image_tag}" : ""
}

# Build and push the image before the function references it. local-exec keeps the pipeline in one
# `terraform apply` (matching the on-box env step) at the cost of needing docker on the apply host —
# the deliberate trade for a feature that is turned on by hand, rarely. Re-runs only when the tag
# changes.
resource "terraform_data" "proof_image" {
  count = local.enabled

  triggers_replace = local.image_tag

  provisioner "local-exec" {
    command = "${path.module}/scripts/build-and-push-image.sh"
    environment = {
      ECR_REPO_URL = aws_ecr_repository.proof_processor[0].repository_url
      IMAGE_TAG    = local.image_tag
      SOURCE_DIR   = local.lambda_source_dir
      AWS_REGION   = var.region
    }
  }

  depends_on = [aws_ecr_repository.proof_processor]
}

resource "aws_lambda_function" "proof_processor" {
  count = local.enabled

  function_name = "proof-processor"
  role          = aws_iam_role.lambda_exec[0].arn
  package_type  = "Image"
  image_uri     = local.image_uri
  architectures = ["arm64"]
  timeout       = 30
  memory_size   = 512

  environment {
    variables = {
      APP_CALLBACK_URL   = var.app_base_url
      APP_CALLBACK_TOKEN = random_password.callback_token[0].result
    }
  }

  depends_on = [
    terraform_data.proof_image,
    aws_iam_role_policy.lambda_s3,
    aws_iam_role_policy_attachment.lambda_logs,
  ]
}

# Let S3 in this account invoke the function; the bucket notification (bucket.tf) depends on this.
resource "aws_lambda_permission" "allow_s3" {
  count = local.enabled

  statement_id   = "s3-invoke"
  action         = "lambda:InvokeFunction"
  function_name  = aws_lambda_function.proof_processor[0].function_name
  principal      = "s3.amazonaws.com"
  source_arn     = aws_s3_bucket.proof[0].arn
  source_account = var.account_id
}
