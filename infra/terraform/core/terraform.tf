terraform {
  required_version = ">= 1.6"

  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.60"
    }
  }

  # State lives in the bucket + lock table that bootstrap/ created. See infra/terraform/README.md.
  backend "s3" {
    bucket         = "delivery-glance-tfstate-814654818352"
    key            = "core/terraform.tfstate"
    region         = "us-east-1"
    dynamodb_table = "delivery-glance-tflock"
    encrypt        = true
  }
}

provider "aws" {
  region = var.region

  # No default_tags. The live Core box was built by hand with only the tags recorded here; adding
  # provider-wide tags would show up as drift and break the no-op-plan promise.
}
