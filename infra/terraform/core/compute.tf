# The one box, and the stable public address in front of it.
#
# Exactly one instance runs, ever: Current Location and the Recipient SSE subscribers live in its
# process memory (docs/deployment.md §1). The Elastic IP is what the DuckDNS hostname points at, so
# it must not change — the certificate and the hostname both hang off it.

resource "aws_instance" "core" {
  ami                  = var.instance_ami
  instance_type        = "t4g.small"
  subnet_id            = var.subnet_id
  key_name             = "bookinn"
  iam_instance_profile = aws_iam_instance_profile.ec2.name

  vpc_security_group_ids = [aws_security_group.core.id]

  # t4g burst credits bill on top of the instance rather than throttling — matches the live box.
  credit_specification {
    cpu_credits = "unlimited"
  }

  # IMDSv2 required.
  metadata_options {
    http_endpoint               = "enabled"
    http_tokens                 = "required"
    http_put_response_hop_limit = 2
  }

  root_block_device {
    volume_size = 20
    volume_type = "gp3"
    iops        = 3000
    throughput  = 125
  }

  tags = {
    Name = "delivery-glance"
  }

  lifecycle {
    # The box was provisioned by hand and carries an on-disk Postgres volume, a Caddy certificate
    # and generated secrets. A new AMI or a user_data edit would replace the instance and destroy
    # all of that; import codifies what exists rather than inviting a rebuild. Roll the box
    # deliberately, not as a side effect of a plan.
    ignore_changes = [ami, user_data]
  }
}

resource "aws_eip" "core" {
  domain   = "vpc"
  instance = aws_instance.core.id
}
