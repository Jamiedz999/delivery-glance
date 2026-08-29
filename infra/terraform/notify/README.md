# Off-band notification on real AWS (Issue 51)

The delivery-notification epic's queue, dead-letter queue, consumer Lambda, SES sender and IAM,
provisioned on real AWS and **gated behind one variable, `enable_notify` (default `false`)**. This is
the real-AWS twin of [`infra/localstack/init/20-notify.sh`](../../localstack/init/20-notify.sh) — the
same wiring the LocalStack demo builds, against a real account instead of LocalStack. The application
code, the transactional outbox, the relay and the consumer Lambda already run everywhere; this module
only provisions the deployment inputs they need to actually send.

A separate state boundary, like `proof/`: turning notification on or off can never show up as drift
against the Core box or the proof stack, and `core/`'s `plan` stays a strict no-op health check.

## Off by default

With `enable_notify = false` (the checked-in default):

- `terraform plan` shows **no resources** and no change to the Core box's `.env`.
- The application's `delivery-glance.notification.*` settings stay blank, so transitions still write
  `notification_outbox` rows for any opted-in Recipient but nothing relays or sends, and the tracking
  page's opt-in reports the feature unavailable.
- The deployment holds **no Recipient contact at all**, exactly as `deployment.md` describes.

## What it provisions when on

`terraform apply -var enable_notify=true -var notify_email_source=you@example.com`:

| Resource | Detail |
| --- | --- |
| SQS work queue | `delivery-glance-notify`, redrive to the DLQ after **3** receives |
| SQS dead-letter queue | `delivery-glance-notify-dlq` |
| Lambda | `notification-sender` — python3.12, 256 MB, 30 s, a plain zip (boto3 from the runtime) |
| Event-source mapping | work queue → Lambda, **batch size 1** |
| Execution role | `sqs:ReceiveMessage`/`DeleteMessage`/`GetQueueAttributes` on the queue, `ses:SendEmail`, `sns:Publish`, CloudWatch Logs |
| SES identity | the verified sender address (`notify_email_source`) |
| Instance role grants | `sqs:SendMessage` on the queue, and read of the callback-token SSM parameter |
| Box env | `NOTIFY_QUEUE_URL`, `NOTIFY_REGION`, `NOTIFY_CALLBACK_TOKEN` upserted into the app's `.env`, then the app container recreated |

Then a re-apply — or the same apply — turns the feature on: the box points at the queue, the relay
publishes, and the Lambda sends.

No Courier coordinate ever enters the queue. The message body is a Status Change (transition) id and
nothing more (ADR 13); this module does not change that.

## The SES sandbox → production-access manual step

Terraform creates the SES identity, but **two steps are AWS-account actions Terraform cannot
perform**, and both are manual:

1. **Verify the sender.** Creating the identity makes SES email a confirmation link to
   `notify_email_source`. The address cannot send until a human clicks it. Until then, every send
   raises and the message ends up in the DLQ.
2. **Leave the sandbox.** A new SES account is in **sandbox**: it may send only *to* addresses that
   are themselves verified, at a low rate. That is enough to demonstrate the loop end to end (verify
   your own recipient address and opt in with it). Sending to an arbitrary Recipient needs
   **production access**, which is a manual support request in the SES console — account-level, not
   something this module or any Terraform can request.

`notify_email_source` has no checked-in default on purpose: a real send needs a verifiable address,
and no personal address belongs in the repository. Supply it at apply time (above).

## The callback-token contract

Terraform owns the shared bearer token end to end — it is never typed in or committed:

1. `random_password` generates a 48-char alphanumeric token.
2. It is handed to the Lambda as `APP_CALLBACK_TOKEN`, and stored as an SSM SecureString
   `/delivery-glance/notify/callback-token`.
3. At env-set time the box reads that parameter (over an IAM grant scoped to it alone) and writes it
   into `.env` as `NOTIFY_CALLBACK_TOKEN` — the value never passes through the SSM Run Command's
   logged parameters.
4. The Lambda presents it as `Authorization: Bearer <token>` on the `begin` and `sent` callbacks; the
   application's `delivery-glance.notification.callback-token` compares it. A blank token on the app
   side disables both callback routes, so a deployment without this module cannot have a send recorded
   by anything but the Lambda it never configured.

## Inputs

Concrete, non-secret facts live in `terraform.tfvars` (account id, region, the Core instance and its
role, the demo hostname) — the same kind `core/terraform.tfvars` records. The token is generated, not
supplied; the SES sender is supplied at apply time, not checked in. No credential is stored here.

## The local no-AWS path is untouched

`infra/localstack/init/20-notify.sh` and `compose.notify.yaml` remain the demo path that needs no AWS
account. This module is the real-AWS deployment; it does not touch them.
