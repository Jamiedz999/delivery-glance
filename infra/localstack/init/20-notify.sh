#!/bin/bash
# LocalStack init hook (runs from /etc/localstack/init/ready.d once SQS, Lambda, SES and SNS are up).
# It builds the reproducible reference for what a real deployment provisions by hand or in Terraform:
# a work queue with a dead-letter queue behind it, a verified SES sender, the consumer Lambda, and
# the SQS -> Lambda trigger.
#
# This is demo wiring for compose.notify.yaml, not part of any automated test. The automated proof
# lives in the server's integration tests and the Lambda's pytest.
set -euo pipefail

QUEUE="${NOTIFY_QUEUE_NAME:-delivery-glance-notify}"
DLQ="${QUEUE}-dlq"
CALLBACK_URL="${APP_CALLBACK_URL:-http://app:8080}"
CALLBACK_TOKEN="${APP_CALLBACK_TOKEN:-local-demo-notify-callback-token}"
EMAIL_SOURCE="${NOTIFY_EMAIL_SOURCE:-notify@delivery-glance.example}"
FUNCTION="notification-sender"
SOURCE="/opt/notification-sender"
BUILD="/tmp/notification-build"
# The endpoint the Lambda's own boto3 calls SES/SNS and the callbacks resolve LocalStack through.
LAMBDA_AWS_ENDPOINT="${LAMBDA_AWS_ENDPOINT:-http://localhost.localstack.cloud:4566}"

echo "[notify-init] creating dead-letter queue ${DLQ}"
awslocal sqs create-queue --queue-name "${DLQ}" >/dev/null
DLQ_ARN="$(awslocal sqs get-queue-attributes \
  --queue-url "$(awslocal sqs get-queue-url --queue-name "${DLQ}" --query QueueUrl --output text)" \
  --attribute-names QueueArn --query 'Attributes.QueueArn' --output text)"

echo "[notify-init] creating work queue ${QUEUE} with a redrive to ${DLQ} after 3 receives"
awslocal sqs create-queue --queue-name "${QUEUE}" \
  --attributes "{\"RedrivePolicy\":\"{\\\"deadLetterTargetArn\\\":\\\"${DLQ_ARN}\\\",\\\"maxReceiveCount\\\":\\\"3\\\"}\"}" \
  >/dev/null
QUEUE_URL="$(awslocal sqs get-queue-url --queue-name "${QUEUE}" --query QueueUrl --output text)"
QUEUE_ARN="$(awslocal sqs get-queue-attributes --queue-url "${QUEUE_URL}" \
  --attribute-names QueueArn --query 'Attributes.QueueArn' --output text)"

echo "[notify-init] verifying SES sender ${EMAIL_SOURCE}"
awslocal ses verify-email-identity --email-address "${EMAIL_SOURCE}" >/dev/null

echo "[notify-init] packaging the Lambda"
rm -rf "${BUILD}"
mkdir -p "${BUILD}"
cp "${SOURCE}/handler.py" "${BUILD}/handler.py"
# requirements.txt is intentionally empty — boto3 is provided by the runtime — but honour it if a
# future dependency is added.
if [ -s "${SOURCE}/requirements.txt" ] && grep -qvE '^\s*#|^\s*$' "${SOURCE}/requirements.txt"; then
  pip install --quiet --target "${BUILD}" -r "${SOURCE}/requirements.txt"
fi
(cd "${BUILD}" && zip -qr /tmp/notification-sender.zip .)

echo "[notify-init] deploying ${FUNCTION}"
awslocal lambda create-function \
  --function-name "${FUNCTION}" \
  --runtime python3.12 \
  --handler handler.lambda_handler \
  --timeout 30 \
  --memory-size 256 \
  --role arn:aws:iam::000000000000:role/lambda-role \
  --zip-file fileb:///tmp/notification-sender.zip \
  --environment "Variables={APP_CALLBACK_URL=${CALLBACK_URL},APP_CALLBACK_TOKEN=${CALLBACK_TOKEN},NOTIFY_EMAIL_SOURCE=${EMAIL_SOURCE},AWS_ENDPOINT_URL=${LAMBDA_AWS_ENDPOINT}}" \
  >/dev/null
awslocal lambda wait function-active-v2 --function-name "${FUNCTION}"

echo "[notify-init] wiring ${QUEUE} -> ${FUNCTION}"
awslocal lambda create-event-source-mapping \
  --function-name "${FUNCTION}" \
  --event-source-arn "${QUEUE_ARN}" \
  --batch-size 1 >/dev/null

echo "[notify-init] off-band notification is wired: queue ${QUEUE} -> ${FUNCTION}, DLQ ${DLQ}"
