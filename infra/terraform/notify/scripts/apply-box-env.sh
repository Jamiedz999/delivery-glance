#!/bin/sh
# Run by Terraform (env.tf) on the apply host. Sends set-notify-env-on-box.sh to the Core instance
# over SSM Run Command — the same path the deploy workflow uses — and polls it to a terminal state.
# Requires INSTANCE_ID, AWS_REGION, NOTIFY_QUEUE_URL, NOTIFY_REGION and TOKEN_PARAM_NAME in the
# environment.
set -eu

: "${INSTANCE_ID:?INSTANCE_ID required}"
: "${AWS_REGION:?AWS_REGION required}"
: "${NOTIFY_QUEUE_URL:?NOTIFY_QUEUE_URL required}"
: "${NOTIFY_REGION:?NOTIFY_REGION required}"
: "${TOKEN_PARAM_NAME:?TOKEN_PARAM_NAME required}"

DIR=$(dirname "$0")
PARAMS=$(mktemp)
trap 'rm -f "$PARAMS"' EXIT

# commands[0..2] export the non-secret inputs; commands[3] is the version-controlled on-box script,
# JSON-encoded verbatim so quoting and newlines survive. The token is not among them.
jq -n \
  --arg queue "$NOTIFY_QUEUE_URL" \
  --arg region "$NOTIFY_REGION" \
  --arg param "$TOKEN_PARAM_NAME" \
  --rawfile script "$DIR/set-notify-env-on-box.sh" \
  '{commands: [
     ("export NOTIFY_QUEUE_URL=" + $queue),
     ("export NOTIFY_REGION=" + $region),
     ("export TOKEN_PARAM_NAME=" + $param),
     $script
   ]}' > "$PARAMS"

echo "[notify-env] sending to $INSTANCE_ID"
CMD_ID=$(aws ssm send-command \
  --region "$AWS_REGION" \
  --instance-ids "$INSTANCE_ID" \
  --document-name AWS-RunShellScript \
  --comment "set notify env" \
  --parameters "file://$PARAMS" \
  --query 'Command.CommandId' --output text)
echo "[notify-env] SSM command: $CMD_ID"

STATUS=Pending
i=0
while [ "$i" -lt 40 ]; do
  STATUS=$(aws ssm get-command-invocation --region "$AWS_REGION" \
    --command-id "$CMD_ID" --instance-id "$INSTANCE_ID" \
    --query Status --output text 2>/dev/null || echo Pending)
  case "$STATUS" in
    Success|Failed|Cancelled|TimedOut) break ;;
  esac
  i=$((i + 1))
  sleep 3
done

echo "----- notify-env output -----"
aws ssm get-command-invocation --region "$AWS_REGION" \
  --command-id "$CMD_ID" --instance-id "$INSTANCE_ID" \
  --query StandardOutputContent --output text
aws ssm get-command-invocation --region "$AWS_REGION" \
  --command-id "$CMD_ID" --instance-id "$INSTANCE_ID" \
  --query StandardErrorContent --output text
echo "----- status: $STATUS -----"
test "$STATUS" = Success
