# The remote-state backend, bootstrapped on its own.
#
# This root module creates the S3 bucket that holds Terraform state and the DynamoDB table that
# locks it. It keeps its OWN state as a local file (committed alongside it) precisely so the
# backend is not chicken-and-egg: `core/` cannot store its state in a bucket that does not exist
# yet, so the bucket cannot itself live in `core/`.
#
# You run this once. After that it almost never changes, and `core/` points its `backend "s3"` at
# what this created.

terraform {
  required_version = ">= 1.6"
  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.60"
    }
  }
}

provider "aws" {
  region = var.region
}

variable "region" {
  description = "AWS region the backend lives in. The Core deployment is us-east-1, so its state is too."
  type        = string
  default     = "us-east-1"
}

variable "state_bucket_name" {
  description = "Globally-unique name of the state bucket. Account-suffixed so it does not collide."
  type        = string
  default     = "delivery-glance-tfstate-814654818352"
}

variable "lock_table_name" {
  description = "DynamoDB table that holds Terraform state locks."
  type        = string
  default     = "delivery-glance-tflock"
}

# --- State bucket ----------------------------------------------------------------------------

resource "aws_s3_bucket" "state" {
  bucket = var.state_bucket_name

  # State is the source of truth for live infrastructure. Losing it, or leaking it, is expensive,
  # so the bucket is versioned, encrypted and closed to the public below.
  lifecycle {
    prevent_destroy = true
  }
}

resource "aws_s3_bucket_versioning" "state" {
  bucket = aws_s3_bucket.state.id
  versioning_configuration {
    status = "Enabled"
  }
}

resource "aws_s3_bucket_server_side_encryption_configuration" "state" {
  bucket = aws_s3_bucket.state.id
  rule {
    apply_server_side_encryption_by_default {
      sse_algorithm = "AES256"
    }
  }
}

resource "aws_s3_bucket_public_access_block" "state" {
  bucket                  = aws_s3_bucket.state.id
  block_public_acls       = true
  block_public_policy     = true
  ignore_public_acls      = true
  restrict_public_buckets = true
}

# --- Lock table ------------------------------------------------------------------------------

resource "aws_dynamodb_table" "lock" {
  name         = var.lock_table_name
  billing_mode = "PAY_PER_REQUEST"
  hash_key     = "LockID"

  attribute {
    name = "LockID"
    type = "S"
  }

  lifecycle {
    prevent_destroy = true
  }
}
