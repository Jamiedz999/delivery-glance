#!/bin/sh
# Turns the demo's scheduled self-heal on for the live portfolio box, or changes its cadence.
#
# The box's compose file is hand-managed on the instance, and the instance is imported with
# `ignore_changes = [ami, user_data]` (infra/terraform/core/compute.tf), so this is deliberately
# NOT a Terraform user_data edit: that would replace the box and take the Caddy certificate and the
# Postgres volume with it. It is the same SSM Run Command path the deploy workflow uses — rewrite
# /home/ubuntu/app/compose.prod.yaml in place, then recreate the app container.
#
# Run it once the image carrying the self-heal has been deployed; an older image simply ignores the
# variable. Inputs, defaulted to the one live box:
#
#   INSTANCE_ID          the Core instance (infra/terraform/notify/terraform.tfvars)
#   AWS_REGION           its region (infra/terraform/core/terraform.tfvars)
#   DEMO_RESET_SCHEDULE  a Spring cron expression, off-peak, because a reset landing mid-walkthrough
#                        is a recording ruined. Empty removes the line and stops the self-heal.
set -eu

INSTANCE_ID=${INSTANCE_ID:-i-0746a20b7f36b195c}
AWS_REGION=${AWS_REGION:-us-east-1}
DEMO_RESET_SCHEDULE=${DEMO_RESET_SCHEDULE:-0 0 4 * * *}

# The expression crosses four shells before it reaches the application, and a malformed one surfaces
# as an app container that will not start. Six whitespace-separated fields is not the whole of
# Spring's cron grammar, but it is the mistake worth catching on this side of the wire.
if [ -n "$DEMO_RESET_SCHEDULE" ]; then
  # Word-split deliberately, to count the fields — with globbing off, or a cron's asterisks would
  # expand into whatever filenames are in the working directory and the count would be nonsense.
  set -f
  # shellcheck disable=SC2086 -- see above
  set -- $DEMO_RESET_SCHEDULE
  set +f
  [ "$#" -eq 6 ] || {
    echo "DEMO_RESET_SCHEDULE must be six fields (second minute hour day month weekday), got $#" >&2
    exit 1
  }
fi

# Runs ON THE BOX (POSIX sh — SSM AWS-RunShellScript uses /bin/sh). Single-quoted here so every
# variable in it is the box's, not this shell's; the cron arrives exported, so its spaces and
# asterisks are never re-split or globbed on the way in.
ON_BOX='
set -eu
cd /home/ubuntu/app || exit 1
COMPOSE=compose.prod.yaml
# The demo switch is the master switch: a schedule without it would be a cron that never runs. The
# value is checked, not just the line, because a commented-out or false one is the same nothing.
grep -Eq "^ *DEMO_RESET_ENABLED: \"?true\"?" "$COMPOSE" || {
  echo "$COMPOSE does not have DEMO_RESET_ENABLED on; a schedule alone would do nothing"
  exit 1
}
# Removed and re-added rather than appended, so running this twice leaves one of it.
sed -i "/^ *DEMO_RESET_SCHEDULE:/d" "$COMPOSE"
if [ -n "$DEMO_RESET_SCHEDULE" ]; then
  sed -i "s|^\( *\)DEMO_RESET_ENABLED: \(.*\)$|\1DEMO_RESET_ENABLED: \2\n\1DEMO_RESET_SCHEDULE: \"$DEMO_RESET_SCHEDULE\"|" "$COMPOSE"
fi
# Says what it did by reading the file back, so a sed that matched nothing is a failure here rather
# than a success message and a demo that never heals.
if [ -n "$DEMO_RESET_SCHEDULE" ] && ! grep -q "DEMO_RESET_SCHEDULE:" "$COMPOSE"; then
  echo "the schedule was not written; $COMPOSE is unchanged"
  exit 1
fi
grep -n "DEMO_RESET" "$COMPOSE"
# The ecr-login credential helper lives in the ubuntu user config; SSM runs this as root.
export DOCKER_CONFIG=/home/ubuntu/.docker
docker compose -f "$COMPOSE" up -d
echo "DEMO SCHEDULE SET to [$DEMO_RESET_SCHEDULE]"
'

PARAMS=$(mktemp)
trap 'rm -f "$PARAMS"' EXIT
jq -n --arg schedule "$DEMO_RESET_SCHEDULE" --arg script "$ON_BOX" \
  '{commands: [("export DEMO_RESET_SCHEDULE=\"" + $schedule + "\""), $script]}' > "$PARAMS"

echo "[demo-schedule] sending to $INSTANCE_ID"
CMD_ID=$(aws ssm send-command \
  --region "$AWS_REGION" \
  --instance-ids "$INSTANCE_ID" \
  --document-name AWS-RunShellScript \
  --comment "set demo reset schedule" \
  --parameters "file://$PARAMS" \
  --query 'Command.CommandId' --output text)
echo "[demo-schedule] SSM command: $CMD_ID"

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

echo "----- demo-schedule output -----"
aws ssm get-command-invocation --region "$AWS_REGION" \
  --command-id "$CMD_ID" --instance-id "$INSTANCE_ID" \
  --query StandardOutputContent --output text
aws ssm get-command-invocation --region "$AWS_REGION" \
  --command-id "$CMD_ID" --instance-id "$INSTANCE_ID" \
  --query StandardErrorContent --output text
echo "----- status: $STATUS -----"
test "$STATUS" = Success
