# Off-band notification consumer Lambda

The asynchronous half of Issue 51. A delivery state change writes a `notification_outbox` row in the
same transaction; the application's relay publishes the bare transition id to an SQS queue; that
message triggers this function, which:

1. calls the application at `POST /api/internal/notifications/begin` with the transition id,
   authorized by a shared bearer token;
2. sends only when told to **PROCEED** — rendering the state-derived message and sending it through
   SES (email) or SNS (SMS) — and does nothing on **ALREADY_SENT**, **SUPPRESSED** or **UNKNOWN**, so
   a redelivery, an unsubscribe and an unknown id are all no-ops;
3. confirms a successful send at `POST /api/internal/notifications/sent`.

All the exactly-once and suppression logic lives in the application, keyed on the transition id; this
function holds no state. A send that fails raises, so SQS retries and finally moves the message to the
dead-letter queue without ever affecting the delivery command. No courier coordinate is ever in the
queue — the body is a transition id and nothing more.

## Layout

| File | What it is |
| --- | --- |
| `handler.py` | `lambda_handler` plus the pure `render` it is built on |
| `test_handler.py` | the message and begin/send/sent-flow assertions, with no AWS |
| `requirements.txt` | none at runtime — boto3 is provided by the runtime |
| `requirements-dev.txt` | adds boto3 and pytest for running the tests locally |

## Environment

| Variable | Meaning |
| --- | --- |
| `APP_CALLBACK_URL` | Base URL of the application the begin/sent callbacks post to |
| `APP_CALLBACK_TOKEN` | Shared bearer token the application compares to authorize the callbacks |
| `NOTIFY_EMAIL_SOURCE` | The verified SES sender address for email notifications |

## Test

```bash
python -m venv .venv && . .venv/bin/activate
pip install -r requirements-dev.txt
python -m pytest
```
