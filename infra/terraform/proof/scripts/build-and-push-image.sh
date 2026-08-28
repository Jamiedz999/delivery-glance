#!/bin/sh
# Build the proof-processor Lambda image for arm64 and push it to ECR. Run by Terraform
# (lambda.tf) with ECR_REPO_URL, IMAGE_TAG, SOURCE_DIR and AWS_REGION in the environment.
#
# --platform linux/arm64 is what makes pip resolve Pillow's arm64 wheels; the function is created
# with architectures = ["arm64"] to match. Pushing an existing tag is a no-op, so a re-apply that
# did not change the tag re-pushes nothing.
set -eu

: "${ECR_REPO_URL:?ECR_REPO_URL required}"
: "${IMAGE_TAG:?IMAGE_TAG required}"
: "${SOURCE_DIR:?SOURCE_DIR required}"
: "${AWS_REGION:?AWS_REGION required}"

REGISTRY=$(printf '%s' "$ECR_REPO_URL" | cut -d/ -f1)

echo "[proof-image] logging in to $REGISTRY"
aws ecr get-login-password --region "$AWS_REGION" \
  | docker login --username AWS --password-stdin "$REGISTRY"

echo "[proof-image] building ${ECR_REPO_URL}:${IMAGE_TAG} for linux/arm64"
docker build --platform linux/arm64 -t "${ECR_REPO_URL}:${IMAGE_TAG}" "$SOURCE_DIR"

echo "[proof-image] pushing ${ECR_REPO_URL}:${IMAGE_TAG}"
docker push "${ECR_REPO_URL}:${IMAGE_TAG}"

echo "[proof-image] done"
