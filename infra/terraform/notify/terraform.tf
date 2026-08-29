terraform {
  required_version = ">= 1.6"

  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.60"
    }
    random = {
      source  = "hashicorp/random"
      version = "~> 3.6"
    }
    archive = {
      source  = "hashicorp/archive"
      version = "~> 2.4"
    }
  }

  # A state boundary of its own, in the same bucket + lock table bootstrap/ created. Keeping notify
  # out of core/ (and out of proof/) is deliberate: core's `plan` stays a strict no-op health check,
  # and turning notification on or off can never show up as drift against the Core box or the proof
  # stack. See infra/terraform/README.md.
  backend "s3" {
    bucket         = "delivery-glance-tfstate-814654818352"
    key            = "notify/terraform.tfstate"
    region         = "us-east-1"
    dynamodb_table = "delivery-glance-tflock"
    encrypt        = true
  }
}

provider "aws" {
  region = var.region

  # No default_tags, to match core/ and proof/: the Core box it attaches to carries only hand-set
  # tags, and a provider-wide tag would read as drift.
}
