# The one security group in front of the box: SSH from the admin only, HTTP/HTTPS from anywhere
# (Caddy needs 80 for the ACME challenge and 443 to serve), all egress open.
#
# Each rule is its own resource — that is how AWS actually stores them, and it keeps the imported
# plan a clean no-op. The vpc is the account's default VPC.

resource "aws_security_group" "core" {
  name        = "delivery-glance-sg"
  description = "delivery-glance demo: ssh from home, http/https public"
  vpc_id      = var.vpc_id
}

resource "aws_vpc_security_group_ingress_rule" "ssh" {
  security_group_id = aws_security_group.core.id
  description       = "home-ssh"
  ip_protocol       = "tcp"
  from_port         = 22
  to_port           = 22
  cidr_ipv4         = var.admin_ssh_cidr
}

resource "aws_vpc_security_group_ingress_rule" "http_v4" {
  security_group_id = aws_security_group.core.id
  ip_protocol       = "tcp"
  from_port         = 80
  to_port           = 80
  cidr_ipv4         = "0.0.0.0/0"
}

resource "aws_vpc_security_group_ingress_rule" "http_v6" {
  security_group_id = aws_security_group.core.id
  ip_protocol       = "tcp"
  from_port         = 80
  to_port           = 80
  cidr_ipv6         = "::/0"
}

resource "aws_vpc_security_group_ingress_rule" "https_v4" {
  security_group_id = aws_security_group.core.id
  ip_protocol       = "tcp"
  from_port         = 443
  to_port           = 443
  cidr_ipv4         = "0.0.0.0/0"
}

resource "aws_vpc_security_group_ingress_rule" "https_v6" {
  security_group_id = aws_security_group.core.id
  ip_protocol       = "tcp"
  from_port         = 443
  to_port           = 443
  cidr_ipv6         = "::/0"
}

resource "aws_vpc_security_group_egress_rule" "all" {
  security_group_id = aws_security_group.core.id
  ip_protocol       = "-1"
  cidr_ipv4         = "0.0.0.0/0"
}
