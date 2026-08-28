#!/bin/sh
# Runs ON THE BOX, delivered by SSM Run Command from apply-box-env.sh. POSIX sh only (SSM
# AWS-RunShellScript uses /bin/sh) — no bashisms. It upserts the three delivery-glance.proof.* vars
# into /home/ubuntu/app/.env and recreates the app container so the running app picks them up.
#
# PROOF_BUCKET, PROOF_REGION and TOKEN_PARAM_NAME arrive as exported vars (commands[0..2]); the
# token value is never passed as a parameter — it is read here from SSM, so it stays out of the
# command's logged input.
set -eu

: "${PROOF_BUCKET:?PROOF_BUCKET required}"
: "${PROOF_REGION:?PROOF_REGION required}"
: "${TOKEN_PARAM_NAME:?TOKEN_PARAM_NAME required}"

cd /home/ubuntu/app || exit 1

TOKEN=$(aws ssm get-parameter --name "$TOKEN_PARAM_NAME" --with-decryption \
  --region "$PROOF_REGION" --query 'Parameter.Value' --output text)

# A file that does not end in a newline would otherwise glue an appended var onto the last line.
[ -s .env ] && [ -n "$(tail -c1 .env)" ] && printf '\n' >> .env

# Add the line if absent, replace it in place if present. The values are all safe for the | sed
# delimiter: a bucket name, a region, and an alphanumeric token (random_password special = false).
upsert() {
  key="$1"
  val="$2"
  if grep -q "^${key}=" .env; then
    sed -i "s|^${key}=.*|${key}=${val}|" .env
  else
    printf '%s=%s\n' "$key" "$val" >> .env
  fi
}

upsert PROOF_BUCKET "$PROOF_BUCKET"
upsert PROOF_REGION "$PROOF_REGION"
upsert PROOF_CALLBACK_TOKEN "$TOKEN"

# ecr-login credential helper lives in the ubuntu user's docker config; SSM runs this as root.
export DOCKER_CONFIG=/home/ubuntu/.docker
docker compose -f compose.prod.yaml up -d

echo "PROOF ENV SET bucket=$PROOF_BUCKET region=$PROOF_REGION"
