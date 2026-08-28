# Proof of Delivery on real AWS (gated)

The real-AWS twin of [`infra/localstack/init/10-proof.sh`](../../localstack/init/10-proof.sh) — the
LocalStack script that already documents itself as "the reproducible reference for what a real
deployment provisions by hand or in Terraform". This is that Terraform.

It is a **separate state boundary** from `core/` on purpose: core's `plan` stays a strict no-op
health check, and turning proof on or off never shows up as drift against the Core box. This root
attaches its two application policies to the Core instance role **by name** (`delivery-glance-ec2`),
so it reads core's role without managing it.

## The one gate

Everything here multiplies by `enable_proof` (default **false**):

- **`false`** — this root creates nothing and the box's env is untouched. `terraform plan` shows no
  resources, and the deployment stays byte-for-byte what `deployment.md §9` describes. `/api/system`
  still reports `proofCaptureEnabled: false`.
- **`true`** — provisions everything below, sets the app's `delivery-glance.proof.*` env on the box,
  and a re-apply turns the feature on.

## What it provisions when on

- a **private** S3 bucket (`delivery-glance-proof-<account_id>`) — all public access blocked,
  browser-origin CORS for `PUT`/`GET`/`HEAD`, and a 30-day expiry — exactly as `10-proof.sh` builds
  it against LocalStack
- the `proof-processor` Lambda (python3.12, 512 MB, 30 s, arm64), packaged as a **container image**
  built from the official Lambda base so pip resolves Pillow's arm64 native wheels — a plain zip
  cannot carry them (see [`lambda/proof-processor/README.md`](../../../lambda/proof-processor/README.md))
- the Lambda execution role, scoped to exactly what the handler does: `s3:GetObject` on `raw/*`,
  `s3:PutObject` on `clean/*` and `thumb/*`, `s3:PutObject`/`s3:DeleteObject` on `quarantine/*` and
  `raw/*`, plus CloudWatch Logs; egress to reach the callback needs no policy (the function runs
  outside any VPC)
- an `s3:ObjectCreated` notification **filtered to `raw/`** invoking the Lambda, and the
  `lambda:InvokeFunction` permission for `s3.amazonaws.com`
- the application's presign IAM on the instance role: `s3:PutObject` on `raw/*` and `s3:GetObject`
  on `clean/*` and `thumb/*` — a presigned URL is valid only if the signer holds the permission
- the box env `PROOF_BUCKET`, `PROOF_REGION`, `PROOF_CALLBACK_TOKEN`, upserted into
  `/home/ubuntu/app/.env` over SSM Run Command and the app container recreated

## The callback token

Terraform **generates** it (`random_password`), hands it to the Lambda as `APP_CALLBACK_TOKEN`, and
stores it as an SSM **SecureString**. At env-set time the box reads it from SSM
(`--with-decryption`), so the secret never appears in a Run Command's logged parameters. Both sides
end up with the same value — the Lambda that presents it and the application's
`delivery-glance.proof.callback-token` that checks it — with nothing typed in or committed.

## Turning it on

```bash
cd infra/terraform/proof
terraform init
terraform apply -var enable_proof=true
```

Two things are needed **on the apply host**, because the whole feature turns on in one apply:

- **docker** — the image is built here (`--platform linux/arm64`) and pushed to ECR.
- **AWS credentials** that can create these resources, push to ECR, and run SSM commands on the box.

Off again is `terraform apply` with `enable_proof=false` (the default): the resources are destroyed
and the box env lines are left in place but inert once the bucket is gone — set them blank by hand
if you want `/api/system` to report the feature disabled again.

## What this does not touch

The LocalStack overlay (`compose.proof.yaml` and `10-proof.sh`) is the no-AWS demo path and is
independent of this. Nothing here changes core's state or its no-op plan.
