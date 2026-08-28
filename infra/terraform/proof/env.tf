# Setting the app's proof env on the box.
#
# The box's /home/ubuntu/app/.env is hand-managed and read by compose.prod.yaml. Rather than replace
# the instance (which would take its Postgres volume and certificate down — core/ ignores AMI and
# user_data changes for exactly this reason), this upserts the three delivery-glance.proof.* vars over
# the same SSM Run Command path the deploy workflow already uses, then recreates the app container.
#
# It re-runs whenever the bucket, region or token changes — never when enable_proof is false. The
# token itself is not passed in the command; the on-box script reads it from SSM (iam.tf grants the
# box that one parameter), so the secret stays out of the command's logged parameters.
resource "terraform_data" "box_env" {
  count = local.enabled

  triggers_replace = [
    local.bucket_name,
    var.region,
    aws_ssm_parameter.callback_token[0].name,
    random_password.callback_token[0].result,
  ]

  provisioner "local-exec" {
    command = "${path.module}/scripts/apply-box-env.sh"
    environment = {
      INSTANCE_ID      = var.instance_id
      AWS_REGION       = var.region
      PROOF_BUCKET     = local.bucket_name
      PROOF_REGION     = var.region
      TOKEN_PARAM_NAME = aws_ssm_parameter.callback_token[0].name
    }
  }

  # The box needs its read-token policy before the on-box script fetches the parameter, and the
  # bucket must exist before the app is told to point at it.
  depends_on = [
    aws_iam_role_policy.app_token_read,
    aws_iam_role_policy.app_presign,
    aws_ssm_parameter.callback_token,
    aws_s3_bucket.proof,
  ]
}
