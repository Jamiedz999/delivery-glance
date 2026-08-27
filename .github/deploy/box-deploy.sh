#!/bin/sh
# Runs ON THE BOX, delivered by SSM Run Command from the deploy workflow.
# POSIX sh only (SSM AWS-RunShellScript uses /bin/sh, i.e. dash) — no bashisms.
# Requires NEW_TAG in the environment (the image tag to deploy, a git sha).
# Recreates the single app container at NEW_TAG, waits for it to report
# healthy, and rolls back to the previously deployed tag if it does not.
set -u

# ecr-login credential helper lives in the ubuntu user's docker config; SSM
# runs this as root, so point docker at that config (the helper authenticates
# through the instance role, not any per-user credentials).
export DOCKER_CONFIG=/home/ubuntu/.docker
cd /home/ubuntu/app || exit 1
: "${NEW_TAG:?NEW_TAG required}"

PREV_TAG=$(grep '^IMAGE_TAG=' .env | cut -d= -f2)
echo "prev=$PREV_TAG new=$NEW_TAG"
sed -i "s|^IMAGE_TAG=.*|IMAGE_TAG=$NEW_TAG|" .env

if ! docker compose -f compose.prod.yaml pull app; then
  echo "pull failed"
  exit 1
fi
if ! docker compose -f compose.prod.yaml up -d; then
  echo "up failed - rolling back to $PREV_TAG"
  sed -i "s|^IMAGE_TAG=.*|IMAGE_TAG=$PREV_TAG|" .env
  docker compose -f compose.prod.yaml up -d
  exit 1
fi

# Gate on the app container's built-in HEALTHCHECK (up to ~120s).
i=0
while [ $i -lt 40 ]; do
  s=$(docker inspect -f '{{.State.Health.Status}}' app-app-1 2>/dev/null || echo starting)
  echo "health=$s"
  if [ "$s" = healthy ]; then
    echo "DEPLOY OK $NEW_TAG"
    exit 0
  fi
  i=$((i + 1))
  sleep 3
done

echo "UNHEALTHY - rolling back to $PREV_TAG"
sed -i "s|^IMAGE_TAG=.*|IMAGE_TAG=$PREV_TAG|" .env
docker compose -f compose.prod.yaml up -d
exit 1
