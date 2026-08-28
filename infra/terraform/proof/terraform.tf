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
  }

  # A state boundary of its own, in the same bucket + lock table bootstrap/ created. Keeping proof
  # out of core/ is deliberate: core's `plan` stays a strict no-op health check, and turning proof
  # on or off can never show up as drift against the Core box. See infra/terraform/README.md.
  backend "s3" {
    bucket         = "delivery-glance-tfstate-814654818352"
    key            = "proof/terraform.tfstate"
    region         = "us-east-1"
    dynamodb_table = "delivery-glance-tflock"
    encrypt        = true
  }
}

provider "aws" {
  region = var.region

  # No default_tags, to match core/: the Core box it attaches to carries only hand-set tags, and a
  # provider-wide tag would read as drift.
}
