# The private image registry GitHub Actions pushes to and the box pulls from.

resource "aws_ecr_repository" "delivery_glance" {
  name                 = "delivery-glance"
  image_tag_mutability = "MUTABLE"

  image_scanning_configuration {
    scan_on_push = false
  }

  encryption_configuration {
    encryption_type = "AES256"
  }
}
