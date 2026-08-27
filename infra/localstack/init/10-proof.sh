#!/bin/bash
# LocalStack init hook (runs from /etc/localstack/init/ready.d when S3 and Lambda are up). It builds
# the reproducible reference for what a real deployment provisions by hand or in Terraform: a private
# bucket with browser CORS, the processing Lambda, and the raw/-prefixed S3 -> Lambda trigger.
#
# This is demo wiring for compose.proof.yaml, not part of any automated test.
set -euo pipefail

BUCKET="${PROOF_BUCKET:-delivery-glance-proof}"
ORIGIN="${PROOF_BROWSER_ORIGIN:-http://localhost:8080}"
CALLBACK_URL="${APP_CALLBACK_URL:-http://app:8080}"
CALLBACK_TOKEN="${APP_CALLBACK_TOKEN:-local-demo-proof-callback-token}"
FUNCTION="proof-processor"
SOURCE="/opt/proof-processor"
BUILD="/tmp/proof-build"

echo "[proof-init] creating private bucket ${BUCKET}"
awslocal s3api create-bucket --bucket "${BUCKET}" >/dev/null

echo "[proof-init] blocking all public access"
awslocal s3api put-public-access-block --bucket "${BUCKET}" --public-access-block-configuration \
  "BlockPublicAcls=true,IgnorePublicAcls=true,BlockPublicPolicy=true,RestrictPublicBuckets=true"

echo "[proof-init] allowing browser uploads from ${ORIGIN}"
awslocal s3api put-bucket-cors --bucket "${BUCKET}" --cors-configuration '{
  "CORSRules": [{
    "AllowedMethods": ["PUT", "GET", "HEAD"],
    "AllowedOrigins": ["'"${ORIGIN}"'"],
    "AllowedHeaders": ["*"],
    "ExposeHeaders": ["ETag"],
    "MaxAgeSeconds": 3000
  }]
}'

echo "[proof-init] expiring proof objects after 30 days"
awslocal s3api put-bucket-lifecycle-configuration --bucket "${BUCKET}" --lifecycle-configuration '{
  "Rules": [{
    "ID": "expire-proof",
    "Status": "Enabled",
    "Filter": {"Prefix": ""},
    "Expiration": {"Days": 30}
  }]
}'

echo "[proof-init] packaging the Lambda with Pillow"
rm -rf "${BUILD}"
mkdir -p "${BUILD}"
cp "${SOURCE}/handler.py" "${BUILD}/handler.py"
pip install --quiet --target "${BUILD}" -r "${SOURCE}/requirements.txt"
(cd "${BUILD}" && zip -qr /tmp/proof-processor.zip .)

echo "[proof-init] deploying ${FUNCTION}"
awslocal lambda create-function \
  --function-name "${FUNCTION}" \
  --runtime python3.12 \
  --handler handler.lambda_handler \
  --timeout 30 \
  --memory-size 512 \
  --role arn:aws:iam::000000000000:role/lambda-role \
  --zip-file fileb:///tmp/proof-processor.zip \
  --environment "Variables={APP_CALLBACK_URL=${CALLBACK_URL},APP_CALLBACK_TOKEN=${CALLBACK_TOKEN}}" >/dev/null
awslocal lambda wait function-active-v2 --function-name "${FUNCTION}"

FUNCTION_ARN="$(awslocal lambda get-function --function-name "${FUNCTION}" --query 'Configuration.FunctionArn' --output text)"

echo "[proof-init] letting S3 invoke the Lambda and wiring the raw/ trigger"
awslocal lambda add-permission \
  --function-name "${FUNCTION}" \
  --statement-id s3-invoke \
  --action lambda:InvokeFunction \
  --principal s3.amazonaws.com \
  --source-arn "arn:aws:s3:::${BUCKET}" >/dev/null
awslocal s3api put-bucket-notification-configuration --bucket "${BUCKET}" --notification-configuration '{
  "LambdaFunctionConfigurations": [{
    "LambdaFunctionArn": "'"${FUNCTION_ARN}"'",
    "Events": ["s3:ObjectCreated:*"],
    "Filter": {"Key": {"FilterRules": [{"Name": "prefix", "Value": "raw/"}]}}
  }]
}'

echo "[proof-init] proof of delivery is wired: bucket ${BUCKET}, trigger raw/ -> ${FUNCTION}"
