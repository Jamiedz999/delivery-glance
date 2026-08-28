# Terraform for the Core box

This describes the live AWS deployment as code. It was **imported, not recreated**: every resource
here already existed — built by hand from the runbook in #49 — and Terraform was pointed at it. The
running demo, the DuckDNS hostname, the Elastic IP, the Caddy certificate and the on-box Postgres
volume were never touched, and a `terraform plan` from a clean checkout reports **no changes**.

That stance is the whole point. This is a record of what runs, and a safe way to change it on
purpose — not an invitation to rebuild it. `aws_instance.core` even ignores changes to its AMI and
user-data so a stray plan cannot replace the box and take the certificate and the database volume
down with it.

## Layout

```
bootstrap/   the S3 bucket + DynamoDB lock table that hold Terraform's own state
core/        the Core deployment: ECR, the GitHub OIDC role, the EC2 role, the security group,
             the instance and its Elastic IP
```

## The backend

`core/` keeps its state in S3, locked by DynamoDB:

- bucket `delivery-glance-tfstate-814654818352` (versioned, encrypted, private)
- lock table `delivery-glance-tflock`

Those two are created by `bootstrap/`, which keeps its **own** state as a local file so the backend
is not chicken-and-egg — `core/` cannot store its state in a bucket that does not exist yet. You run
`bootstrap/` once; it rarely changes again. Its local state is not committed: the bucket and table
already exist, and if the state is ever lost they can be re-imported the same way `core/` was.

## Working on it

```bash
cd core
terraform init          # wires up the S3 backend
terraform plan          # from a clean checkout this reports: No changes.
```

`plan` reporting no changes is the health check: it means the code still matches the live box. If it
ever shows a diff you did not intend, that is drift — reconcile it before applying anything.

To change the deployment, edit `core/`, run `plan`, read it, then `terraform apply`. Concrete,
non-secret facts (account id, region, the Elastic IP's admin SSH CIDR, the AMI) live in
`core/terraform.tfvars`. No credential is stored in this directory.

### The OIDC subject pitfall — do not re-break it

The push role's trust policy (`core/iam_gha.tf`) lists **two** subject forms:

```
repo:Jamiedz999/delivery-glance:*
repo:Jamiedz999@147264453/delivery-glance@1329246885:*
```

This repo has **immutable OIDC subjects** enabled, so the GitHub Actions token's `sub` is the second
(owner@id/repo@id) form. If you narrow the policy to the plain `owner/repo` form alone,
`sts:AssumeRoleWithWebIdentity` starts failing and the image push breaks. Keep both. Confirm the
current subject with:

```bash
gh api repos/Jamiedz999/delivery-glance/actions/oidc/customization/sub
```

## What is not here

This codifies **Core only**. The proof-of-delivery and off-band-notification stacks are separate,
gated modules added on top of this foundation — #70 and #71 — with their own state boundaries.
