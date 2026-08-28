# Proof-of-delivery processing Lambda

The asynchronous half of Issue 50. A browser uploads a captured photo or signature straight to the
private S3 bucket under `raw/`; that `ObjectCreated` event triggers this function, which:

1. reads the raw object and checks it is an allowed image (`JPEG`, `PNG`, `WEBP`) within the size
   limit — anything else is moved to `quarantine/` and reported **rejected**;
2. strips EXIF/GPS and every other metadata block by re-encoding from decoded pixels into a fresh
   image (see `scrub_image`);
3. writes a scrubbed full copy under `clean/` and a thumbnail under `thumb/`;
4. calls the application back at `POST /api/internal/proof-processed` with the resulting keys, the
   content hash, and the outcome, authorized by a shared bearer token.

No image byte ever passes through the application, on the way in or the way out. The application
stores only the references this callback delivers.

## Layout

| File | What it is |
| --- | --- |
| `handler.py` | `lambda_handler` plus the pure `scrub_image` it is built on |
| `test_handler.py` | the EXIF/GPS-strip and thumbnail assertions, on bytes alone — no AWS |
| `requirements.txt` | the one runtime dependency, Pillow (boto3 is provided by the runtime) |
| `requirements-dev.txt` | adds boto3, piexif and pytest for running the tests locally |
| `Dockerfile` | the arm64 container image the Terraform module builds and deploys |

## Test

```bash
python -m venv .venv && . .venv/bin/activate
pip install -r requirements-dev.txt
pytest
```

The floor assertion the epic names — "the Lambda strips EXIF, asserted against a fixture that
carries GPS" — is `test_scrub_strips_gps_and_every_other_exif_tag`.

## Environment

| Variable | Meaning |
| --- | --- |
| `APP_CALLBACK_URL` | application base URL; the callback posts to `{URL}/api/internal/proof-processed` |
| `APP_CALLBACK_TOKEN` | shared bearer token; must equal the application's `delivery-glance.proof.callback-token` |
| `MAX_UPLOAD_BYTES` | largest object accepted (default 10485760); a larger object is rejected |
| `THUMBNAIL_MAX_PX` | longest thumbnail edge in pixels (default 320) |

## Packaging and trigger

Pillow needs a native wheel, so package the function as a container image or with a Pillow layer
built for the Lambda architecture; a plain zip of this directory will not carry Pillow's binaries.
The `Dockerfile` here builds that image from the official Lambda base, and
`infra/terraform/proof/` builds it `--platform linux/arm64` and deploys it — the real-AWS twin of
the LocalStack wiring below.

Wire the trigger as an S3 `ObjectCreated` notification on the bucket **filtered to the `raw/`
prefix**, so the function never re-triggers on the `clean/` and `thumb/` objects it writes. The
execution role needs `s3:GetObject` on `raw/*`, `s3:PutObject` on `clean/*` and `thumb/*`,
`s3:PutObject`/`s3:DeleteObject` on `quarantine/*` and `raw/*`, and network egress to reach the
callback URL.

For the local Compose demo the same wiring is done against LocalStack by
`infra/localstack/init/10-proof.sh`, the reproducible reference for what a real deployment sets up
by hand or in Terraform. `infra/terraform/proof/` is that Terraform, gated behind `enable_proof`.

The callback token both sides share (`APP_CALLBACK_TOKEN` here, `delivery-glance.proof.callback-token`
in the application) is generated and held by that module, so it is never typed in or committed.
